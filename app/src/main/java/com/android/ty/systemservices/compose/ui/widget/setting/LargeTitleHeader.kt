package com.android.ty.systemservices.compose.ui.widget.setting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** 大标题折叠时的表现 */
enum class LargeTitleMode {
    /** 标题连续缩小并平移到顶栏标题位置（原 TITLE_COLLAPSE_MODE_SCALE） */
    SCALE,

    /** 完全复刻原 `TITLE_COLLAPSE_MODE_FADE`：随滚动上移并按 Material 的曲线淡出 */
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

/**
 * 标题绘制盒相对行高的余量。
 *
 * `lineHeight` 只决定排版行距，不等于字形的实际占位：中文（Noto Sans CJK）
 * 的 ascent / descent 比拉丁字体高一截，在部分 ROM（如 LineageOS）或系统字体
 * 放大后，字形上下会超出这一行，被固定高度的盒子裁掉——表现就是大标题「显示不全」。
 *
 * 这里把盒子按比例放高，并改成底边对齐：文字底边位置不变，额外空间全部加在上方，
 * 因此排版位置不动，只是不再裁字形。
 */
private const val TitleBoxHeightFactor = 1.4f

/** 顶栏标题左边界：Row 起点 8 + 返回按钮 48 + 间隔 4 + 文字内边距 4 */
private val CollapsedTitleStart = BackButtonInset + BackButtonSize + 8.dp

/**
 * Material `CollapsingTextHelper.FADE_MODE_THRESHOLD_FRACTION_RELATIVE`。
 *
 * 交接点按「剩余可折叠行程的一半」取，和原版完全一致：
 * `threshold = start + (1 - start) * 0.5`。
 */
private const val FadeThresholdRelative = 0.5f

/**
 * 原版 `CollapsingTextHelper.calculateFadeModeTextAlpha()` 的逐行移植。
 *
 * 在 [start] 之前保持全亮；[start]~[threshold] 线性淡到 0；
 * 之后不再绘制大标题（顶栏那行小标题接管）。
 * 注意这里是**分段线性**而不是 smoothstep——原版用的就是
 * `AnimationUtils.lerp(value, startFraction, endFraction)`，没有缓动。
 */
private fun fadeModeAlpha(progress: Float, start: Float, threshold: Float): Float = when {
    progress <= start -> 1f
    progress <= threshold ->
        1f - (progress - start) / (threshold - start).coerceAtLeast(1e-4f)
    else -> 0f
}

/**
 * 大标题头部（纯 Compose，替代 Material 的 CollapsingToolbarLayout）。
 *
 * ## 为什么只有一个标题
 * 之前是「大标题滑上去淡出 + 顶栏标题淡入」两套坐标系各画一份，
 * 中间还夹一层背景去遮挡，观感就是「先消失、再出现」，不是滑到顶部。
 *
 * 两档共用同一个 Text，但折叠曲线各自对照原版实现：
 *
 * - **SCALE**：位置、字号连续插值，从「头部左下角」一路滑到「顶栏返回按钮右侧」，
 *   全程不透明，收尾时它自己就是顶栏标题；
 * - **FADE**：照搬 `CollapsingToolbarLayout.TITLE_COLLAPSE_MODE_FADE` 的
 *   分段参数（`fadeStart` / `fadeThreshold` 和分段线性 alpha）。标题不做额外位移，
 *   由 AppBarLayout 的滚动把它推上去，边推边按原曲线淡出，
 *   到 `fadeThreshold` 时 alpha 正好为 0，再把顶栏那行小标题淡进来。
 *   因为切换发生在完全透明的瞬间，看不出「啪」地跳位置。
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
        // 绘制盒：比行高高一截，避免 CJK 字形被裁（见 TitleBoxHeightFactor）
        val titleBoxHeight = lineHeight * TitleBoxHeightFactor

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        // 折叠行程 = 头部高度 - 顶栏高度；顶栏靠它反推回屏幕顶部
        val pinnedOffset = (maxHeight - CollapsedBarHeight) * progress

        val isFade = mode == LargeTitleMode.FADE

        // ---------------- FADE：原版 CollapsingToolbarLayout 的淡出参数 ----------------
        // 原版在 onOffsetChanged 里算这两个区间：
        //   expandRange = height - minHeight - insetTop
        //   scrimRange  = height - min(2 * minHeight + insetTop, height)
        //   start       = min(1, scrimRange / expandRange)
        //   threshold   = start + (1 - start) * 0.5
        val insetTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val expandRange = maxHeight - CollapsedBarHeight - insetTop
        val scrimTrigger = minOf(CollapsedBarHeight * 2 + insetTop, maxHeight)
        val scrimRange = maxHeight - scrimTrigger
        val fadeStart =
            if (expandRange > 0.dp) (scrimRange / expandRange).coerceIn(0f, 1f) else 0f
        val fadeThreshold = fadeStart + (1f - fadeStart) * FadeThresholdRelative

        val expandedAlpha = if (isFade) fadeModeAlpha(progress, fadeStart, fadeThreshold) else 1f
        val collapsedAlpha = if (!isFade) 0f else
            ((progress - fadeThreshold) / (1f - fadeThreshold).coerceAtLeast(1e-4f))
                .coerceIn(0f, 1f)

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
        val scale: Float
        val left: Dp
        val topInternal: Dp
        val maxTextWidth: Dp

        if (isFade) {
            // 原版 fade 档不做位置插值：标题钉在布局内的展开位置，
            // 由 AppBarLayout 的滚动把它推上去，同时按上面的曲线淡出——
            // 「滑到顶部」和「渐变消失」因此是同一个动作，不会划到一半停住。
            scale = 1f
            left = TitleInset
            topInternal = maxHeight - TitleInset - titleBoxHeight
            maxTextWidth = maxWidth - TitleInset * 2
        } else {
            // SCALE 档：位置 + 字号连续插值，终点正好落在顶栏标题那一格
            scale = 1f - 0.32f * progress
            val visualHeight = lineHeight * scale
            val startBottom = maxHeight - TitleInset
            val endBottom = CollapsedBarHeight / 2 + visualHeight / 2
            val screenBottom = lerp(startBottom, endBottom, progress)
            // 屏幕坐标 → AppBarLayout 内部坐标（以绘制盒底边为准，字形底边与盒底重合）
            topInternal = screenBottom + pinnedOffset - titleBoxHeight

            // 横坐标用 ease-out 提前完成，上升段开始时已在返回按钮右侧，不会重合
            val slide = 1f - (1f - progress) * (1f - progress)
            val endX = if (backVisible) CollapsedTitleStart else 16.dp
            left = lerp(TitleInset, endX, slide)
            // 布局宽度随进度收缩：展开态保留旧版 24dp 双边距，收起态给右侧菜单让位
            maxTextWidth = maxWidth - lerp(TitleInset * 2, endX + 56.dp, slide)
        }

        val leftPx = with(density) { left.toPx() }
        val topPx = with(density) { topInternal.toPx() }

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
                .height(titleBoxHeight),
            contentAlignment = Alignment.BottomStart,
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
