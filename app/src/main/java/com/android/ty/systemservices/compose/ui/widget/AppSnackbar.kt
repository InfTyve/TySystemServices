package com.android.ty.systemservices.compose.ui.widget

import android.util.Log
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.android.ty.systemservices.R
import kotlinx.coroutines.delay

/** 进入 / 退出动画时长（毫秒） */
private const val ENTER_MS = 260
private const val EXIT_MS = 420

/** 卡片默认底色：接近纯黑、轻微透明，和参考图一致 */
private val SnackbarContainer = Color(0xF21C1C1E)

/** Snackbar 的显示时长 */
enum class AppSnackbarDuration(val millis: Long) {
    Short(3_000L),
    Long(6_000L),
    Indefinite(-1L),
}

/**
 * Snackbar 状态持有者。
 *
 * 刻意不依赖 Material3 的 `SnackbarHostState`：后者靠 `showSnackbar` 挂起协程 +
 * 内部计时驱动，在没有 `Scaffold` 的页面里出现过「数据入队但界面不刷新」的情况。
 * 这里只用一个 Compose 状态，[show] 写入即触发重组，宿主读到就渲染。
 */
@Stable
class AppSnackbarState {

    internal data class Message(
        val text: String,
        val token: Long,
        val durationMs: Long,
    )

    internal var current by mutableStateOf<Message?>(null)
        private set

    /** 当前文案，供外部读取（无障碍播报等场景可用） */
    var message: String? by mutableStateOf(null)
        private set

    private var sequence = 0L

    /** 显示一条提示；重复调用直接替换上一条，不排队 */
    fun show(text: String, duration: AppSnackbarDuration = AppSnackbarDuration.Short) {
        sequence += 1
        current = Message(text = text, token = sequence, durationMs = duration.millis)
        message = text
        Log.d(TAG, "show(${duration.name}): $text")
    }

    /** 立即收起 */
    fun dismiss() {
        current = null
        message = null
    }

    /** 只收起指定 token，避免旧计时把新提示关掉 */
    internal fun dismiss(token: Long) {
        if (current?.token == token) dismiss()
    }

    private companion object {
        const val TAG = "AppSnackbar"
    }
}

/** 创建并记住一个 [AppSnackbarState] */
@Composable
fun rememberAppSnackbarState(): AppSnackbarState = remember { AppSnackbarState() }

/**
 * 通用 Snackbar 宿主。
 *
 * 视觉：深色圆角卡片 + 白色文字 + 右下角「确定」按钮。
 * 行为：
 *  - 首次出现从屏幕右侧滑入；
 *  - 文案变化（连点更新计数）只换文字，不重播动画；
 *  - 到时间淡出消失，也可以点「确定」提前关闭。
 *
 * 实现是独立的 `Popup` 子窗口，位置按窗口尺寸算到「底部居中」，
 * 不受宿主页面高度、裁剪、`LazyColumn` 层级影响；`focusable = false`
 * 保证不抢焦点，连点列表时不会被挡。
 */
@Composable
fun AppSnackbarHost(
    state: AppSnackbarState,
    confirmText: String? = null,
    shape: Shape = RoundedCornerShape(20.dp),
    containerColor: Color = SnackbarContainer,
    contentColor: Color = Color.White,
    bottomMargin: Dp = 24.dp,
) {
    val data = state.current
    val density = LocalDensity.current
    val bottomMarginPx = with(density) { bottomMargin.roundToPx() }

    // Popup 内容按无界约束测量，必须自己限宽，否则长文案会撑出屏幕
    val windowWidthPx = LocalWindowInfo.current.containerSize.width
    val maxCardWidth = with(density) {
        if (windowWidthPx > 0) windowWidthPx.toDp() - 32.dp else 400.dp
    }

    // 退出动画期间还要保留旧内容，否则文字会瞬间消失
    var lastData by remember { mutableStateOf<AppSnackbarState.Message?>(null) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(data?.token) {
        val current = data
        if (current != null) lastData = current
        visible = current != null

        // 到点自动收起；token 变化会重启计时，实现「替换而不是排队」
        if (current != null && current.durationMs > 0L) {
            delay(current.durationMs)
            state.dismiss(current.token)
        }
    }

    // 淡出结束后再把内容丢掉，避免 Popup 提前拆除
    LaunchedEffect(visible) {
        if (!visible) {
            delay((EXIT_MS + 80).toLong())
            if (!visible) lastData = null
        }
    }

    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (visible) {
            tween(durationMillis = ENTER_MS, easing = FastOutSlowInEasing)
        } else {
            tween(durationMillis = EXIT_MS, easing = LinearOutSlowInEasing)
        },
        label = "AppSnackbar",
    )

    val shown = data ?: lastData
    if (shown == null) return

    val positionProvider = remember(bottomMarginPx) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val x = (windowSize.width - popupContentSize.width) / 2
                val y = (windowSize.height - popupContentSize.height - bottomMarginPx)
                    .coerceAtLeast(0)
                return IntOffset(x, y)
            }
        }
    }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = { state.dismiss() },
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            excludeFromSystemGesture = true,
        ),
    ) {
        SnackbarCard(
            text = shown.text,
            shape = shape,
            containerColor = containerColor,
            contentColor = contentColor,
            maxWidth = maxCardWidth,
            // 只在首次出现时位移，文字变化时 progress 恒为 1，不会再动
            modifier = Modifier.graphicsLayer {
                translationX = (1f - progress) * size.width
                alpha = progress
            },
            confirmText = confirmText ?: stringResource(R.string.snackbar_confirm),
            onConfirm = { state.dismiss() },
        )
    }
}

@Composable
private fun SnackbarCard(
    text: String,
    shape: Shape,
    containerColor: Color,
    contentColor: Color,
    maxWidth: Dp,
    modifier: Modifier = Modifier,
    confirmText: String,
    onConfirm: () -> Unit,
) {
    Surface(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .width(maxWidth),
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = 10.dp,
    ) {
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 8.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = contentColor,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onConfirm,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
                ) {
                    Text(
                        text = confirmText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
