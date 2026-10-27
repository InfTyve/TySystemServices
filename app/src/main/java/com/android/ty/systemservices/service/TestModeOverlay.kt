package com.android.ty.systemservices.service

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView

/**
 * TestMode 悬浮窗控制器。
 *
 * 用 [WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY] 添加一个常驻浮层，
 * 层级在**所有应用窗口之上**（含系统应用、桌面、设置等），不抢占焦点、不拦截触摸，
 * 只作为状态指示。
 *
 * 开关状态存在 `dev_settings/test_mode_enabled`，进程重启后由
 * [ServiceSettings.onCreate] 调 [restore] 自动恢复，因此重启应用仍然记得。
 */
object TestModeOverlay {

    private const val TAG = "TestModeOverlay"
    private const val PREFS_NAME = "dev_settings"
    private const val KEY_ENABLED = "test_mode_enabled"

    /** 当前挂着的浮层，null 表示未显示（避免重复 addView） */
    private var overlayView: View? = null

    /** 读取持久化的开关状态 */
    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    /**
     * 设置开关：写入持久化 + 立即生效。
     *
     * @return 最终是否处于开启态（缺少悬浮窗权限时返回 false，调用方据此回滚开关）
     */
    fun setEnabled(context: Context, enabled: Boolean): Boolean {
        val app = context.applicationContext
        if (enabled && !canDrawOverlays(app)) {
            Log.w(TAG, "missing SYSTEM_ALERT_WINDOW permission")
            return false
        }
        app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
        return if (enabled) show(app) else hide(app)
    }

    /** 进程启动时按持久化状态恢复浮层 */
    fun restore(context: Context) {
        val app = context.applicationContext
        if (isEnabled(app) && canDrawOverlays(app)) {
            show(app)
        } else {
            hide(app)
        }
    }

    /** 是否已获得「显示在其他应用上层」权限 */
    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

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
        if (overlayView != null) return true
        val wm = context.getSystemService(WindowManager::class.java) ?: return false

        val view = TextView(context).apply {
            text = stringResource(R.string.dev_test_mode_title)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            val padH = (12 * resources.displayMetrics.density).toInt()
            val padV = (6 * resources.displayMetrics.density).toInt()
            setPadding(padH, padV, padH, padV)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16 * resources.displayMetrics.density
                setColor(0xCC000000.toInt())
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // 不抢焦点、不拦触摸，纯粹作为指示层，不影响下面应用的交互
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (48 * context.resources.displayMetrics.density).toInt()
        }

        return try {
            wm.addView(view, params)
            overlayView = view
            true
        } catch (e: Exception) {
            Log.e(TAG, "add overlay view failed", e)
            overlayView = null
            false
        }
    }

    private fun hide(context: Context): Boolean {
        val view = overlayView ?: return true
        val wm = context.getSystemService(WindowManager::class.java)
        return try {
            wm?.removeView(view)
            true
        } catch (e: Exception) {
            Log.w(TAG, "remove overlay view failed", e)
            true
        } finally {
            overlayView = null
        }
    }
}
