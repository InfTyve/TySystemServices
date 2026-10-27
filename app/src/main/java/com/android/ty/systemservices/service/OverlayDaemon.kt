package com.android.ty.systemservices.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log

/**
 * 悬浮窗守护调度器。
 *
 * 三条链路互为兜底：
 * 1. [OverlayDaemonService] 以 `START_STICKY` 常驻，`onTaskRemoved` / `onDestroy` 时重新排闹钟；
 * 2. [AlarmManager] 每 [HEARTBEAT_INTERVAL_MS] 自唤醒一次，进程被杀后由广播重新拉起 service；
 * 3. system_server 侧 `OverlayBoot` 心跳每 5 分钟 `startServiceAsUser` 一次（见 services 补丁）。
 *
 * 开关关闭时 [start] 会直接走 [stop]：取消闹钟并停掉 service，不会留下常驻进程。
 */
object OverlayDaemon {

    private const val TAG = "OverlayDaemon"

    /** 闹钟广播的 action，[OverlayDaemonReceiver] 只处理这一条 */
    const val ACTION_WAKE = "com.android.ty.systemservices.action.DAEMON_WAKE"

    /** 自唤醒间隔，与 system_server 心跳保持一致 */
    const val HEARTBEAT_INTERVAL_MS = 5 * 60 * 1000L

    private const val RETRY_DELAY_MS = 5_000L
    private const val ALARM_REQUEST_CODE = 0x5454

    /**
     * 按当前开关状态拉起或停止守护。
     * 开关开启时：启动 service + 排下一次自唤醒；关闭时：取消闹钟 + 停 service。
     */
    @JvmStatic
    fun start(context: Context) {
        val app = context.applicationContext
        if (!TestModeOverlay.isEnabled(app)) {
            stop(app)
            return
        }
        startService(app)
        schedule(app, HEARTBEAT_INTERVAL_MS)
    }

    /** 停止守护：取消闹钟并停掉 service（不会动悬浮窗，浮层由 [TestModeOverlay] 自己收） */
    @JvmStatic
    fun stop(context: Context) {
        val app = context.applicationContext
        cancelAlarm(app)
        try {
            app.stopService(Intent(app, OverlayDaemonService::class.java))
        } catch (t: Throwable) {
            Log.w(TAG, "stop daemon service failed", t)
        }
    }

    /**
     * 只启动 service，不排闹钟。
     *
     * 供 [OverlayDaemonReceiver]、[ServiceSettings] 以及 system_server 的
     * `OverlayBoot` 心跳使用；被后台启动限制拦下时退化成一次闹钟重试。
     */
    @JvmStatic
    fun startService(context: Context) {
        val app = context.applicationContext
        try {
            app.startService(Intent(app, OverlayDaemonService::class.java))
        } catch (t: Throwable) {
            Log.w(TAG, "startService rejected, fallback to alarm", t)
            schedule(app, RETRY_DELAY_MS)
        }
    }

    /** 排一次自唤醒；Doze 下系统可能顺延，属于预期行为 */
    @JvmStatic
    fun schedule(context: Context, delayMs: Long) {
        val app = context.applicationContext
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = SystemClock.elapsedRealtime() + delayMs
        try {
            am.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAt,
                pendingIntent(app),
            )
        } catch (t: Throwable) {
            Log.w(TAG, "schedule wake-up failed", t)
        }
    }

    private fun cancelAlarm(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        try {
            am.cancel(pendingIntent(context))
        } catch (t: Throwable) {
            Log.w(TAG, "cancel wake-up failed", t)
        }
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            Intent(context, OverlayDaemonReceiver::class.java).setAction(ACTION_WAKE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
