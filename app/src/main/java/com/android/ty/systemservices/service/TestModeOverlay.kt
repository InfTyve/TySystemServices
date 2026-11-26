package com.android.ty.systemservices.service

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.android.ty.systemservices.R

/**
 * TestMode 悬浮窗控制器。
 *
 * 加窗走两条路，按权限自动选择：
 *
 * 1. **系统层级**（首选）：持 [PERM_INTERNAL_SYSTEM_WINDOW] 时用
 *    [WindowManager.LayoutParams.TYPE_SYSTEM_ALERT]（2003），
 *    层级在状态栏（2000）之上、与 SystemUI 同一梯队；
 *    判权限只看签名，**完全不受「显示在其他应用上层」开关影响**——
 *    用户在设置里关掉那个开关，浮层照旧存在。
 * 2. **普通浮层**（回退）：没有系统权限时才用
 *    [WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY]，此时才依赖 appop。
 *
 * 不抢占焦点、不拦截触摸，只作为状态指示。
 *
 * 开关状态存在 `dev_settings/test_mode_enabled`，进程重启后由
 * [ServiceSettings.onCreate] 调 [restore] 自动恢复，因此重启应用仍然记得。
 */
object TestModeOverlay {

    private const val TAG = "TestModeOverlay"
    private const val PREFS_NAME = "dev_settings"

    /** 系统私有窗口权限：signature 级，平台签名的系统应用自动授予 */
    private const val PERM_INTERNAL_SYSTEM_WINDOW =
        "android.permission.INTERNAL_SYSTEM_WINDOW"

    /** 浮层数量：TestMode + 预览开发阶段 */
    private const val OVERLAY_COUNT = 2
    private const val KEY_ENABLED = "test_mode_enabled"

    /**
     * 守护状态的系统级镜像。
     *
     * system_server 侧 `OverlayBoot` 的心跳每 5 分钟读一次这个值：
     * 为 0 就完全不碰本应用，避免"开关已关、进程却被反复拉起"。
     */
    private const val GLOBAL_DAEMON_KEY = "ty_overlay_daemon"

    /** 当前挂着的所有浮层（TestMode + 预览开发阶段），空表示未显示 */
    private val overlayViews = mutableListOf<View>()

    /** 读取持久化的开关状态 */
    @JvmStatic
    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    /**
     * 设置开关：写入持久化 + 立即生效。
     *
     * 注意返回值语义是**最终状态**（true = 现在正处于开启态），不是"操作是否成功"。
     * 早期版本直接 `return hide()` 返回了"移除成功"的 true，导致关掉之后
     * 调用方把 true 赋回开关状态，界面要等重新进页面才刷新。
     *
     * @return 最终是否处于开启态（缺少权限或浮层挂不上时都是 false）
     */
    fun setEnabled(context: Context, enabled: Boolean): Boolean {
        val app = context.applicationContext
        if (enabled && !canDrawOverlays(app)) {
            Log.w(TAG, "missing SYSTEM_ALERT_WINDOW permission")
            return false
        }

        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        if (!enabled) {
            prefs.edit().putBoolean(KEY_ENABLED, false).apply()
            hide(app)
            // 先让 system_server 停止唤醒，再停本地守护，顺序不能反
            syncGlobalFlag(app, false)
            // 关掉开关就不再需要常驻守护：取消闹钟并停掉 service
            ServiceDaemon.stop(app)
            return false
        }

        // 先真正挂上浮层，成功才把状态记为开启，避免"开关显示开、浮层却没出现"
        val shown = show(app)
        prefs.edit().putBoolean(KEY_ENABLED, shown).apply()
        syncGlobalFlag(app, shown)
        // 开启后交给守护体系：START_STICKY + 闹钟 + system_server 心跳三重兜底
        if (shown) ServiceDaemon.start(app) else ServiceDaemon.stop(app)
        return shown
    }

    /** 进程启动时按持久化状态恢复浮层 */
    @JvmStatic
    fun restore(context: Context) {
        val app = context.applicationContext
        val enabled = isEnabled(app) && canDrawOverlays(app)
        if (enabled) show(app) else hide(app)
        // 每次进程启动同步一次，保证 system_server 心跳看到的状态与应用一致
        syncGlobalFlag(app, enabled)
        watchOverlayOp(app)
    }

    /** appop 监听器只注册一次，进程内常驻 */
    private var appOpsWatcher: AppOpsManager.OnOpChangedListener? = null

    /**
     * 盯住「显示在其他应用上层」开关。
     *
     * 系统在收回 appop 时会直接把 [WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY]
     * 窗口摘掉，应用侧收不到任何回调——这就是「一关开关浮层就没了」的直接原因。
     *
     * 这里在 appop 变化时按当前权限重挂一遍：有系统签名权限就走 2003，
     * 窗口立刻回到屏幕上，且后续不再受这个开关影响。真正的启动路径仍然只有一条，
     * 不引入第二套生命周期。
     */
    private fun watchOverlayOp(context: Context) {
        if (appOpsWatcher != null) return
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return
        val handler = Handler(Looper.getMainLooper())
        val listener = AppOpsManager.OnOpChangedListener { _, _ ->
            handler.post {
                if (!isEnabled(context)) return@post
                // 窗口可能已被系统摘掉，本地列表里的引用是脏的，先清空再重建
                hide(context)
                show(context)
            }
        }
        try {
            appOps.startWatchingMode(
                AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                context.packageName,
                listener,
            )
            appOpsWatcher = listener
        } catch (t: Throwable) {
            Log.w(TAG, "watch overlay appop failed", t)
        }
    }

    /** 把开关状态镜像到 Settings.Global，供 system_server 心跳读取 */
    private fun syncGlobalFlag(context: Context, enabled: Boolean) {
        try {
            Settings.Global.putInt(
                context.contentResolver,
                GLOBAL_DAEMON_KEY,
                if (enabled) 1 else 0,
            )
        } catch (t: Throwable) {
            Log.w(TAG, "write Settings.Global $GLOBAL_DAEMON_KEY failed", t)
        }
    }

    /**
     * 是否具备加窗能力。
     *
     * **只要拿到系统私有窗口权限就算有**，不再看 appop：
     * 否则用户把「显示在其他应用上层」一关，恢复流程就会主动 hide，
     * 浮层凭空消失——这正是要修的行为。
     */
    fun canDrawOverlays(context: Context): Boolean =
        hasSystemWindowPermission(context) || Settings.canDrawOverlays(context)

    /** 是否持有系统私有窗口权限（签名为准，与 appop 无关） */
    private fun hasSystemWindowPermission(context: Context): Boolean =
        context.checkSelfPermission(PERM_INTERNAL_SYSTEM_WINDOW) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * 选窗口类型：有系统权限就挂到系统层级（2003，高于状态栏），
     * 否则退回普通应用浮层（2038，需要 appop）。
     */
    private fun overlayWindowType(context: Context): Int =
        if (hasSystemWindowPermission(context)) {
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        } else {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        }

    /** 跳系统设置页申请悬浮窗权限 */
    fun requestPermission(context: Context) {
        val app = context.applicationContext
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${app.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            app.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "open overlay permission page failed", e)
        }
    }

    private fun show(context: Context): Boolean {
        // 完整挂上过就直接返回；只挂上一半（某次 addView 失败）则清掉重建，
        // 避免"永远少一个浮层"且后续 show 都直接返回 true
        if (overlayViews.size >= OVERLAY_COUNT) return true
        if (overlayViews.isNotEmpty()) hide(context)

        val wm = context.getSystemService(WindowManager::class.java) ?: return false
        val density = context.resources.displayMetrics.density

        // 第二个浮层挂在第一个下面，两行文字都带中英双语
        val specs = listOf(
            context.getString(R.string.dev_test_mode_overlay) to (48 * density).toInt(),
            previewText(context) to (92 * density).toInt(),
        )

        var addedAny = false
        val preferredType = overlayWindowType(context)
        for ((text, offsetY) in specs) {
            val view = createLabel(context, text)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                preferredType,
                // 不抢焦点、不拦触摸，纯粹作为指示层，不影响下面应用的交互
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = offsetY
            }
            var added = tryAdd(wm, view, params, text)
            // 系统层级被 ROM 拒掉（少数发行版会收紧 2003）时退回普通浮层，
            // 这样至少在有 appop 的机器上仍然能显示
            if (!added && preferredType != WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY) {
                params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                added = tryAdd(wm, view, params, text)
            }
            if (added) {
                overlayViews.add(view)
                addedAny = true
            }
        }
        return addedAny
    }

    /** addView 的薄封装：失败只记日志，交给调用方决定是否回退 */
    private fun tryAdd(
        wm: WindowManager,
        view: View,
        params: WindowManager.LayoutParams,
        tag: String,
    ): Boolean = try {
        wm.addView(view, params)
        true
    } catch (e: Exception) {
        Log.e(TAG, "add overlay view failed (type=${params.type}): $tag", e)
        false
    }

    /** 预览浮层文案：本地化一行 + 英文一行，语言相同时只显示一行 */
    private fun previewText(context: Context): String {
        val localized = context.getString(R.string.dev_preview_overlay)
        val english = context.getString(R.string.dev_preview_overlay_en)
        return if (localized == english) localized else "$localized\n$english"
    }

    private fun createLabel(context: Context, text: String): View {
        val density = context.resources.displayMetrics.density
        return TextView(context).apply {
            this.text = text
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            val padH = (12 * density).toInt()
            val padV = (6 * density).toInt()
            setPadding(padH, padV, padH, padV)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16 * density
                setColor(0xCC000000.toInt())
            }
        }
    }

    private fun hide(context: Context): Boolean {
        if (overlayViews.isEmpty()) return true
        val wm = context.getSystemService(WindowManager::class.java)
        overlayViews.forEach { view ->
            try {
                wm?.removeView(view)
            } catch (e: Exception) {
                Log.w(TAG, "remove overlay view failed", e)
            }
        }
        overlayViews.clear()
        return true
    }
}
