package com.android.ty.systemservices.terminal

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Termux 桌面图标的开关。
 *
 * 做法是直接改 [TermuxActivity] 这个组件的 enabled 状态：manifest 里它带着 LAUNCHER，
 * 组件被禁用后桌面图标随之消失；重新启用即恢复，不需要重装、也不影响 TermuxService。
 *
 * 这里以 PackageManager 的状态为准，不额外存 SharedPreferences——
 * 组件状态本身就是持久化的（重启、覆盖安装都在），双份记录反而容易不同步。
 */
object TermuxLauncher {

    private fun component(context: Context) =
        ComponentName(context.packageName, TermuxActivity::class.java.name)

    /** 桌面入口当前是否可见。从未改过时返回 manifest 的默认值（启用）。 */
    fun isEnabled(context: Context): Boolean {
        val state = context.packageManager.getComponentEnabledSetting(component(context))
        return state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }

    /**
     * 显示 / 隐藏桌面入口。
     *
     * [PackageManager.DONT_KILL_APP]：只切组件，不重启进程，避免正在编辑的设置被丢掉。
     * 切换后系统会发 PACKAGE_CHANGED，桌面自己刷新图标。
     */
    fun setEnabled(context: Context, enabled: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            component(context),
            if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            },
            PackageManager.DONT_KILL_APP,
        )
    }
}
