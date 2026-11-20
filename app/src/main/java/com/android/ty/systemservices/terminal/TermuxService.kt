package com.android.ty.systemservices.terminal

import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.android.ty.systemservices.R
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 终端会话宿主。
 *
 * 会话（pty 子进程 + VT 解析状态）必须活得比 Activity 久，否则旋转屏幕、切后台回来
 * 就会把正在跑的命令杀掉，所以放在 Service 里，Activity 只通过 Binder 拿引用。
 */
class TermuxService : Service(), TerminalSessionClient {

    interface Listener {
        fun onSessionChanged(session: TerminalSession)
        fun onSessionFinished(session: TerminalSession)
        fun onSessionTitleChanged(session: TerminalSession)
    }

    inner class LocalBinder : Binder() {
        fun service(): TermuxService = this@TermuxService
    }

    companion object {
        private const val TAG = "TermuxService"
        private const val TRANSCRIPT_ROWS = 2000
    }

    private val binder = LocalBinder()
    private val sessions = ArrayList<TerminalSession>()
    private val listeners = CopyOnWriteArrayList<Listener>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var nextIndex = 1

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        synchronized(sessions) {
            sessions.forEach { runCatching { it.finishIfRunning() } }
            sessions.clear()
        }
        super.onDestroy()
    }

    fun addListener(listener: Listener) {
        listeners.addIfAbsent(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun sessions(): List<TerminalSession> = synchronized(sessions) { ArrayList(sessions) }

    /**
     * 新建一个会话。shell 固定用系统自带的 /system/bin/sh —— 这个应用没有自己的
     * rootfs，不提供 apt，能用的就是系统命令。
     */
    fun createSession(): TerminalSession {
        val home = File(filesDir, "home").apply { mkdirs() }
        val env = arrayOf(
            "HOME=" + home.absolutePath,
            "PREFIX=" + home.absolutePath,
            "TMPDIR=" + cacheDir.absolutePath,
            "PATH=/sbin:/system/sbin:/system/bin:/system/xbin:/vendor/bin",
            "TERM=xterm-256color",
            "LANG=en_US.UTF-8",
            "ANDROID_ROOT=/system",
            "ANDROID_DATA=/data",
            "SHELL=/system/bin/sh",
        )
        val session = TerminalSession(
            "/system/bin/sh",
            home.absolutePath,
            emptyArray(),
            env,
            TRANSCRIPT_ROWS,
            this,
        )
        session.mSessionName = getString(R.string.termux_session_name, nextIndex++)
        synchronized(sessions) { sessions.add(session) }
        return session
    }

    fun removeSession(session: TerminalSession) {
        synchronized(sessions) { sessions.remove(session) }
        runCatching { session.finishIfRunning() }
    }

    fun removeAll() {
        val copy = synchronized(sessions) {
            val c = ArrayList(sessions)
            sessions.clear()
            c
        }
        copy.forEach { runCatching { it.finishIfRunning() } }
    }

    // ------------------------------------------------------------------ //
    // TerminalSessionClient
    // ------------------------------------------------------------------ //

    override fun onTextChanged(changedSession: TerminalSession) {
        listeners.forEach { it.onSessionChanged(changedSession) }
    }

    override fun onTitleChanged(changedSession: TerminalSession) {
        listeners.forEach { it.onSessionTitleChanged(changedSession) }
    }

    override fun onSessionFinished(finishedSession: TerminalSession) {
        listeners.forEach { it.onSessionFinished(finishedSession) }
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        mainHandler.post {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return@post
            cm.setPrimaryClip(ClipData.newPlainText("terminal", text))
        }
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        val target = session ?: return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val text = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString() ?: return
        if (text.isNotEmpty()) target.write(text.toByteArray(), 0, text.toByteArray().size)
    }

    override fun onBell(session: TerminalSession) {
        // 静音：AOD/终端场景下响铃反而干扰
    }

    override fun onColorsChanged(session: TerminalSession) {
        listeners.forEach { it.onSessionChanged(session) }
    }

    override fun onTerminalCursorStateChange(state: Boolean) {
        // 光标闪烁由 TerminalView 自己处理
    }

    override fun getTerminalCursorStyle(): Int? = null

    override fun logError(tag: String, message: String) { Log.e(tag, message) }

    override fun logWarn(tag: String, message: String) { Log.w(tag, message) }

    override fun logInfo(tag: String, message: String) { Log.i(tag, message) }

    override fun logDebug(tag: String, message: String) { Log.d(tag, message) }

    override fun logVerbose(tag: String, message: String) { Log.v(tag, message) }

    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {
        Log.e(tag, message, e)
    }

    override fun logStackTrace(tag: String, e: Exception) { Log.e(tag, "stack trace", e) }
}
