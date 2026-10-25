/*
 * 移植自 Zalith Launcher 2（GPL-3.0-only）
 * 原文件：ui/screens/content/settings/layouts/Slider_Luch.kt
 *
 * 改动：
 * 1. 包名改为本项目；
 * 2. 去掉依赖 IntSettingUnit / NullableIntSettingUnit 的重载，只保留受控版本；
 * 3. 条目描述统一用 SettingsItem，卡片走 BaseWidget_Luch；
 * 4. 修复 `onValueChange(it.toInt())` 的截断问题，改用 roundToInt()；
 * 5. 精确输入对话框使用当前值（原实现闭包捕获入参 value，拖动后打开会拿到过期值）。
 */

package com.android.ty.systemservices.compose.ui.settings.luch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.ty.systemservices.compose.ui.widget.BaseWidget_Luch
import com.android.ty.systemservices.compose.ui.widget.SettingsItem
import kotlin.math.roundToInt

/**
 * 整数滑杆卡片：标题 / 摘要 + 滑杆 + 右侧数值（可点开精确输入）。
 */
@Composable
fun IntSliderSettingsCard(
    item: SettingsItem,
    value: Int,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    steps: Int = 0,
    suffix: String? = null,
    onValueChangeFinished: () -> Unit = {},
    appendContent: @Composable () -> Unit = {},
    previewContent: @Composable ColumnScope.() -> Unit = {},
) {
    var showValueEditDialog by remember { mutableStateOf(false) }

    BaseWidget_Luch(
        item = item,
        modifier = modifier,
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SimpleTextSlider(
                    modifier = Modifier.fillMaxWidth(),
                    value = value.toFloat(),
                    enabled = item.enabled,
                    onValueChange = { onValueChange(it.roundToInt()) },
                    onValueChangeFinished = onValueChangeFinished,
                    onTextClick = { showValueEditDialog = true },
                    toInt = true,
                    valueRange = valueRange,
                    steps = steps,
                    suffix = suffix,
                )
                appendContent()
                previewContent()
            }
        },
    )

    if (showValueEditDialog) {
        SliderValueEditDialog(
            onDismissRequest = { showValueEditDialog = false },
            title = item.resolvedTitle(),
            valueRange = valueRange,
            // 用当前值而不是闭包捕获的入参，避免拖动后打开对话框拿到旧值
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            onValueChangeFinished = onValueChangeFinished,
            intCheck = true,
        )
    }
}
