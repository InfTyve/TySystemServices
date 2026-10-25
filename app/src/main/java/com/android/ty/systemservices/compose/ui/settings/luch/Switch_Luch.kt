/*
 * 移植自 Zalith Launcher 2（GPL-3.0-only）
 * 原文件：ui/screens/content/settings/layouts/Switch_Luch.kt
 *
 * 改动：
 * 1. 包名改为本项目；
 * 2. 去掉依赖 `BooleanSettingUnit` 的重载，只保留受控版本；
 * 3. 条目描述统一用 SettingsItem，卡片本体走 BaseWidget_Luch，圆角由 SegmentedColumn 注入；
 * 4. 拇指图标改为 Crossfade + 旋转（勾选时 check，未选时 close），替代原来的 DefaultSwitch；
 * 5. 修正原实现里 `Switch(...)` 直接当成位置实参传给 BaseWidget_Luch 的写法
 *    （会导致语法错误），改为放进 trailingContent 插槽。
 */

package com.android.ty.systemservices.compose.ui.settings.luch

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.android.ty.systemservices.R
import com.android.ty.systemservices.compose.ui.widget.BaseWidget_Luch
import com.android.ty.systemservices.compose.ui.widget.SettingsItem

/**
 * 开关卡片：整卡可点，右侧 Switch；可选 [trailingIcon] 放在 Switch 前面，
 * 可选 [columnLayout] 在头部下方追加内容。
 */
@Composable
fun SwitchSettingsCard(
    item: SettingsItem,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    trailingIcon: (@Composable RowScope.() -> Unit)? = null,
    columnLayout: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val switchColors = SwitchDefaults.colors()

    BaseWidget_Luch(
        item = item,
        modifier = modifier,
        onClick = { onCheckedChange(!checked) },
        trailingContent = {
            trailingIcon?.let { trailing ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    trailing()
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = item.enabled,
                colors = switchColors,
                interactionSource = interactionSource,
                thumbContent = {
                    val rotation by animateFloatAsState(
                        targetValue = if (checked) 0f else -180f,
                        animationSpec = tween(durationMillis = 200),
                        label = "switchThumbRotation",
                    )
                    Crossfade(
                        targetState = checked,
                        modifier = Modifier.rotate(rotation),
                        label = "switchThumbIcon",
                    ) { isChecked ->
                        Icon(
                            painter = painterResource(
                                if (isChecked) R.drawable.ic_check else R.drawable.ic_close,
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isChecked) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                },
            )
        },
        content = columnLayout,
    )
}
