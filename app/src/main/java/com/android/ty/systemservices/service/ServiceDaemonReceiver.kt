package com.android.ty.systemservices.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 闹钟自唤醒的落点：收到 [ServiceDaemon.ACTION_WAKE] 后把守护服务拉起来，
 * 并续订下一次闹钟。
 *
 * 用广播而不是 `PendingIntent.getService`，是为了避开 Android 8+ 的后台
 * 服务启动限制——广播处理期间处于前台窗口，`startService` 一定被允许。
 */
class ServiceDaemonReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ServiceDaemon.ACTION_WAKE) return
        Log.i(TAG, "wake-up received")
        ServiceDaemon.startService(context)
        if (TestModeOverlay.isEnabled(context)) {
            ServiceDaemon.schedule(context, ServiceDaemon.HEARTBEAT_INTERVAL_MS)
        }
    }

    private companion object {
        const val TAG = "ServiceDaemonReceiver"
    }
}
