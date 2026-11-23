// 统一的「卡片底 + 居中图标」容器。
// 返回按钮、工具栏右侧的动作/溢出按钮、About 页功能卡片的图标块都走这一份，
// 以后新 Activity 的工具栏图标直接调 ExpressiveIconCard 即可，不需要再抄颜色和形状。
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.android.ty.systemservices.compose.ui.widget.setting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 卡片容器的默认配色。
 *
 * 所有「有底的图标」都从这里取色，改一处就是全局改：
 * 返回按钮、工具栏动作按钮、About 功能卡片的图标块。
 */
object ExpressiveCardDefaults {

    /** 卡片底：surfaceContainerHighest 60%，在深/浅色主题下都刚好浮起一层 */
    @Composable
    fun containerColor(): Color =
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)

    /** 卡片上的图标色 */
    @Composable
    fun contentColor(): Color = MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * 通用图标卡片：一块圆角/圆形容器 + 居中的图标内容。
 *
 * 传 [onClick] 就是按钮（走 [IconButton]，自带波纹、禁用态、无障碍语义）；
 * 不传就是纯装饰块（About 卡片左上角那种）。两者共用同一套容器色和形状，
 * 所以视觉上永远是「同一张卡」。
 *
 * @param size 容器边长。按钮建议不小于 48dp（触摸区），装饰块按设计稿给
 * @param shape 默认圆形；About 的图标块传 `RoundedCornerShape(14.dp)`
 * @param containerColor 容器底色，默认 [ExpressiveCardDefaults.containerColor]
 * @param contentColor 图标色，默认 [ExpressiveCardDefaults.contentColor]
 * @param content 图标内容，通常是 [androidx.compose.material3.Icon] 或 `Image`
 */
@Composable
fun ExpressiveIconCard(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    shape: Shape = CircleShape,
    containerColor: Color = ExpressiveCardDefaults.containerColor(),
    contentColor: Color = ExpressiveCardDefaults.contentColor(),
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (onClick != null) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.size(size),
            shapes = IconButtonDefaults.shapes(shape = shape),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = containerColor,
                contentColor = contentColor,
            ),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(containerColor),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}
