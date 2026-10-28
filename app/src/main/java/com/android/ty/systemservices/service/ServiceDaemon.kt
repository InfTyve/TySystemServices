package com.android.ty.systemservices.service

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log

/**
 * 悬浮窗守护调度器。
 *
 * 三条链路互为兜底：
 * 1. [ServiceDaemonService] 以 `START_STICKY` 常驻，`onTaskRemoved` / `onDestroy` 时重新排闹钟；
 * 2. [AlarmManager] 每 [HEARTBEAT_INTERVAL_MS] 自唤醒一次，进程被杀后由广播重新拉起 service；
 * 3. system_server 侧 `OverlayBoot` 心跳每 5 分钟 `startServiceAsUser` 一次（见 services 补丁）。
 *
 * 开关关闭时 [start] 会直接走 [stop]：取消闹钟并停掉 service，不会留下常驻进程。
 */
object ServiceDaemon {

    private const val TAG = "ServiceDaemon"

    /** 闹钟广播的 action，[ServiceDaemonReceiver] 只处理这一条 */
    const val ACTION_WAKE = "com.android.ty.systemservices.action.DAEMON_WAKE"

    /** 自唤醒间隔，与 system_server 心跳保持一致 */
    const val HEARTBEAT_INTERVAL_MS = 5 * 60 * 1000L

    private const val RETRY_DELAY_MS = 5_000L
    private const val ALARM_REQUEST_CODE = 0x5454

    /** 与 [TestModeOverlay] 共用 dev_settings，便于统一查看/清理 */
    private const val PREFS_NAME = "dev_settings"

    /** 是否检测开机向导三件套，默认开启 */
    private const val KEY_CHECK_SETUP = "setup_wizard_check_enabled"

    /** 开机向导标记：三个都为 0 才认为设备尚未完成初始化 */
    private const val KEY_USER_SETUP_COMPLETE = "user_setup_complete"
    private const val KEY_SETUP_WIZARD_HAS_RUN = "setup_wizard_has_run"

    /** 系统开机向导入口 */
    private const val SETUP_PACKAGE = "com.android.setup"
    private const val SETUP_ACTIVITY = "com.android.setup.MainActivity"

    /** 拉起后给 SetupWizard 的启动宽限期，避免启动间隙被误判为"不在前台" */
    private const val LAUNCH_GRACE_MS = 90_000L

    /** 两次重启的最小间隔，避免开机自检还没走完就再次重启 */
    private const val REBOOT_COOLDOWN_MS = 5 * 60 * 1000L

    /** 最多重启次数，超过后只拉起不重启（防止 setup 组件缺失导致 boot loop） */
    private const val REBOOT_LIMIT = 3

    private const val REBOOT_REASON = "enforce-setup-wizard"

    private const val KEY_LAST_LAUNCH = "setup_last_launch"
    private const val KEY_REBOOT_COUNT = "setup_reboot_count"
    private const val KEY_LAST_REBOOT = "setup_last_reboot"

    /**
     * 按当前开关状态拉起或停止守护。
     * 开关开启时：启动 service + 排下一次自唤醒；关闭时：取消闹钟 + 停 service。
     */
    @JvmStatic
    fun start(context: Context) {
        val app = context.applicationContext

        // 开机向导未完成时优先把 SetupWizard 拉起来（三个标记都为 0 才触发）
        launchSetupWizardIfNeeded(app)

        if (!TestModeOverlay.isEnabled(app)) {
            stop(app)
            return
        }
        startService(app)
        schedule(app, HEARTBEAT_INTERVAL_MS)
    }

    /**
     * 开机向导是否尚未完成。
     *
     * 三个标记都取默认值 0，任意一个非 0 就视为已完成，避免"误判成未初始化"
     * 导致 SetupWizard 反复弹出：
     * - [Settings.Global.DEVICE_PROVISIONED]（device_provisioned）
     * - [Settings.Secure] 的 `user_setup_complete`
     * - [Settings.Secure] 的 `setup_wizard_has_run`
     *
     * 读取失败一律按 0 处理（即视为未完成），与守护"宁可不误判已完成"的取向一致。
     */
    @JvmStatic
    fun isSetupWizardPending(context: Context): Boolean {
        if (!isSetupCheckEnabled(context)) {
            Log.d(TAG, "setup wizard check disabled")
            return false
        }
        val cr = context.contentResolver
        val deviceProvisioned = readSetting {
            Settings.Global.getInt(cr, Settings.Global.DEVICE_PROVISIONED, 0)
        }
        val userSetupComplete = readSetting {
            Settings.Secure.getInt(cr, KEY_USER_SETUP_COMPLETE, 0)
        }
        val setupWizardHasRun = readSetting {
            Settings.Secure.getInt(cr, KEY_SETUP_WIZARD_HAS_RUN, 0)
        }
        Log.d(
            TAG,
            "setup state: device_provisioned=$deviceProvisioned, " +
                "user_setup_complete=$userSetupComplete, " +
                "setup_wizard_has_run=$setupWizardHasRun",
        )
        return deviceProvisioned == 0 && userSetupComplete == 0 && setupWizardHasRun == 0
    }

    /** 开机向导检测开关（DevScreen 里的开关），默认 true */
    @JvmStatic
    fun isSetupCheckEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_CHECK_SETUP, true)

    /** 写入开机向导检测开关 */
    @JvmStatic
    fun setSetupCheckEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_CHECK_SETUP, enabled)
            .apply()
    }

    /**
     * `com.android.setup` 是否在前台。
     *
     * 只看包名，不关心是哪个 Activity，因此 SetupWizard 内部换页、拆成多个 Activity
     * 都不会误判。两条路互相兜底：
     * 1. `getRunningTasks(1)` 取栈顶 Activity 的包名（需要 REAL_GET_TASKS，平台签名可获得）；
     * 2. 退化为 `runningAppProcesses` 中 importance == FOREGROUND 的进程。
     *
     * 两条都失败时返回 false（即"不在前台"），行为退化为不加此判断，不会卡死引导。
     */
    @JvmStatic
    fun isSetupWizardForeground(context: Context): Boolean {
        val am = context.getSystemService(ActivityManager::class.java) ?: return false

        try {
            val top = am.getRunningTasks(1).firstOrNull()?.topActivity
            if (top?.packageName == SETUP_PACKAGE) {
                Log.d(TAG, "setup wizard is foreground (task)")
                return true
            }
        } catch (t: Throwable) {
            Log.w(TAG, "getRunningTasks failed", t)
        }

        try {
            val info = am.runningAppProcesses?.firstOrNull { it.processName == SETUP_PACKAGE }
            if (info != null &&
                info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
            ) {
                Log.d(TAG, "setup wizard is foreground (process)")
                return true
            }
        } catch (t: Throwable) {
            Log.w(TAG, "getRunningAppProcesses failed", t)
        }

        return false
    }

    /**
     * 开机向导未完成时的处置入口。
     *
     * - 三个标记都为 0 且 setup 在前台：什么都不做，并清零重启计数；
     * - 不在前台、但刚拉起过（< [LAUNCH_GRACE_MS]）：等它起来，避免启动间隙误判；
     * - 不在前台、且已过宽限期：判定用户退出了引导 → **重启设备**，
     *   重启后 [ServiceDaemonService] 会再次检测到未完成并拉起 SetupWizard；
     * - 重启受 [REBOOT_LIMIT] 与 [REBOOT_COOLDOWN_MS] 双重限制，防止组件缺失时无限重启。
     *
     * @return 是否采取了动作（拉起或重启）；"等待"与"已在前台"返回 false
     */
    @JvmStatic
    fun launchSetupWizardIfNeeded(context: Context): Boolean {
        if (!isSetupWizardPending(context)) return false

        if (isSetupWizardForeground(context)) {
            resetRebootState(context)
            return false
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastLaunch = prefs.getLong(KEY_LAST_LAUNCH, 0L)

        // 刚拉起过，给 SetupWizard 留出启动时间
        if (lastLaunch > 0 && now - lastLaunch < LAUNCH_GRACE_MS) {
            Log.d(TAG, "setup wizard starting, wait before judging")
            return false
        }

        if (lastLaunch > 0) {
            // 拉起过、宽限期已过，人却不在引导里 → 视为退出引导
            val rebootCount = prefs.getInt(KEY_REBOOT_COUNT, 0)
            val lastReboot = prefs.getLong(KEY_LAST_REBOOT, 0L)
            val coolingDown = now - lastReboot < REBOOT_COOLDOWN_MS
            if (rebootCount >= REBOOT_LIMIT || coolingDown) {
                Log.w(TAG, "reboot suppressed (count=$rebootCount, coolingDown=$coolingDown)")
                return launchAndMark(context, prefs, now)
            }

            prefs.edit()
                .putInt(KEY_REBOOT_COUNT, rebootCount + 1)
                .putLong(KEY_LAST_REBOOT, now)
                .putLong(KEY_LAST_LAUNCH, 0L)
                .apply()
            Log.i(TAG, "setup wizard left foreground, rebooting (count=${rebootCount + 1})")
            return rebootDevice(context)
        }

        return launchAndMark(context, prefs, now)
    }

    /** 拉起 SetupWizard，成功才记录时间；组件不存在时不记录，避免误触发重启 */
    private fun launchAndMark(
        context: Context,
        prefs: SharedPreferences,
        now: Long,
    ): Boolean {
        if (!launchSetup(context)) return false
        prefs.edit().putLong(KEY_LAST_LAUNCH, now).apply()
        return true
    }

    private fun launchSetup(context: Context): Boolean = try {
        val intent = Intent().apply {
            setClassName(SETUP_PACKAGE, SETUP_ACTIVITY)
            // 已在向导栈里时复用，避免叠出多个实例
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(intent)
        Log.i(TAG, "setup wizard launched: $SETUP_PACKAGE/$SETUP_ACTIVITY")
        true
    } catch (t: Throwable) {
        // 设备上没有该组件时会抛 ActivityNotFoundException，忽略即可
        Log.w(TAG, "launch setup wizard failed", t)
        false
    }

    /** 重启设备；需要 REBOOT 权限（signature|privileged，平台签名自动授予） */
    private fun rebootDevice(context: Context): Boolean = try {
        val pm = context.getSystemService(PowerManager::class.java)
        if (pm == null) {
            Log.w(TAG, "PowerManager unavailable, reboot skipped")
            false
        } else {
            pm.reboot(REBOOT_REASON)
            true
        }
    } catch (t: Throwable) {
        Log.w(TAG, "reboot failed", t)
        false
    }

    /** 回到引导内即清零计数，下次真正退出时重新开始计 */
    private fun resetRebootState(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_REBOOT_COUNT, 0) == 0 && prefs.getLong(KEY_LAST_LAUNCH, 0L) == 0L) {
            return
        }
        prefs.edit()
            .putInt(KEY_REBOOT_COUNT, 0)
            .putLong(KEY_LAST_LAUNCH, 0L)
            .putLong(KEY_LAST_REBOOT, 0L)
            .apply()
    }

    /**
     * 强制把三个标记重置为 0，让设备重新进入开机向导。
     *
     * 默认**不自动调用**：这会让系统持续认为设备未完成初始化。
     * 需要测试 SetupWizard 时手动调一次即可。
     */
    @JvmStatic
    fun resetSetupState(context: Context) {
        val cr = context.contentResolver
        try {
            Settings.Global.putInt(cr, Settings.Global.DEVICE_PROVISIONED, 0)
            Settings.Secure.putInt(cr, KEY_USER_SETUP_COMPLETE, 0)
            Settings.Secure.putInt(cr, KEY_SETUP_WIZARD_HAS_RUN, 0)
            Log.i(TAG, "setup state reset to 0")
        } catch (t: Throwable) {
            Log.w(TAG, "reset setup state failed", t)
        }
    }

    private inline fun readSetting(block: () -> Int): Int =
        try {
            block()
        } catch (t: Throwable) {
            Log.w(TAG, "read setting failed", t)
            0
        }

    /** 停止守护：取消闹钟并停掉 service（不会动悬浮窗，浮层由 [TestModeOverlay] 自己收） */
    @JvmStatic
    fun stop(context: Context) {
        val app = context.applicationContext
        cancelAlarm(app)
        try {
            app.stopService(Intent(app, ServiceDaemonService::class.java))
        } catch (t: Throwable) {
            Log.w(TAG, "stop daemon service failed", t)
        }
    }

    /**
     * 只启动 service，不排闹钟。
     *
     * 供 [ServiceDaemonReceiver]、[ServiceSettings] 以及 system_server 的
     * `OverlayBoot` 心跳使用；被后台启动限制拦下时退化成一次闹钟重试。
     */
    @JvmStatic
    fun startService(context: Context) {
        val app = context.applicationContext
        try {
            app.startService(Intent(app, ServiceDaemonService::class.java))
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
            Intent(context, ServiceDaemonReceiver::class.java).setAction(ACTION_WAKE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
