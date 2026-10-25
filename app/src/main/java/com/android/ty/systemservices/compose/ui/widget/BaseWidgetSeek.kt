// SPDX-License-Identifier: GPL-3.0-only
package com.android.ty.systemservices.compose.ui.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/**
 * [BaseWidget] 的滑杆版本：**专供 SeekBar 使用**。
 *
 * 与 [BaseWidget] 的差异只有一处——底部多了一条 [Slider]，其余完全对齐：
 * - 头部仍是 M3 `ListItem`（图标 / 标题 / 描述 / 右侧数值），左右 16dp、上下 12dp，
 *   与同组其它条目同一套度量，卡片高度只多出滑杆本身；
 * - 形状取 [LocalSegmentedItemShape]，放进 `SegmentedColumn` 后会自动与相邻条目
 *   "粘连"：首卡只圆上角、中卡直角、末卡只圆下角、单卡四角全圆；
 * - **无水波纹**：整卡不可点（交互在滑杆上），头部使用非点击态 `ListItem`，
 *   避免"点一下有涟漪但什么也没发生"的误导；
 * - 只承担"标题 + 滑杆"这一件事，附加内容（例如枚举单选）请不要塞进来，
 *   需要组合时单独做一张卡，视觉上仍是同一套卡片。
 *
 * @param item 条目描述（图标 / 标题 / 描述 / enabled，支持资源 id）
 * @param progress 当前进度
 * @param onProgressChange 拖动回调
 * @param valueText 右侧显示的当前值文本
 * @param modifier 外部修饰符
 * @param valueRange 取值范围
 * @param steps 档位数（0 表示连续）
 * @param containerColor 卡片底色，默认 surfaceBright
 */
@Composable
fun BaseWidget_Seek(
    item: SettingsItem,
    progress: Float,
    onProgressChange: (Float) -> Unit,
    valueText: String,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    containerColor: Color? = null,
) {
    val shape = LocalSegmentedItemShape.current
    val title = item.resolvedTitle()
    val description = item.resolvedDescription()
    val enabled = item.enabled

    val colors = ListItemDefaults.colors(
        // 底色交给外层 Column，ListItem 保持透明，避免圆角处出现双层面
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        leadingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        trailingContentColor = MaterialTheme.colorScheme.primary,
        supportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    val leading: (@Composable () -> Unit)? = when {
        item.iconRes != 0 -> {
            {
                Icon(
                    painter = painterResource(item.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        item.icon != null -> {
            {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        else -> null
    }

    val supporting: (@Composable () -> Unit)? = description?.let { text ->
        { Text(text = text, style = MaterialTheme.typography.bodyMedium) }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(containerColor ?: MaterialTheme.colorScheme.surfaceBright),
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.alpha(if (enabled) 1f else 0.38f),
                )
            },
            supportingContent = supporting,
            leadingContent = leading,
            trailingContent = {
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.alpha(if (enabled) 1f else 0.38f),
                )
            },
            colors = colors,
        )
        Slider(
            value = progress,
            onValueChange = onProgressChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp)
                .padding(bottom = 12.dp),
        )
    }
}
