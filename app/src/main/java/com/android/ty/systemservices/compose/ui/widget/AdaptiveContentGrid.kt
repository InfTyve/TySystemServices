// 页面级自适应容器：一个「按宽度自动决定列数」的瀑布网格。
//
// 所有 Compose 页面（设置主页、Preference、贡献者、开发调试、实验室、测试页）都用它，
// 不再各自 new 一个 LazyColumn。竖屏手机只有一列，行为和原来的 LazyColumn 完全一致；
// 横屏 / 平板 / 折叠屏展开时自动变成多列，条目顺序由框架按列填充，不需要页面自己判断方向。
package com.android.ty.systemservices.compose.ui.widget

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridItemScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * 一列宽度下限。小于它就只能放一列，所以竖屏手机（360~430dp）永远是单列。
 *
 * 这个值决定「多少宽度切换成两列」：两列需要 `2 * minColumnWidth + columnSpacing`，
 * 即 356dp × 2 + 16dp ≈ 728dp，正好是主流横屏手机的可用宽度。
 */
private val DefaultMinColumnWidth = 340.dp

/**
 * 条目位置动画的曲线。
 *
 * 用 `StiffnessMediumLow` 而不是默认的 Medium：条目是被「上面那张卡片变高」推下去的，
 * 属于被动位移，弹簧太硬会显得跟手过头；偏低一点会有轻微的跟随感，
 * 视觉上和 `AnimatedVisibility` 的展开动画是同一档节奏。
 */
private val ItemPlacementSpec: FiniteAnimationSpec<IntOffset> = spring(
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = IntOffset.VisibilityThreshold,
)

/**
 * 自适应内容网格。
 *
 * 与 [androidx.compose.foundation.lazy.LazyColumn] 的用法几乎一样：`item { }` / `items(...)`
 * 原样保留，只是把列表换成「列数由宽度决定」的瀑布网格。
 *
 * 为什么用**瀑布网格**而不是普通网格：
 * 页面里每个 section 的高度差很多（一段开关组 vs 一张提示卡），普通网格会按行对齐，
 * 矮的那格下方留一大片空白；瀑布网格按列各自堆叠，观感接近两侧独立的列表。
 *
 * 条目顺序交给框架：按 [StaggeredGridCells.Adaptive] 算出的列数依次填充，
 * 页面不需要关心「哪一条放左边」。窄屏单列时顺序与原来完全一致。
 *
 * ## 高度变化的动画
 *
 * 可展开卡片展开时自身高度是逐帧变化的，但 Lazy 布局默认会让后面的条目**立刻跳到**新位置。
 * 这里给每个条目自动套一层 [LazyStaggeredGridItemScope.animateItem]，
 * 「被推走的条目」于是带弹簧曲线平移，而不是瞬移。
 *
 * 只启用 placement 动画（`fadeInSpec` / `fadeOutSpec` 传 null）：淡入淡出会让滚动时
 * 新进入视口的条目也渐显，观感反而闪烁；这里只需要位置过渡。
 * 页面里的 `item {}` 完全不用改，包装是在容器内部完成的。
 *
 * @param minColumnWidth 单列最小宽度，决定断点；默认 [DefaultMinColumnWidth]
 * @param horizontalPadding 左右内容内边距（横屏也是整块内容的外边距，不是每列各自一份）
 * @param verticalPadding 首尾内边距
 * @param verticalSpacing 条目间距；原来是 `verticalArrangement = Arrangement.spacedBy(x)` 的页面传这里
 * @param columnSpacing 列间距
 * @param animateItemPlacement 条目位置变化时是否播放过渡动画，默认开启；
 *   需要严格对齐的页面（例如自己接管了位移动画）可以关掉
 */
@Composable
fun AdaptiveContentGrid(
    modifier: Modifier = Modifier,
    minColumnWidth: Dp = DefaultMinColumnWidth,
    horizontalPadding: Dp = 16.dp,
    verticalPadding: Dp = 8.dp,
    verticalSpacing: Dp = 0.dp,
    columnSpacing: Dp = 16.dp,
    animateItemPlacement: Boolean = true,
    content: AdaptiveGridScope.() -> Unit,
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(minColumnWidth),
        modifier = modifier,
        contentPadding = PaddingValues(
            start = horizontalPadding,
            end = horizontalPadding,
            top = verticalPadding,
            bottom = verticalPadding,
        ),
        verticalItemSpacing = verticalSpacing,
        horizontalArrangement = Arrangement.spacedBy(columnSpacing),
    ) {
        AdaptiveGridScope(this, animateItemPlacement).content()
    }
}

/**
 * 与 [LazyStaggeredGridScope] 同形的 DSL 作用域，唯一区别是每个条目会多包一层位移动画。
 *
 * 为什么不用继承：`LazyStaggeredGridScope` 是 `sealed interface`，只允许在
 * foundation 自己的 module 里实现，应用层无法 extend。所以这里做成静态代理，
 * 由 [AdaptiveContentGrid] 在内部把真实的 scope 交给它。
 *
 * 对页面来说写法和原来一模一样：
 * ```kotlin
 * AdaptiveContentGrid { item { ... }; items(list) { ... } }
 * ```
 * `item` / `items` 解析到这里的成员函数（成员优先于同名的扩展函数），
 * 所以页面不需要改任何一行。
 */
class AdaptiveGridScope internal constructor(
    private val delegate: LazyStaggeredGridScope,
    private val animatePlacement: Boolean,
) {

    /** 单条内容；参数含义与 [LazyStaggeredGridScope.item] 完全一致 */
    fun item(
        key: Any? = null,
        contentType: Any? = null,
        span: StaggeredGridItemSpan? = null,
        content: @Composable LazyStaggeredGridItemScope.() -> Unit,
    ) {
        delegate.item(key, contentType, span) {
            AnimatedEntry(animatePlacement, content)
        }
    }

    /** 按数量批量添加；参数含义与 [LazyStaggeredGridScope.items] 完全一致 */
    fun items(
        count: Int,
        key: ((index: Int) -> Any)? = null,
        contentType: (index: Int) -> Any? = { null },
        span: ((index: Int) -> StaggeredGridItemSpan)? = null,
        itemContent: @Composable LazyStaggeredGridItemScope.(index: Int) -> Unit,
    ) {
        delegate.items(count, key, contentType, span) { index ->
            AnimatedEntry(animatePlacement) { itemContent(index) }
        }
    }

    /** 按列表批量添加；对应 foundation 里 `items(items: List<T>)` 那个扩展 */
    fun <T> items(
        items: List<T>,
        key: ((item: T) -> Any)? = null,
        contentType: (item: T) -> Any? = { null },
        span: ((item: T) -> StaggeredGridItemSpan)? = null,
        itemContent: @Composable LazyStaggeredGridItemScope.(item: T) -> Unit,
    ) {
        items(
            count = items.size,
            key = key?.let { { index: Int -> it(items[index]) } },
            contentType = { index -> contentType(items[index]) },
            span = span?.let { { index: Int -> it(items[index]) } },
        ) { index ->
            itemContent(items[index])
        }
    }
}

/**
 * 条目根节点。
 *
 * `animateItem` 的修饰符必须落在 item 的**根**上，所以这里统一包一层 `Box`；
 * 宽高约束原样透传，不影响原有布局。关闭动画时直接组合原内容，不引入额外节点。
 */
@Composable
private fun LazyStaggeredGridItemScope.AnimatedEntry(
    animatePlacement: Boolean,
    content: @Composable LazyStaggeredGridItemScope.() -> Unit,
) {
    if (!animatePlacement) {
        content()
        return
    }

    val itemScope = this
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .animateItem(
                fadeInSpec = null,
                placementSpec = ItemPlacementSpec,
                fadeOutSpec = null,
            ),
    ) {
        with(itemScope) { content() }
    }
}
