// SPDX-License-Identifier: GPL-3.0-only
package com.android.ty.systemservices.compose.ui.widget

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.ty.systemservices.R

/**
 * [BaseWidget] 的展开版：在 ListItem 头部下方多渲染一块 [content]，两者共享同一张圆角卡片背景。
 * 独立于 [BaseWidget] 存在，避免给 Switch / DropDownMenu 等既有调用方引入改动。
 */
@Composable
fun ExpandableBaseWidget(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconRes: Int? = null,
    title: String,
    description: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    onClick: (() -> Unit)? = null,
    containerColor: Color? = null,
    accentColor: Color? = null,
    trailingContent: @Composable () -> Unit = {},
    content: @Composable () -> Unit = {},
) {
    val shape = LocalSegmentedItemShape.current
    val alpha = if (enabled) 1f else 0.38f
    val container = containerColor ?: MaterialTheme.colorScheme.surfaceBright

    val colors = ListItemDefaults.colors(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        leadingContentColor = accentColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
        trailingContentColor = accentColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
        supportingContentColor = when {
            isError -> MaterialTheme.colorScheme.error
            accentColor != null -> accentColor
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
    )

    val headline: @Composable () -> Unit = {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
    }
    val supporting: (@Composable () -> Unit)? = description?.let { d ->
        { Text(text = d, style = MaterialTheme.typography.bodyMedium) }
    }
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container),
    ) {
        val itemModifier = if (onClick != null) {
            Modifier.fillMaxWidth()
        } else {
            Modifier.fillMaxWidth().alpha(alpha)
        }

        if (onClick != null) {
            ListItem(
                modifier = itemModifier,
                content = headline,
                supportingContent = supporting,
                leadingContent = leading,
                trailingContent = trailingContent,
                verticalAlignment = Alignment.CenterVertically,
                colors = colors,
                enabled = enabled,
                onClick = onClick,
            )
        } else {
            ListItem(
                modifier = itemModifier,
                content = headline,
                supportingContent = supporting,
                leadingContent = leading,
                trailingContent = trailingContent,
                verticalAlignment = Alignment.CenterVertically,
                colors = colors,
            )
        }
        content()
    }
}

/**
 * An expandable card: a header row (optional icon + title + description + expand toggle)
 * that reveals [content] when expanded.
 *
 * @param expanded Whether the card is currently expanded.
 * @param onExpandedChange Called when the user toggles the expand state.
 * @param title The primary text displayed in the header.
 * @param description Optional supporting text displayed below the title.
 * @param icon Optional leading icon; omitted when null.
 * @param content The content revealed when expanded.
 */
@Composable
fun ExpandableCard(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    title: String,
    description: String? = null,
    icon: ImageVector? = null,
    content: @Composable () -> Unit = {},
) {
    ExpandableBaseWidget(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        icon = icon,
        title = title,
        description = description,
        onClick = { onExpandedChange(!expanded) },
        trailingContent = {
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = stringResource(if (expanded) R.string.collapse else R.string.expand),
            )
        },
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            content()
        }
    }
}
