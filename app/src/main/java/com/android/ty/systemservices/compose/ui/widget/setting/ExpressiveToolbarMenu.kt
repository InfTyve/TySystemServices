// Material 3 Expressive 风格的工具栏菜单：动作图标 + 卡片式溢出菜单。
// 由 CollapsingToolbarBaseActivity 在 useExpressiveOverflowMenu = true 时挂到 Toolbar 尾部。
//
// 溢出部分沿用项目里的 GroupedDropdownMenuPopup 思路：直接使用 Material3 的
// DropdownMenuPopup / DropdownMenuGroup / MenuDefaults 形状体系，卡片外观、分组圆角、
// 进出场动画都由框架负责，本文件只负责「怎么分组、往哪弹、多宽」。
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.android.ty.systemservices.compose.ui.widget.setting

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.DropdownMenuPopupPositionProvider
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties

/** 一条菜单项：已从 MenuItem 抽出的、Compose 侧需要的字段 */
data class ExpressiveMenuItem(
    val itemId: Int,
    val title: CharSequence,
    val icon: ImageBitmap?,
    val enabled: Boolean,
    /** true = 放在工具栏上（showAsAction always / ifRoom），false = 进溢出卡片 */
    val showAsAction: Boolean,
    /** 与上一条之间是否需要分组（菜单 XML 里 group 变化处） */
    val startsNewGroup: Boolean = false,
)

/** 溢出卡片宽度，和改造前保持一致 */
private val OverflowMenuWidth = 252.dp

/** 卡片与三点按钮之间的垂直间距 */
private val OverflowMenuGap = 6.dp

/**
 * 工具栏右侧的动作区：
 * - 前几个是 `showAsAction` 的图标按钮（Circle 触摸区，跟返回按钮同一套 Expressive 语言）
 * - 最后一个是三点溢出按钮，点开后是 Material3 分组卡片菜单
 *
 * 整块由 Compose 绘制，因此不再受 AppCompat Toolbar 原生 overflow 样式影响。
 */
@Composable
fun ExpressiveToolbarMenu(
    items: List<ExpressiveMenuItem>,
    onItemClick: (ExpressiveMenuItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val actions = remember(items) { items.filter { it.showAsAction } }
    // 溢出项按 startsNewGroup 切成若干组，每组在卡片里是一块独立的圆角
    val groups = remember(items) {
        val result = mutableListOf<MutableList<ExpressiveMenuItem>>()
        items.filterNot { it.showAsAction }.forEach { item ->
            if (result.isEmpty() || item.startsNewGroup) {
                result += mutableListOf(item)
            } else {
                result.last() += item
            }
        }
        result
    }
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        actions.forEach { item ->
            ExpressiveMenuIconButton(
                icon = item.icon,
                contentDescription = item.title.toString(),
                enabled = item.enabled,
                onClick = { onItemClick(item) },
            )
        }

        if (groups.isNotEmpty()) {
            Box {
                ExpressiveMenuIconButton(
                    vectorIcon = Icons.Rounded.MoreVert,
                    contentDescription = "More",
                    onClick = { expanded = true },
                )

                val endInsetPx = with(LocalDensity.current) { 4.dp.roundToPx() }
                val gapPx = with(LocalDensity.current) { OverflowMenuGap.roundToPx() }

                DropdownMenuPopup(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.width(OverflowMenuWidth),
                    popupPositionProvider = remember(gapPx, endInsetPx) {
                        ToolbarOverflowPositionProvider(gapPx, endInsetPx)
                    },
                    properties = MenuDefaults.DefaultMenuProperties,
                ) {
                    groups.forEachIndexed { groupIndex, group ->
                        if (groupIndex > 0) {
                            Spacer(Modifier.height(2.dp))
                        }
                        DropdownMenuGroup(
                            shapes = MenuDefaults.groupShape(
                                index = groupIndex,
                                count = groups.size,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            group.forEachIndexed { itemIndex, item ->
                                val leading: (@Composable () -> Unit)? = item.icon?.let { bitmap ->
                                    {
                                        Image(
                                            painter = remember(bitmap) { BitmapPainter(bitmap) },
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            colorFilter = ColorFilter.tint(
                                                MaterialTheme.colorScheme.onSurfaceVariant,
                                            ),
                                        )
                                    }
                                }
                                // 组内首/中/尾用不同圆角，拼成一整块圆角卡片
                                val itemShape = when {
                                    group.size == 1 -> MenuDefaults.standaloneItemShape
                                    itemIndex == 0 -> MenuDefaults.leadingItemShape
                                    itemIndex == group.size - 1 -> MenuDefaults.trailingItemShape
                                    else -> MenuDefaults.middleItemShape
                                }
                                DropdownMenuItem(
                                    onClick = {
                                        expanded = false
                                        onItemClick(item)
                                    },
                                    text = {
                                        Text(
                                            text = item.title.toString(),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    shape = itemShape,
                                    modifier = Modifier.fillMaxWidth(),
                                    leadingIcon = leading,
                                    enabled = item.enabled,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 把卡片钉在三点按钮正下方、右边缘对齐。
 *
 * `transformOrigin` 交给 Material3，进场缩放从右上角弹出；下方放不下时翻到按钮上方。
 */
private class ToolbarOverflowPositionProvider(
    private val verticalGapPx: Int,
    private val endInsetPx: Int,
) : DropdownMenuPopupPositionProvider {

    override val transformOrigin: TransformOrigin = TransformOrigin(1f, 0f)

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val x = (anchorBounds.right - endInsetPx - popupContentSize.width).coerceIn(0, maxX)

        val below = anchorBounds.bottom + verticalGapPx
        val y = if (below + popupContentSize.height <= windowSize.height) {
            below
        } else {
            (anchorBounds.top - popupContentSize.height - verticalGapPx).coerceAtLeast(0)
        }
        return IntOffset(x, y)
    }
}

/**
 * 圆形图标按钮。动作项用菜单自带的图标（Bitmap），溢出按钮用内置矢量图标。
 */
@Composable
private fun ExpressiveMenuIconButton(
    onClick: () -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageBitmap? = null,
    vectorIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val tint = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    }

    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(44.dp),
        shapes = IconButtonDefaults.shapes(shape = CircleShape),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.Transparent,
            contentColor = tint,
        ),
    ) {
        when {
            vectorIcon != null -> Icon(
                imageVector = vectorIcon,
                contentDescription = contentDescription,
                modifier = Modifier.size(24.dp),
                tint = tint,
            )

            icon != null -> Image(
                painter = remember(icon) { BitmapPainter(icon) },
                contentDescription = contentDescription,
                modifier = Modifier.size(22.dp),
                colorFilter = ColorFilter.tint(tint),
            )

            else -> Text(
                text = contentDescription?.take(1) ?: "?",
                style = MaterialTheme.typography.titleMedium,
                color = tint,
            )
        }
    }
}
