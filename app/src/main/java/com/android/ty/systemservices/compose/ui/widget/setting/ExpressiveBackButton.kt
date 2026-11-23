// 提取自 InstallerX Revived 的 NavigationIcon.kt（ExpressiveBackButton）
// 适配当前工程包名 com.android.ty.systemservices.compose，去除对 AppIcons 的依赖。
// 2.0：容器样式收敛到 [ExpressiveIconCard]，和工具栏动作按钮、About 图标块共用一份实现。
package com.android.ty.systemservices.compose.ui.widget.setting

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.ty.systemservices.R

/** 返回按钮直径：沿用旧布局 `back_button_container` 的 48dp */
private val BackButtonSize = 48.dp

/**
 * 标准返回按钮：圆形卡片底 + 返回箭头。
 *
 * 和工具栏右侧的动作按钮、About 功能卡片的图标块走同一个 [ExpressiveIconCard]，
 * 所以「有底的图标」在全局只有一种观感。
 *
 * @param modifier 外部修饰符（布局位置、边距等）
 * @param icon 显示图标，默认返回箭头
 * @param containerColor 卡片底色，默认 [ExpressiveCardDefaults.containerColor]
 * @param contentColor 图标色，默认 [ExpressiveCardDefaults.contentColor]
 * @param contentDescription 无障碍描述
 * @param onClick 点击回调
 */
@Composable
fun ExpressiveBackButton(
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.AutoMirrored.Rounded.ArrowBack,
    containerColor: Color = ExpressiveCardDefaults.containerColor(),
    contentColor: Color = ExpressiveCardDefaults.contentColor(),
    contentDescription: String = stringResource(R.string.back),
    onClick: () -> Unit,
) {
    ExpressiveIconCard(
        modifier = modifier,
        size = BackButtonSize,
        containerColor = containerColor,
        contentColor = contentColor,
        onClick = onClick,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
        )
    }
}
