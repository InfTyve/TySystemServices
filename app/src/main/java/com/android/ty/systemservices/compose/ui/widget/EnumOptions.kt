// SPDX-License-Identifier: GPL-3.0-only
package com.android.ty.systemservices.compose.ui.widget

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha

/**
 * 枚举单选行：[EnumSettingsCard] 里"选项区"的独立版本（移植自 Zalith Launcher 2）。
 *
 * 与卡片版一致，选项**完全动态**：条目来自外部传入的 [entries]（一般就是 `Enum.entries`），
 * 文案由 [getText] 提供，不在此处硬编码任何分支；超出宽度时按 [maxItemsInEachRow] 换行，
 * 高度变化用 `animateContentSize` 平滑过渡。
 *
 * 与卡片版的区别只有两点：不画底板（交给宿主卡片），且不提供"禁用"以外的状态控制。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <E : Enum<E>> EnumOptionsRow(
    value: E,
    entries: List<E>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    disabledAlpha: Float = 0.38f,
    maxItemsInEachRow: Int = Int.MAX_VALUE,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.SpaceEvenly,
    isEnabled: (E) -> Boolean = { true },
    getText: @Composable (E) -> String,
    onSelect: (E) -> Unit,
) {
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(durationMillis = 260)),
        horizontalArrangement = horizontalArrangement,
        maxItemsInEachRow = maxItemsInEachRow,
    ) {
        entries.forEach { item ->
            val itemEnabled = enabled && isEnabled(item)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = value == item,
                    onClick = { onSelect(item) },
                    enabled = itemEnabled,
                )
                Text(
                    text = getText(item),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.alpha(if (itemEnabled) 1f else disabledAlpha),
                )
            }
        }
    }
}
