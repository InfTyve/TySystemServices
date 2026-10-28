package com.android.ty.systemservices.compose.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 贡献者条目的展示模型。
 *
 * [icon] 只是卡片顶部的大图标占位；后续接入真实头像时，
 * 把它换成图片加载即可，卡片布局不用动。
 */
private data class Contributor(
    val name: String,
    val role: String,
    val summary: String,
    val icon: ImageVector,
)

/** 示例数据，按实际贡献者替换即可 */
private val contributors = listOf(
    Contributor(
        name = "Ty",
        role = "项目维护",
        summary = "系统服务集成、Compose 界面与悬浮窗守护逻辑",
        icon = Icons.Filled.Build,
    ),
    Contributor(
        name = "Zalith Launcher",
        role = "组件参考",
        summary = "SettingsCard 系列组件的移植来源",
        icon = Icons.Filled.Extension,
    ),
    Contributor(
        name = "MonetCompat",
        role = "取色兼容",
        summary = "Android 12 以下的 Material You 动态取色支持",
        icon = Icons.Filled.Palette,
    ),
    Contributor(
        name = "SettingsLib",
        role = "基础组件",
        summary = "设置页 Preference 体系与卡片样式参考",
        icon = Icons.Filled.Code,
    ),
)

/**
 * 贡献者列表页。
 *
 * 宿主 [com.android.ty.systemservices.compose.ContributorsActivity] 走大标题体系，
 * 本页不再自带 `TopAppBar`；`LazyColumn` 通过 nestedScroll 与折叠头联动。
 * 卡片为「大图标区 + 底部标题条」，整卡可点，带 M3 水波纹。
 */
@Composable
fun ContributorsScreen() {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                // 与 CollapsingToolbar 的 AppBar 联动
                .nestedScroll(rememberNestedScrollInteropConnection())
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(contributors) { contributor ->
                ContributorCard(contributor)
            }
        }
    }
}

/**
 * 单个贡献者卡片：顶部 16:9 大图标区，底部名称 / 角色 / 描述。
 *
 * `Card(onClick = ...)` 自带 M3 水波纹，点击反馈裁在 16dp 圆角内。
 */
@Composable
private fun ContributorCard(contributor: Contributor) {
    Card(
        onClick = { /* 预留：后续可跳转到贡献者主页 */ },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceBright,
        ),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = contributor.icon,
                    contentDescription = null,
                    modifier = Modifier.size(88.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = contributor.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = contributor.role,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    text = contributor.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
