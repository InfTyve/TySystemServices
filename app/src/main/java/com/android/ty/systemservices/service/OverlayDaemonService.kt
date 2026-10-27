package com.android.ty.systemservices.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * 悬浮窗守护服务 —— **由 system_server 直接启动**。
 *
 * 启动链路：
 * ```
 * system_server (OverlayBoot)
 *   └─ startServiceAsUser(OverlayDaemonService)
 *        └─ onCreate / onStartCommand → TestModeOverlay.restore()
 *             └─ WindowManager.addView(TYPE_APPLICATION_OVERLAY)
 * ```
 *
 * 自身也承担应用内守护：
 * - `START_STICKY`：进程被系统回收后由 AMS 重新拉起；
 * - `onTaskRemoved` / `onDestroy`：补排一次闹钟，避免被一次性杀掉后再也起不来；
 * - 开关关闭时立即 `stopSelf()`，不留常驻进程。
 */
class OverlayDaemonService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "daemon service created")
        sync()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        sync()
        return START_STICKY
    }

    /** 统一入口：开关开着就恢复浮层并续订闹钟，关着就自己退出 */
    private fun sync() {
        if (!TestModeOverlay.isEnabled(this)) {
            Log.i(TAG, "overlay disabled, stopping daemon")
            stopSelf()
            return
        }
        TestModeOverlay.restore(this)
        OverlayDaemon.schedule(this, OverlayDaemon.HEARTBEAT_INTERVAL_MS)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 用户从最近任务划掉应用时，尽快唤醒一次（服务本身可能随后被停）
        if (TestModeOverlay.isEnabled(this)) {
            OverlayDaemon.schedule(this, 1_500L)
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        // 被系统杀掉/主动停止时补一次闹钟；开关已关就不再排，避免无限拉起
        if (TestModeOverlay.isEnabled(this)) {
            OverlayDaemon.schedule(this, 5_000L)
        }
        Log.i(TAG, "daemon service destroyed")
        super.onDestroy()
    }

    private companion object {
        const val TAG = "OverlayDaemonService"
    }
}
