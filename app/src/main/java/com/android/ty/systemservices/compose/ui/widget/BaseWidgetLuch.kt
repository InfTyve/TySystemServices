// SPDX-License-Identifier: GPL-3.0-only
package com.android.ty.systemservices.compose.ui.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/**
 * [BaseWidget] 的"卡片 + 内容区"版本，供移植而来的 SettingsCard 系列使用。
 *
 * 统一要点（与 [BaseWidget] / [BaseWidget_Seek] 完全一致）：
 * - 头部就是 [BaseWidget] 那套 M3 `ListItem`：24dp 前导图标 + `titleMedium` 标题 +
 *   `bodyMedium` 描述，左右 16dp、上下 12dp；
 * - 形状取 [LocalSegmentedItemShape]，放进 `SegmentedColumn` 后自动与其它条目粘连，
 *   不再需要自己算 Top / Middle / Bottom 圆角；
 * - 底色默认 `surfaceBright`，与同组条目同一张卡片；
 * - [onClick] 非空时头部带水波纹（用于开关卡整卡点击、列表卡展开），为空则无水波纹。
 *
 * 与 [BaseWidget] 的唯一区别：多了一个 [content] 插槽，用来放控件本体
 * （单选行 / 滑杆 / 输入框 / 展开列表），插槽自动左右 16dp、底部 12dp，与头部对齐。
 *
 * @param title 标题
 * @param modifier 外部修饰符
 * @param description 可选描述
 * @param icon 可选前导图标（ImageVector）
 * @param iconRes 可选前导图标（drawable 资源 ID），优先于 [icon]
 * @param enabled 是否可用
 * @param onClick 头部点击回调，非空时带水波纹
 * @param containerColor 卡片底色，默认 surfaceBright
 * @param trailingContent 右侧附加内容（开关、箭头、数值等）
 * @param content 头部下方的控件区
 */
@Composable
fun BaseWidget_Luch(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    iconRes: Int? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    containerColor: Color? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val shape = LocalSegmentedItemShape.current
    val contentAlpha = if (enabled) 1f else 0.38f

    val colors = ListItemDefaults.colors(
        // 底色由外层 Column 提供，ListItem 透明，避免圆角处出现双层面
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        leadingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        trailingContentColor = MaterialTheme.colorScheme.primary,
        supportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    val leading: (@Composable () -> Unit)? = when {
        iconRes != null -> {
            {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        icon != null -> {
            { Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp)) }
        }

        else -> null
    }

    val supporting: (@Composable () -> Unit)? = description?.let { text ->
        { Text(text = text, style = MaterialTheme.typography.bodyMedium) }
    }

    val trailing: (@Composable () -> Unit)? = trailingContent?.let { slot ->
        {
            Row(verticalAlignment = Alignment.CenterVertically) {
                slot()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(containerColor ?: MaterialTheme.colorScheme.surfaceBright),
    ) {
        if (onClick != null) {
            // 带点击的重载没有 headlineContent，标题走尾随 content 槽
            ListItem(
                onClick = onClick,
                enabled = enabled,
                leadingContent = leading,
                trailingContent = trailing,
                supportingContent = supporting,
                colors = colors,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.alpha(contentAlpha),
                )
            }
        } else {
            ListItem(
                headlineContent = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.alpha(contentAlpha),
                    )
                },
                supportingContent = supporting,
                leadingContent = leading,
                trailingContent = trailing,
                colors = colors,
            )
        }

        if (content != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp)
                    .padding(bottom = 12.dp)
                    .alpha(contentAlpha),
            ) {
                content()
            }
        }
    }
}
