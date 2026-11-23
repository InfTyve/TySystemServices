package com.android.ty.systemservices.compose.ui.widget.setting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** 大标题折叠时的表现 */
enum class LargeTitleMode {
    /** 标题连续缩小并平移到顶栏标题位置（原 TITLE_COLLAPSE_MODE_SCALE） */
    SCALE,

    /** 位置同样连续，但缩小集中在前半程之后，整体更「稳」一点（原 TITLE_COLLAPSE_MODE_FADE） */
    FADE,
}

/** 折叠后钉在顶部的高度，和 Material Toolbar 一致 */
private val CollapsedBarHeight = 56.dp

/** 返回按钮直径：沿用旧布局 `back_button_container` 的 48dp */
private val BackButtonSize = 48.dp

/** 返回按钮左边距：旧版是 4dp 容器外边距 + 48dp 容器内 40dp 居中的 4dp，合计 8dp */
private val BackButtonInset = 8.dp

/** 展开态标题的左/下内边距，沿用原来 CollapsingToolbarLayout 的 24dp */
private val TitleInset = 24.dp

/** 展开态标题字号，对齐原 CollapsingToolbarSampleLargeTitle 的 32sp bold */
private val BigTitleSize = 32.sp

/** 行高倍数，和折叠态顶栏标题（22sp）在终点处的观感对齐 */
private const val LineHeightFactor = 1.18f

/** 顶栏标题左边界：Row 起点 8 + 返回按钮 48 + 间隔 4 + 文字内边距 4 */
private val CollapsedTitleStart = BackButtonInset + BackButtonSize + 8.dp

/** 0..1 的 Hermite 平滑过渡，用于淡入淡出，避免线性透明度变化的生硬感 */
private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * 大标题头部（纯 Compose，替代 Material 的 CollapsingToolbarLayout）。
 *
 * ## 为什么只有一个标题
 * 之前是「大标题滑上去淡出 + 顶栏标题淡入」两套坐标系各画一份，
 * 中间还夹一层背景去遮挡，观感就是「先消失、再出现」，不是滑到顶部。
 *
 * 现在整段折叠只绘制**一个** Text：位置、字号都由 [fraction] 连续插值，
 * 从「头部左下角」一路滑到「顶栏返回按钮右侧」，不会滑到一半停住。
 * SCALE 档收尾时它自己就是顶栏标题（全程不透明）；
 * FADE 档保持原语义——边滑边渐隐，最后交给顶栏那行小标题，
 * 两段是叠着过渡的，不会像之前那样「先整个消失、再冒出另一个」。
 *
 * ## 坐标说明
 * Compose 画在 AppBarLayout 内部，而 AppBarLayout 折叠时整块上移 [pinnedOffset]。
 * 所以「屏幕上的目标位置」要加上 [pinnedOffset] 才是内部坐标。
 * 顶栏因此也得反向 offset 回去，才能看起来钉在屏幕顶部。
 *
 * ## 层级
 * 标题最后绘制（在顶栏之上），不会被顶栏背景切掉；
 * 又因为横坐标的插值提前完成（[progress] 的 ease-out），
 * 上升段开始时标题已经挪到返回按钮右侧，不会压到返回键上。
 *
 * @param fraction 折叠进度 0（展开）~ 1（收起），由 Activity 从 AppBarLayout 的 offset 换算
 * @param backVisible 是否显示返回按钮（顶层页隐藏，二级页显示）
 * @param actions `showAsAction` 的动作项 + 溢出项，直接交给 [ExpressiveToolbarMenu] 绘制
 */
@Composable
fun LargeTitleHeader(
    title: String,
    fraction: Float,
    mode: LargeTitleMode,
    backVisible: Boolean,
    actions: List<ExpressiveMenuItem>,
    onActionClick: (ExpressiveMenuItem) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val progress = fraction.coerceIn(0f, 1f)
    val density = LocalDensity.current

    val titleStyle: TextStyle = MaterialTheme.typography.headlineLarge.copy(
        fontSize = BigTitleSize,
        lineHeight = BigTitleSize * LineHeightFactor,
        fontWeight = FontWeight.Bold,
    )
    val lineHeight = remember(density) {
        with(density) { (BigTitleSize * LineHeightFactor).toDp() }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        // 折叠行程 = 头部高度 - 顶栏高度；顶栏靠它反推回屏幕顶部
        val pinnedOffset = (maxHeight - CollapsedBarHeight) * progress

        val isFade = mode == LargeTitleMode.FADE
        // FADE 档：大标题边滑边淡出（「渐变消失」），顶栏标题延后一点再淡入，避免重影
        val expandedAlpha =
            if (isFade) 1f - smoothstep(0.45f, 1f, progress) else 1f
        val collapsedAlpha =
            if (isFade) smoothstep(0.52f, 1f, progress) else 0f

        // ---------------- 折叠态顶栏（背景 + 返回按钮 + 标题 + 菜单） ----------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(CollapsedBarHeight)
                .offset { IntOffset(0, pinnedOffset.roundToPx()) }
                .background(colors.background)
                .padding(start = BackButtonInset, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (backVisible) {
                ExpressiveBackButton(
                    onClick = onBack,
                    modifier = Modifier.size(BackButtonSize),
                )
            }
            // SCALE 档的大标题自己就会缩到这一格，这里透明；FADE 档由它接力
            Text(
                text = title,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
                    .graphicsLayer { alpha = collapsedAlpha },
            )

            ExpressiveToolbarMenu(
                items = actions,
                onItemClick = onActionClick,
            )
        }

        // ---------------- 唯一的大标题 ----------------
        // 两档位置插值完全一致，区别只在终点大小和是否淡出：
        // SCALE 线性缩到 0.68（≈22sp，正好等于顶栏标题）并保持不透明；
        // FADE 缩到 0.78 的同时渐隐到 0，收尾交给顶栏那行小标题。
        val endScale = if (isFade) 0.78f else 0.68f
        val scale = 1f - (1f - endScale) * progress

        // 视觉高度（缩放后的实际占位高度），用于把「视觉中心」对齐到顶栏中线
        val visualHeight = lineHeight * scale

        // 纵坐标插值：底边从「离头部底 24dp」滑到「顶栏垂直居中」
        val startBottom = maxHeight - TitleInset
        val endBottom = CollapsedBarHeight / 2 + visualHeight / 2
        val screenBottom = lerp(startBottom, endBottom, progress)
        // 屏幕坐标 → AppBarLayout 内部坐标
        val topInternal = screenBottom + pinnedOffset - lineHeight

        // 横坐标插值：用 ease-out 提前完成，上升段开始时已经在返回按钮右侧，不会重合
        val slide = 1f - (1f - progress) * (1f - progress)
        val endX = if (backVisible) CollapsedTitleStart else 16.dp
        val left = lerp(TitleInset, endX, slide)

        val leftPx = with(density) { left.toPx() }
        val topPx = with(density) { topInternal.toPx() }
        // 布局宽度随进度收缩：展开态保留旧版的 24dp 双边距，收起态给右侧菜单让位
        val maxTextWidth = maxWidth - lerp(TitleInset * 2, endX + 56.dp, slide)

        Box(
            modifier = Modifier
                .offset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
                .graphicsLayer {
                    alpha = expandedAlpha
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 1f)
                }
                .widthIn(max = if (maxTextWidth > 0.dp) maxTextWidth else 0.dp)
                .height(lineHeight),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = title,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = titleStyle,
            )
        }
    }
}
