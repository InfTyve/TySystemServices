// SPDX-License-Identifier: GPL-3.0-only
package com.android.ty.systemservices.compose.ui.widget

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource

/**
 * 设置条目的统一描述：**图标 + 标题 + 描述 + 可用状态**。
 *
 * 设计目的：任何界面都只声明"这是什么条目"，不写 `stringResource` / `painterResource`，
 * 也不关心排版；资源解析与绘制统一由 [BaseWidget_Luch] / [BaseWidget] 系列完成。
 *
 * 文本与图标都支持两种写法，资源 id 优先：
 * ```kotlin
 * // 资源 id：文案与图标都不需要在 Screen 里解析
 * SettingsItem(
 *     titleRes = R.string.ported_switch_title,
 *     descriptionRes = R.string.ported_switch_desc,
 *     iconRes = R.drawable.ic_settings_24dp,
 * )
 *
 * // 直接给值：适合运行期拼出来的标题（例如"当前档位：标准"）
 * SettingsItem(title = "当前档位：标准", icon = Icons.Filled.Tune, description = "动态文案")
 * ```
 *
 * @param title 标题文本（与 [titleRes] 二选一，[titleRes] 优先）
 * @param titleRes 标题字符串资源
 * @param description 描述文本（与 [descriptionRes] 二选一，[descriptionRes] 优先）
 * @param descriptionRes 描述字符串资源
 * @param icon 前导图标（与 [iconRes] 二选一，[iconRes] 优先）
 * @param iconRes 前导图标 drawable 资源
 * @param enabled 是否可用
 */
data class SettingsItem(
    val title: String = "",
    @StringRes val titleRes: Int = 0,
    val description: String? = null,
    @StringRes val descriptionRes: Int = 0,
    val icon: ImageVector? = null,
    @DrawableRes val iconRes: Int = 0,
    val enabled: Boolean = true,
) {
    /** 解析后的标题：有资源用资源，否则用字面量 */
    @Composable
    fun resolvedTitle(): String = if (titleRes != 0) stringResource(titleRes) else title

    /** 解析后的描述：有资源用资源，否则用字面量；空串视为没有描述 */
    @Composable
    fun resolvedDescription(): String? = when {
        descriptionRes != 0 -> stringResource(descriptionRes)
        else -> description?.takeIf { it.isNotEmpty() }
    }

    /** 是否提供了图标（两种来源任一） */
    fun hasIcon(): Boolean = iconRes != 0 || icon != null

    companion object {
        /** 只给字面量时的简写 */
        fun of(
            title: String,
            description: String? = null,
            icon: ImageVector? = null,
            enabled: Boolean = true,
        ): SettingsItem = SettingsItem(
            title = title,
            description = description,
            icon = icon,
            enabled = enabled,
        )
    }
}
