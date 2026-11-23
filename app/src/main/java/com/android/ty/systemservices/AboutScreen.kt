package com.android.ty.systemservices

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.ty.systemservices.compose.ContributorsActivity
import com.android.ty.systemservices.terminal.TermuxActivity
import com.android.ty.systemservices.terminal.TermuxLauncher
import com.android.ty.systemservices.compose.ui.theme.CornerRadius
import com.android.ty.systemservices.compose.ui.widget.InfoTipCard
import com.android.ty.systemservices.compose.ui.widget.NavigationItemWidget
import com.android.ty.systemservices.compose.ui.widget.SegmentedColumn
import com.android.ty.systemservices.compose.ui.widget.setting.ExpressiveIconCard

/** Logo 高度：字标要在这个尺寸下仍能看清，再小就退回到「图标」观感了 */
private val LogoHeight = 136.dp

/** 矢量本身的宽高比（viewport 813x856），固定高度后按它算宽度，避免被 contentScale 二次缩放 */
private const val LogoAspectRatio = 813f / 856f

private const val PROJECT_URL = "https://github.com/TYVEIT/TySystemServices"

/** 功能卡片的配色档位，两列网格里交替使用 */
private enum class CardTone {
    PRIMARY,
    SECONDARY,
    TERTIARY,
    NEUTRAL,
}

/** 功能卡片点击后去的地方；用枚举而不是闭包，顶层 val 里就不需要 Context */
private enum class AboutCardAction {
    TERMINAL,
    AOD,
    SECURITY,
    CONTRIBUTORS,
}

/**
 * 功能卡片的数据模型。
 *
 * 改 [titleRes]/[descRes]/[icon] 即可替换文案与图标；
 * 点击行为由 [action] 决定，统一在 [runCardAction] 里分发。
 */
private data class AboutCard(
    val icon: ImageVector,
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
    val tone: CardTone,
    val action: AboutCardAction,
)

/** 占位卡片：文案集中在 strings.xml 的 `about_card_*` */
private val aboutCards = listOf(
    AboutCard(
        icon = Icons.Filled.Terminal,
        titleRes = R.string.about_card_terminal_title,
        descRes = R.string.about_card_terminal_desc,
        tone = CardTone.SECONDARY,
        action = AboutCardAction.TERMINAL,
    ),
    AboutCard(
        icon = Icons.Filled.NightsStay,
        titleRes = R.string.about_card_aod_title,
        descRes = R.string.about_card_aod_desc,
        tone = CardTone.PRIMARY,
        action = AboutCardAction.AOD,
    ),
    AboutCard(
        icon = Icons.Filled.Lock,
        titleRes = R.string.about_card_security_title,
        descRes = R.string.about_card_security_desc,
        tone = CardTone.NEUTRAL,
        action = AboutCardAction.SECURITY,
    ),
    AboutCard(
        icon = Icons.Filled.Group,
        titleRes = R.string.about_card_contributors_title,
        descRes = R.string.about_card_contributors_desc,
        tone = CardTone.TERTIARY,
        action = AboutCardAction.CONTRIBUTORS,
    ),
)

/**
 * 卡片点击的分发。
 *
 * 「终端」走 TermuxActivity；桌面入口关掉时该组件是 disabled 的，
 * 直接 startActivity 会抛 ActivityNotFoundException，所以先问 [TermuxLauncher]。
 * 「安全」跳系统安全设置——应用内的 SecurityActivity 只管 doze 那几个开关。
 */
private fun runCardAction(context: Context, action: AboutCardAction) {
    val intent = when (action) {
        AboutCardAction.TERMINAL -> {
            if (!TermuxLauncher.isEnabled(context)) {
                Toast.makeText(context, R.string.termux_launcher_disabled, Toast.LENGTH_SHORT).show()
                return
            }
            Intent(context, TermuxActivity::class.java)
        }

        AboutCardAction.AOD -> Intent(context, SecurityActivity::class.java)
        AboutCardAction.SECURITY -> Intent(Settings.ACTION_SECURITY_SETTINGS)
        AboutCardAction.CONTRIBUTORS -> Intent(context, ContributorsActivity::class.java)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.link_open_failed, Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun AboutScreen(
    projectUrl: String = PROJECT_URL,
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .nestedScroll(rememberNestedScrollInteropConnection()),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        item {
            LogoHeader(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
        item {
            // 外层已经有 16dp 的 contentPadding，这里用 noPadding 让卡片和上下两张对齐，
            // 只留纵向间距
            InfoTipCard(
                text = stringResource(R.string.teyun_studio),
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                noPadding = true,
            )
        }
        item {
            SegmentedColumn(title = stringResource(id = R.string.space)) {
                item {
                    NavigationItemWidget(
                        icon = Icons.Filled.Construction,
                        title = stringResource(R.string.contributors_title),
                        description = stringResource(R.string.contributor_role_maintainer),
                        onClick = {
                            val intent = Intent(context, ContributorsActivity::class.java)
                            context.startActivity(intent)
                        },
                    )
                }
                item {
                    NavigationItemWidget(
                        icon = Icons.Filled.Code,
                        title = stringResource(R.string.tysystem_project),
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(projectUrl))
                            try {
                                context.startActivity(intent)
                            } catch (_: ActivityNotFoundException) {
                                // 没有浏览器/能处理 http 的应用，给个提示而不是静默失败
                                Toast.makeText(
                                    context,
                                    R.string.link_open_failed,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                    )
                }
            }
        }
        item {
            AboutCardGrid(
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
        }
    }
}

/**
 * 双列功能卡片网格。
 *
 * 没用 LazyVerticalGrid：它不能和同方向的 LazyColumn 嵌套（会崩），
 * 卡片数量固定，chunked 成若干 Row 更省事。
 */
@Composable
private fun AboutCardGrid(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        aboutCards.chunked(2).forEach { rowCards ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowCards.forEach { card ->
                    AboutCardItem(card = card, modifier = Modifier.weight(1f))
                }
                // 奇数个时补一个空位，避免最后一张被拉伸成整行
                if (rowCards.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** 单张功能卡片：左上角圆角图标块 + 底部标题/描述，结构对齐参考图的「工具」区 */
@Composable
private fun AboutCardItem(
    card: AboutCard,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val container: Color
    val content: Color
    when (card.tone) {
        CardTone.PRIMARY -> {
            container = colors.primaryContainer
            content = colors.onPrimaryContainer
        }

        CardTone.SECONDARY -> {
            container = colors.secondaryContainer
            content = colors.onSecondaryContainer
        }

        CardTone.TERTIARY -> {
            container = colors.tertiaryContainer
            content = colors.onTertiaryContainer
        }

        CardTone.NEUTRAL -> {
            container = colors.surfaceContainerHighest
            content = colors.onSurface
        }
    }

    val context = LocalContext.current

    // 用 Card(onClick) 而不是外层套 Modifier.clickable：
    // 后者画在 Surface 背景之下，波纹会被卡片底色盖住，点起来像没反应。
    Card(
        onClick = { runCardAction(context, card.action) },
        modifier = modifier,
        shape = RoundedCornerShape(CornerRadius),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
                .padding(16.dp),
        ) {
            // 图标块：和返回按钮、工具栏动作按钮共用同一张「卡片」，
            // 只是形状从圆形换成 14dp 圆角、底色换成卡片内的浅色层
            ExpressiveIconCard(
                size = 44.dp,
                shape = RoundedCornerShape(14.dp),
                containerColor = colors.surface.copy(alpha = 0.85f),
                contentColor = content,
            ) {
                Icon(
                    imageVector = card.icon,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(Modifier.weight(1f))

            Text(
                text = stringResource(card.titleRes),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(card.descRes),
                style = MaterialTheme.typography.bodySmall,
                color = content.copy(alpha = 0.72f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * Logo 头部卡片。
 *
 * 和下面的 [SegmentedColumn] 条目用同一套容器（surfaceBright + [CornerRadius]），
 * 因此左右边缘、圆角都能对齐；Logo 按高度定尺，宽度由矢量自身 813:856 的比例撑开。
 * 矢量本身是纯黑描边，这里统一 tint 成 onSurface，深色主题下才不会看不见。
 */
@Composable
private fun LogoHeader(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(CornerRadius))
            .background(colors.surfaceBright)
            .padding(vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_ty),
            contentDescription = stringResource(id = R.string.about),
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(colors.onSurface),
            modifier = Modifier
                .height(LogoHeight)
                .aspectRatio(LogoAspectRatio),
        )
    }
}
