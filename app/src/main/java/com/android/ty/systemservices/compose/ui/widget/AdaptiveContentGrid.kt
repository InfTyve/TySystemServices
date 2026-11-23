// 页面级自适应容器：一个「按宽度自动决定列数」的瀑布网格。
//
// 所有 Compose 页面（设置主页、Preference、贡献者、开发调试、实验室、测试页）都用它，
// 不再各自 new 一个 LazyColumn。竖屏手机只有一列，行为和原来的 LazyColumn 完全一致；
// 横屏 / 平板 / 折叠屏展开时自动变成多列，条目顺序由框架按列填充，不需要页面自己判断方向。
package com.android.ty.systemservices.compose.ui.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 一列宽度下限。小于它就只能放一列，所以竖屏手机（360~430dp）永远是单列。
 *
 * 这个值决定「多少宽度切换成两列」：两列需要 `2 * minColumnWidth + columnSpacing`，
 * 即 356dp × 2 + 16dp ≈ 728dp，正好是主流横屏手机的可用宽度。
 */
private val DefaultMinColumnWidth = 340.dp

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
 * @param minColumnWidth 单列最小宽度，决定断点；默认 [DefaultMinColumnWidth]
 * @param horizontalPadding 左右内容内边距（横屏也是整块内容的外边距，不是每列各自一份）
 * @param verticalPadding 首尾内边距
 * @param verticalSpacing 条目间距；原来是 `verticalArrangement = Arrangement.spacedBy(x)` 的页面传这里
 * @param columnSpacing 列间距
 */
@Composable
fun AdaptiveContentGrid(
    modifier: Modifier = Modifier,
    minColumnWidth: Dp = DefaultMinColumnWidth,
    horizontalPadding: Dp = 16.dp,
    verticalPadding: Dp = 8.dp,
    verticalSpacing: Dp = 0.dp,
    columnSpacing: Dp = 16.dp,
    content: LazyStaggeredGridScope.() -> Unit,
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
        content = content,
    )
}
