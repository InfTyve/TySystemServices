/*
 * 移植自 Zalith Launcher 2（GPL-3.0-only）
 * 原文件：ui/screens/content/settings/layouts/TextInput_Luch.kt
 *
 * 改动：
 * 1. 包名改为本项目；
 * 2. 去掉依赖 StringSettingUnit 的重载，只保留受控版本；
 * 3. 卡片底板统一为 BaseWidget_Luch，输入框放进内容插槽，与头部左右 16dp 对齐；
 * 4. 原重载把 label 固定包成 `{ Text(label ?: "ignore if blank") }`，
 *    即调用方不传 label 也会出现提示文案；受控版本里 label 只在真正传入时渲染。
 */

package com.android.ty.systemservices.compose.ui.settings.luch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.android.ty.systemservices.compose.ui.widget.BaseWidget_Luch

/**
 * 文本输入卡片：标题 / 摘要 + 单行约束 + 输入框。
 */
@Composable
fun TextInputSettingsCard(
    value: String,
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    onValueChange: (String) -> Unit = {},
    isError: Boolean = false,
    label: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    BaseWidget_Luch(
        title = title,
        modifier = modifier,
        description = summary,
        icon = icon,
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SingleLineTextCheck(
                    text = value,
                    onSingleLined = onValueChange,
                )

                OwnOutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = value,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    onValueChange = onValueChange,
                    isError = isError,
                    label = label,
                    supportingText = supportingText,
                    prefix = prefix,
                    suffix = suffix,
                    singleLine = singleLine,
                    minLines = minLines,
                    shape = MaterialTheme.shapes.large,
                )
            }
        },
    )
}
