/*
 * 移植自 Zalith Launcher 2（GPL-3.0-only）
 * 原文件：ui/screens/content/settings/layouts/SettingsCard.kt
 *
 * 改动：包名改为本项目；BackgroundCard / TitleAndSummary 改由本包 Components_Luch.kt 提供。
 */

package com.android.ty.systemservices.compose.ui.settings.luch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 根据卡片在 UI 组中的位置，选择不同的圆角形状
 */
enum class CardPosition {
    /** 位于 UI 组的顶部 */
    Top,

    /** 位于 UI 组的顶部左侧 */
    TopStart,

    /** 位于 UI 组的顶部右侧 */
    TopEnd,

    /** 位于 UI 组的中部 */
    Middle,

    /** 位于 UI 组的底部 */
    Bottom,

    /** 位于 UI 组的底部左侧 */
    BottomStart,

    /** 位于 UI 组的底部右侧 */
    BottomEnd,

    /** 单个 UI 组件 */
    Single,
}

/**
 * 根据 UI 组件在组中的位置决定的形状。
 */
@Composable
fun rememberSettingsCardShape(
    position: CardPosition,
    outerShape: Dp = 28.dp,
    innerShape: Dp = 4.dp,
): Shape {
    return remember(position, outerShape, innerShape) {
        when (position) {
            CardPosition.Top -> RoundedCornerShape(
                topStart = outerShape,
                topEnd = outerShape,
                bottomStart = innerShape,
                bottomEnd = innerShape,
            )

            CardPosition.TopStart -> RoundedCornerShape(
                topStart = outerShape,
                topEnd = innerShape,
                bottomStart = innerShape,
                bottomEnd = innerShape,
            )

            CardPosition.TopEnd -> RoundedCornerShape(
                topStart = innerShape,
                topEnd = outerShape,
                bottomStart = innerShape,
                bottomEnd = innerShape,
            )

            CardPosition.Middle -> RoundedCornerShape(innerShape)
            CardPosition.Bottom -> RoundedCornerShape(
                topStart = innerShape,
                topEnd = innerShape,
                bottomStart = outerShape,
                bottomEnd = outerShape,
            )

            CardPosition.BottomStart -> RoundedCornerShape(
                topStart = innerShape,
                topEnd = innerShape,
                bottomStart = outerShape,
                bottomEnd = innerShape,
            )

            CardPosition.BottomEnd -> RoundedCornerShape(
                topStart = innerShape,
                topEnd = innerShape,
                bottomStart = innerShape,
                bottomEnd = outerShape,
            )

            CardPosition.Single -> RoundedCornerShape(outerShape)
        }
    }
}

@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    position: CardPosition,
    outerShape: Dp = 28.dp,
    innerShape: Dp = 4.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = rememberSettingsCardShape(position, outerShape, innerShape)

    BackgroundCard(
        modifier = modifier,
        shape = shape,
        content = content,
    )
}

@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    position: CardPosition,
    outerShape: Dp = 28.dp,
    innerShape: Dp = 4.dp,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = rememberSettingsCardShape(position, outerShape, innerShape)

    BackgroundCard(
        modifier = modifier,
        shape = shape,
        onClick = onClick,
        enabled = enabled,
        content = content,
    )
}

@Composable
fun SettingsCard(
    position: CardPosition,
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    titleStyle: TextStyle = MaterialTheme.typography.titleSmall,
    summaryStyle: TextStyle = MaterialTheme.typography.labelSmall,
    outerShape: Dp = 28.dp,
    innerShape: Dp = 4.dp,
    innerPadding: PaddingValues = PaddingValues(all = 16.dp),
    onClick: () -> Unit,
    trailingIcon: (@Composable RowScope.() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val shape = rememberSettingsCardShape(position, outerShape, innerShape)

    BackgroundCard(
        modifier = modifier,
        shape = shape,
        onClick = onClick,
        enabled = enabled,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(innerPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TitleAndSummary(
                modifier = Modifier.weight(1f),
                title = title,
                summary = summary,
                titleStyle = titleStyle,
                summaryStyle = summaryStyle,
            )
            trailingIcon?.let { trailing ->
                Row(
                    modifier = Modifier.align(Alignment.CenterVertically),
                    content = trailing,
                )
            }
        }
    }
}

@Composable
fun SettingsCardColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}
