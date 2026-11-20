package com.android.ty.systemservices.terminal

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.android.ty.systemservices.R
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

/**
 * 终端主界面。
 *
 * 结构照搬 AOSP Terminal：顶部 Toolbar + 会话分页 + 终端视图，底部加一行快捷键。
 * 会话本身由 [TermuxService] 持有，这里只负责把当前会话接到 [TerminalView] 上。
 */
class TermuxActivity : AppCompatActivity(), TerminalViewClient, TermuxService.Listener {

    companion object {
        private const val TAG = "TermuxActivity"
        private const val MIN_FONT = 6
        private const val MAX_FONT = 32
    }

    private lateinit var pager: ViewPager2
    private lateinit var toolbar: Toolbar
    private lateinit var adapter: TerminalPagerAdapter

    private val sessions = ArrayList<TerminalSession>()
    private var service: TermuxService? = null
    private var bound = false

    private var fontSizeSp = 14
    private var foreground = Color.WHITE
    private var background = Color.BLACK

    private var ctrlPressed = false
    private var altPressed = false

    private val handler = Handler(Looper.getMainLooper())
    private val bindings = HashMap<TerminalSession, TerminalView>()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val local = binder as? TermuxService.LocalBinder ?: return
            service = local.service()
            bound = true
            service?.addListener(this@TermuxActivity)
            syncSessions(createIfEmpty = true)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_termux)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(true)

        pager = findViewById(R.id.pager)
        adapter = TerminalPagerAdapter()
        pager.adapter = adapter
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateTitle()
            }
        })

        readPreferences()
        applyWindowFlags()
    }

    override fun onStart() {
        super.onStart()
        if (!bound) {
            bindService(Intent(this, TermuxService::class.java), connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        if (bound) {
            service?.removeListener(this)
            unbindService(connection)
            bound = false
        }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // 从设置页回来时字号/配色可能变了，重新套一遍
        val oldSize = fontSizeSp
        readPreferences()
        applyWindowFlags()
        if (oldSize != fontSizeSp) {
            adapter.notifyDataSetChanged()
        } else {
            bindings.forEach { (_, view) -> applyColors(view) }
        }
        updateTitle()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.termux_activity, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_new -> {
                newSession()
                true
            }
            R.id.action_close -> {
                closeCurrentSession()
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, TermuxSettingsActivity::class.java))
                true
            }
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ------------------------------------------------------------------ //
    // 会话
    // ------------------------------------------------------------------ //

    private fun readPreferences() {
        fontSizeSp = TermuxPreferences.fontSize(this)
        val (fg, bg) = TermuxPreferences.colors(this)
        foreground = fg
        background = bg
    }

    private fun applyWindowFlags() {
        if (TermuxPreferences.fullscreen(this)) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.statusBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            WindowCompat.setDecorFitsSystemWindows(window, true)
            WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.statusBars())
        }

        requestedOrientation = when (TermuxPreferences.orientation(this)) {
            "portrait" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            "landscape" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    private fun syncSessions(createIfEmpty: Boolean) {
        val svc = service ?: return
        val current = svc.sessions()
        sessions.clear()
        sessions.addAll(current)
        if (sessions.isEmpty() && createIfEmpty) {
            sessions.add(svc.createSession())
        }
        adapter.notifyDataSetChanged()
        if (sessions.isNotEmpty()) {
            pager.setCurrentItem(sessions.size - 1, false)
        }
        updateTitle()
    }

    private fun newSession() {
        val svc = service ?: return
        val session = svc.createSession()
        sessions.add(session)
        adapter.notifyItemInserted(sessions.size - 1)
        pager.setCurrentItem(sessions.size - 1, true)
        updateTitle()
    }

    private fun closeCurrentSession() {
        val index = pager.currentItem
        val session = sessions.getOrNull(index) ?: return
        service?.removeSession(session)
        sessions.removeAt(index)
        adapter.notifyItemRemoved(index)
        if (sessions.isEmpty()) {
            finish()
        } else {
            pager.setCurrentItem(index.coerceAtMost(sessions.size - 1), false)
            updateTitle()
        }
    }

    private fun updateTitle() {
        val session = sessions.getOrNull(pager.currentItem)
        val title = session?.title?.takeIf { it.isNotBlank() }
            ?: session?.mSessionName
            ?: getString(R.string.termux_app_name)
        supportActionBar?.title = title
    }

    // ------------------------------------------------------------------ //
    // Service 回调
    // ------------------------------------------------------------------ //

    override fun onSessionChanged(session: TerminalSession) {
        handler.post { bindings[session]?.onScreenUpdated() }
    }

    override fun onSessionTitleChanged(session: TerminalSession) {
        handler.post { updateTitle() }
    }

    override fun onSessionFinished(session: TerminalSession) {
        handler.post {
            val index = sessions.indexOf(session)
            if (index >= 0) {
                Log.i(TAG, "session finished: ${session.mSessionName}")
                // 保留结束的会话让用户看到最后的输出，只在全部结束时退出
                updateTitle()
            }
        }
    }

    // ------------------------------------------------------------------ //
    // RecyclerView
    // ------------------------------------------------------------------ //

    private inner class TerminalPagerAdapter : RecyclerView.Adapter<TerminalHolder>() {

        override fun getItemCount(): Int = sessions.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TerminalHolder {
            val context = parent.context
            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setBackgroundColor(Color.BLACK)
            }

            val terminal = TerminalView(context, null).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
                )
                isFocusable = true
                isFocusableInTouchMode = true
            }
            // padding 放在容器上：TerminalView 用自身内边距算行列数，会少算一圈
            root.setPadding(dp(8), dp(8), dp(8), 0)
            root.addView(terminal)

            val extraKeys = ExtraKeysView(context)
            root.addView(
                extraKeys,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )

            return TerminalHolder(root, terminal, extraKeys)
        }

        override fun onBindViewHolder(holder: TerminalHolder, position: Int) {
            holder.bind(sessions[position])
        }

        override fun onViewRecycled(holder: TerminalHolder) {
            holder.unbind()
            super.onViewRecycled(holder)
        }
    }

    private inner class TerminalHolder(
        root: View,
        private val terminal: TerminalView,
        private val extraKeys: ExtraKeysView,
    ) : RecyclerView.ViewHolder(root) {

        private var session: TerminalSession? = null

        @SuppressLint("ClickableViewAccessibility")
        fun bind(target: TerminalSession) {
            session = target
            bindings[target] = terminal

            terminal.setTerminalViewClient(this@TermuxActivity)
            terminal.setTextSize(fontSizeSp)
            terminal.setTypeface(Typeface.MONOSPACE)
            applyColors(terminal)
            terminal.attachSession(target)
            terminal.requestFocus()

            extraKeys.resetModifiers()
            extraKeys.onKey = { text ->
                val bytes = text.toByteArray()
                target.write(bytes, 0, bytes.size)
            }
            extraKeys.onModifierChanged = { ctrl, alt ->
                ctrlPressed = ctrl
                altPressed = alt
            }

            // 点终端区域就把焦点和软键盘拉回来
            terminal.setOnClickListener {
                terminal.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(terminal, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        fun unbind() {
            session?.let { bindings.remove(it) }
            session = null
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun applyColors(view: TerminalView) {
        view.setBackgroundColor(background)
        val emulator = view.mEmulator ?: return
        emulator.mColors.mCurrentColors[com.termux.terminal.TextStyle.COLOR_INDEX_FOREGROUND] = foreground
        emulator.mColors.mCurrentColors[com.termux.terminal.TextStyle.COLOR_INDEX_BACKGROUND] = background
        view.invalidate()
    }

    // ------------------------------------------------------------------ //
    // TerminalViewClient
    // ------------------------------------------------------------------ //

    override fun onScale(scale: Float): Float {
        val next = (fontSizeSp * scale).toInt().coerceIn(MIN_FONT, MAX_FONT)
        if (next != fontSizeSp) {
            fontSizeSp = next
            bindings.values.forEach { it.setTextSize(next) }
        }
        return scale
    }

    override fun onSingleTapUp(e: MotionEvent) {
        val session = sessions.getOrNull(pager.currentItem) ?: return
        val view = bindings[session] ?: return
        view.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun shouldBackButtonBeMappedToEscape(): Boolean = false

    override fun shouldEnforceCharBasedInput(): Boolean = true

    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false

    override fun isTerminalViewSelected(): Boolean {
        return bindings[sessions.getOrNull(pager.currentItem)]?.hasFocus() == true
    }

    override fun copyModeChanged(copyMode: Boolean) {
        // 文本选择模式不需要额外的 UI 状态
    }

    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean {
        if (TermuxPreferences.volumeKeyMode(this)) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> return true
                KeyEvent.KEYCODE_VOLUME_DOWN -> return true
            }
        }
        return false
    }

    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && (ctrlPressed || altPressed)) {
            ctrlPressed = false
            altPressed = false
            return true
        }
        return false
    }

    override fun onLongPress(event: MotionEvent): Boolean = false

    override fun readControlKey(): Boolean = ctrlPressed

    override fun readAltKey(): Boolean = altPressed

    override fun readShiftKey(): Boolean = false

    override fun readFnKey(): Boolean = false

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean {
        // 粘滞 CTRL/ALT 用过一次就释放，符合 Termux 的手感
        if (ctrlDown || altPressed) {
            ctrlPressed = false
            altPressed = false
        }
        return false
    }

    override fun onEmulatorSet() {
        bindings.forEach { (_, view) -> applyColors(view) }
    }

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
