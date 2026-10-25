/*
 * 移植自 Zalith Launcher 2（GPL-3.0-only）
 * 原文件：ui/screens/content/settings/layouts/Switch_Luch.kt
 *
 * 改动：
 * 1. 包名改为本项目；
 * 2. 去掉依赖 `BooleanSettingUnit` 的重载，只保留受控版本；
 * 3. 卡片底板与头部统一为 BaseWidget_Luch（BaseWidget 的排版 + 内容插槽），
 *    因此不再需要 position / outerShape / innerShape，圆角由 SegmentedColumn 注入；
 * 4. 原实现里 `trailingIcon?.invoke(this)` 在两层嵌套 Row 的内层 RowScope 上调用，
 *    这里合并成一层，避免作用域歧义。
 */

package com.android.ty.systemservices.compose.ui.settings.luch

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.android.ty.systemservices.compose.ui.widget.BaseWidget_Luch

/**
 * 开关卡片：整卡可点，右侧 Switch；可选 [trailingIcon] 放在 Switch 前面，
 * 可选 [columnLayout] 在头部下方追加内容。
 */
@Composable
fun SwitchSettingsCard(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    trailingIcon: (@Composable RowScope.() -> Unit)? = null,
    columnLayout: (@Composable ColumnScope.() -> Unit)? = null,
) {
    BaseWidget_Luch(
        title = title,
        modifier = modifier,
        description = summary,
        icon = icon,
        enabled = enabled,
        onClick = { onCheckedChange(!checked) },
        trailingContent = {
            trailingIcon?.let { trailing ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    trailing()
                }
            }
            DefaultSwitch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = { value -> onCheckedChange(value) },
            )
        },
        content = columnLayout,
    )
}
