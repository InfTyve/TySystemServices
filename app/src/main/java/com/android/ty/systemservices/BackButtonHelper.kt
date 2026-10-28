package com.android.ty.systemservices

import android.app.Activity

/**
 * 返回按钮显隐判断。
 *
 * 满足任一条件就显示返回按钮：
 * 1. [Activity.isTaskRoot] 为 false —— 任务栈里还有本应用的上一页；
 * 2. 由系统设置（`com.android.settings`）启动 —— 显示返回按钮，方便回到设置页。
 *
 * 只有「从桌面/其他外部应用作为任务根启动」才隐藏，保持入口页的观感。
 */
class BackButtonHelper {
    companion object {
        private const val SETTINGS_PACKAGE = "com.android.settings"

        @JvmStatic
        fun isFromOwnActivity(activity: Activity): Boolean {
            // 本应用内的二级页：任务栈里还有上一页
            if (!activity.isTaskRoot) return true

            // 从系统设置跳进来
            return isLaunchedFromSettings(activity)
        }

        /**
         * 判断启动者是否为系统设置。
         *
         * 三条路依次尝试，任意一条命中就认为来自设置：
         * - `callingActivity`：设置用 startActivityForResult 启动时有效；
         * - `referrer`：`android-app://com.android.settings` 形式；
         * - `getLaunchedFromPackage()`：任务记录里的启动者包名，最准，但属于 hidden API，
         *   用反射调用，失败时静默跳过。
         */
        private fun isLaunchedFromSettings(activity: Activity): Boolean {
            val callingPackage = try {
                activity.callingActivity?.packageName
            } catch (t: Throwable) {
                null
            }
            if (callingPackage == SETTINGS_PACKAGE) return true

            val referrerAuthority = try {
                activity.referrer?.authority
            } catch (t: Throwable) {
                null
            }
            if (referrerAuthority == SETTINGS_PACKAGE) return true

            val launchedFromPackage = try {
                Activity::class.java
                    .getMethod("getLaunchedFromPackage")
                    .invoke(activity) as? String
            } catch (t: Throwable) {
                null
            }
            return launchedFromPackage == SETTINGS_PACKAGE
        }
    }
}
