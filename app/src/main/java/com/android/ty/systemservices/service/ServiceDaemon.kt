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

    /**
     * 启动 SetupWizard 前留给宿主的窗口期。
     *
     * 这段时间内即使用户切走，也只等不重启，避免 Activity 冷启动、
     * 页面切换的空档被当成"用户退出了引导"。
     */
    private const val LAUNCH_GRACE_MS = 75_000L

    /**
     * "setup 已启动但离开前台"的倒计时窗口。
     *
     * 从第一次采样到不在前台开始计时 [MONITOR_WINDOW_MS]，期间按
     * [MONITOR_INTERVAL_MS] 反复采样；**整段倒计时内每一次都不在前台**才重启，
     * 任意一次回到前台立即撤销倒计时。
     */
    private const val MONITOR_WINDOW_MS = 20_000L

    /** 倒计时窗口内的采样间隔 */
    private const val MONITOR_INTERVAL_MS = 5_000L

    /** 扫描任务栈时最多看多少个任务（setup 通常在最前面，取少量即可） */
    private const val MAX_TASK_SCAN = 20

    /** 两次重启的最小间隔，避免开机自检还没走完就再次重启 */
    private const val REBOOT_COOLDOWN_MS = 5 * 60 * 1000L

    /** 最多重启次数，超过后只拉起不重启（防止 setup 组件缺失导致 boot loop） */
    private const val REBOOT_LIMIT = 3

    private const val REBOOT_REASON = "enforce-setup-wizard"

    private const val KEY_LAST_LAUNCH = "setup_last_launch"
    private const val KEY_REBOOT_COUNT = "setup_reboot_count"
    private const val KEY_LAST_REBOOT = "setup_last_reboot"
    private const val KEY_MONITOR_START = "setup_monitor_start"

    /** setup 的可见性探测结果，把"探测失败"和"真的不在前台"区分开 */
    private enum class SetupVisibility {
        /** 两条探测通道都不可用，无法判断，此时不做任何重启判定 */
        UNKNOWN,

        /** 进程不在、任务栈里也没有：还没启动 */
        NOT_RUNNING,

        /** 已启动（进程或任务存在），但不在前台 */
        BACKGROUND,

        /** 已启动且在前台 */
        FOREGROUND,
    }

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
        schedule(app, nextWakeDelayMs(app))
    }

    /**
     * 下一次自唤醒该等多久。
     *
     * 处于开机向导倒计时窗口时用 [MONITOR_INTERVAL_MS] 密集采样，
     * 其余时间回到 [HEARTBEAT_INTERVAL_MS] 的常规定时。
     *
     * 检测开关关掉后一律回到常规心跳：即使 prefs 里还残留倒计时记录
     * （例如刚关开关、还没来得及清理），也不会再按 5 秒高频唤醒。
     */
    @JvmStatic
    fun nextWakeDelayMs(context: Context): Long =
        if (isMonitoring(context)) MONITOR_INTERVAL_MS else HEARTBEAT_INTERVAL_MS

    /** 当前是否处在"setup 已启动但不在前台"的倒计时窗口里 */
    private fun isMonitoring(context: Context): Boolean =
        isSetupCheckEnabled(context) && prefs(context).getLong(KEY_MONITOR_START, 0L) > 0L

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
        prefs(context).getBoolean(KEY_CHECK_SETUP, true)

    /**
     * 写入开机向导检测开关。
     *
     * 关掉时立刻收尾：撤销可能还在跑的倒计时、清零重启计数，
     * 并把闹钟从 5 秒采样改回常规心跳。这样"关开关"就是当场生效，
     * 不会残留一个已经排好的密集采样任务继续跑。
     */
    @JvmStatic
    fun setSetupCheckEnabled(context: Context, enabled: Boolean) {
        val app = context.applicationContext
        prefs(app).edit()
            .putBoolean(KEY_CHECK_SETUP, enabled)
            .apply()

        if (enabled) {
            // 打开后尽快跑一次检查，不必等满一个心跳周期；不在这里直接拉起，避免突兀弹屏
            if (TestModeOverlay.isEnabled(app)) {
                schedule(app, 3_000L)
            }
            return
        }

        Log.i(TAG, "setup wizard check disabled, stopping countdown")
        clearMonitor(app)
        resetRebootState(app)
        // 悬浮窗守护还开着才需要重排心跳；已关掉的话 stop() 已经取消闹钟了
        if (TestModeOverlay.isEnabled(app)) {
            schedule(app, HEARTBEAT_INTERVAL_MS)
        }
    }

    /**
     * `com.android.setup` 是否已经启动。
     *
     * "启动"不看前台：进程活着或任务栈里还有它的栈就算已启动，
     * 这样冷启动中、刚被切走都会被识别为已启动，走倒计时而不是重启。
     */
    @JvmStatic
    fun isSetupWizardRunning(context: Context): Boolean = when (queryVisibility(context)) {
        SetupVisibility.FOREGROUND, SetupVisibility.BACKGROUND -> true
        SetupVisibility.NOT_RUNNING, SetupVisibility.UNKNOWN -> false
    }

    /** `com.android.setup` 是否在前台；探测不可用时返回 false */
    @JvmStatic
    fun isSetupWizardForeground(context: Context): Boolean =
        queryVisibility(context) == SetupVisibility.FOREGROUND

    /**
     * 统一的可见性探测：一次拿到"有没有启动"和"在不在前台"，避免重复查询。
     *
     * 只看包名，不关心是哪个 Activity，因此 SetupWizard 内部换页、拆成多个 Activity
     * 都不会误判。两条通道互相兜底：
     * 1. `getRunningTasks` 取任务栈：栈顶是 setup → 前台；任意一个栈里有 → 已启动；
     * 2. `runningAppProcesses` 里 setup 进程 importance ≤ FOREGROUND → 前台，进程存在 → 已启动。
     *
     * 两条都抛异常或都返回空 → [SetupVisibility.UNKNOWN]，此时不做重启判定。
     */
    private fun queryVisibility(context: Context): SetupVisibility {
        val am = context.getSystemService(ActivityManager::class.java)
            ?: return SetupVisibility.UNKNOWN

        var probed = false
        var running = false
        var foreground = false

        try {
            val tasks = am.getRunningTasks(MAX_TASK_SCAN)
            if (tasks != null) {
                probed = true
                for (index in 0 until tasks.size) {
                    val task = tasks[index]
                    val top = task.topActivity
                    val base = task.baseActivity
                    if (top?.packageName == SETUP_PACKAGE || base?.packageName == SETUP_PACKAGE) {
                        running = true
                        if (index == 0 && top?.packageName == SETUP_PACKAGE) {
                            foreground = true
                        }
                        break
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "getRunningTasks failed", t)
        }

        try {
            val processes = am.runningAppProcesses
            if (processes != null) {
                probed = true
                val info = processes.firstOrNull { it.processName == SETUP_PACKAGE }
                if (info != null) {
                    running = true
                    if (info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                        foreground = true
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "getRunningAppProcesses failed", t)
        }

        if (!probed) return SetupVisibility.UNKNOWN
        return when {
            foreground -> SetupVisibility.FOREGROUND
            running -> SetupVisibility.BACKGROUND
            else -> SetupVisibility.NOT_RUNNING
        }
    }

    /**
     * 开机向导未完成时的处置入口。
     *
     * 判定顺序（严格自上而下，前一档不满足才进入下一档）：
     * 1. 三标记不全为 0 → 已完成，清掉倒计时后退出；
     * 2. setup **已启动且在前台** → 一切正常，撤销倒计时并清零重启计数；
     * 3. setup **还没启动** → 拉起它；刚拉起过就等宽限期，**不重启**；
     * 4. setup 已启动但不在前台：
     *    - 距上次拉起还在 [LAUNCH_GRACE_MS] 内 → 当作启动中，等；
     *    - 否则启动 [MONITOR_WINDOW_MS] 倒计时，期间按 [MONITOR_INTERVAL_MS] 采样；
     *    - **倒计时内每次都不在前台** → 判定用户退出引导，重启设备；
     *      倒计时内任意一次回到前台 → 撤销倒计时并清零计数。
     *
     * 重启受 [REBOOT_LIMIT] 与 [REBOOT_COOLDOWN_MS] 双重限制，超限后只拉起不重启，
     * 防止 setup 组件缺失时无限重启。
     *
     * @return 是否采取了动作（拉起或重启）；"等待"与"已在前台"返回 false
     */
    @JvmStatic
    fun launchSetupWizardIfNeeded(context: Context): Boolean {
        if (!isSetupWizardPending(context)) {
            // 设备已完成初始化，残留的倒计时与计数一并清掉
            clearMonitor(context)
            resetRebootState(context)
            return false
        }

        val prefs = prefs(context)
        val now = System.currentTimeMillis()
        val lastLaunch = prefs.getLong(KEY_LAST_LAUNCH, 0L)
        val visibility = queryVisibility(context)

        // 1. 已启动且在前台：引导正常进行，撤销倒计时并清零重启计数
        if (visibility == SetupVisibility.FOREGROUND) {
            clearMonitor(context)
            resetRebootState(context)
            return false
        }

        // 2. 还没启动（或探测不可用）：先拉起，绝不在这一步重启
        if (visibility == SetupVisibility.NOT_RUNNING || visibility == SetupVisibility.UNKNOWN) {
            if (lastLaunch > 0 && now - lastLaunch < LAUNCH_GRACE_MS) {
                Log.d(TAG, "setup wizard not started yet, waiting for launch grace")
                return false
            }
            clearMonitor(context)
            return launchAndMark(context, prefs, now)
        }

        // 3. 已启动但不在前台：先看是不是还在启动宽限期
        if (lastLaunch > 0 && now - lastLaunch < LAUNCH_GRACE_MS) {
            Log.d(TAG, "setup wizard started, still in launch grace")
            return false
        }

        // 4. 倒计时：整段窗口都不在前台才重启
        val monitorStart = prefs.getLong(KEY_MONITOR_START, 0L)
        if (monitorStart <= 0L) {
            prefs.edit().putLong(KEY_MONITOR_START, now).apply()
            Log.i(
                TAG,
                "setup wizard backgrounded, countdown ${MONITOR_WINDOW_MS}ms " +
                    "(sample every ${MONITOR_INTERVAL_MS}ms)",
            )
            return false
        }

        val elapsed = now - monitorStart
        if (elapsed < MONITOR_WINDOW_MS) {
            Log.d(
                TAG,
                "countdown running: ${elapsed}ms / ${MONITOR_WINDOW_MS}ms, still background",
            )
            return false
        }

        // 倒计时结束，期间每一次采样都不在前台 → 重启
        clearMonitor(context)
        val rebootCount = prefs.getInt(KEY_REBOOT_COUNT, 0)
        val lastReboot = prefs.getLong(KEY_LAST_REBOOT, 0L)
        val coolingDown = lastReboot > 0 && now - lastReboot < REBOOT_COOLDOWN_MS
        if (rebootCount >= REBOOT_LIMIT || coolingDown) {
            Log.w(TAG, "reboot suppressed (count=$rebootCount, coolingDown=$coolingDown)")
            return launchAndMark(context, prefs, now)
        }

        prefs.edit()
            .putInt(KEY_REBOOT_COUNT, rebootCount + 1)
            .putLong(KEY_LAST_REBOOT, now)
            .putLong(KEY_LAST_LAUNCH, 0L)
            .apply()
        Log.i(
            TAG,
            "setup wizard stayed in background for ${elapsed}ms, rebooting (count=${rebootCount + 1})",
        )
        return rebootDevice(context)
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

    /** 撤销倒计时（回到前台或设备已完成初始化时调用） */
    private fun clearMonitor(context: Context) {
        val prefs = prefs(context)
        if (prefs.getLong(KEY_MONITOR_START, 0L) == 0L) return
        prefs.edit().putLong(KEY_MONITOR_START, 0L).apply()
    }

    /** 回到引导内即清零计数，下次真正退出时重新开始计 */
    private fun resetRebootState(context: Context) {
        val prefs = prefs(context)
        if (prefs.getInt(KEY_REBOOT_COUNT, 0) == 0 &&
            prefs.getLong(KEY_LAST_LAUNCH, 0L) == 0L &&
            prefs.getLong(KEY_LAST_REBOOT, 0L) == 0L
        ) {
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

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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
