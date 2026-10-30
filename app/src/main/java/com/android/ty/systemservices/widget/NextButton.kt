package com.android.ty.systemservices.widget

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import com.android.ty.systemservices.R
import com.google.android.material.button.MaterialButton

/**
 * 全流程统一的「下一步」按钮。
 *
 * 继承 Google 的 [MaterialButton]（Material3 填充按钮），把 LauActivity 那颗按钮的规格内置进来：
 * 最小 120×48dp、文字不转大写、文字右侧 8dp 箭头、箭头颜色跟随文字颜色。
 *
 * 默认样式取自主题的 `materialButtonStyle`，所以换主题会自动跟着变；XML 里用全限定类名直接引用：
 *
 * ```xml
 * <com.android.setup.widget.NextButton
 *     android:id="@+id/lau_next"
 *     android:layout_width="wrap_content"
 *     android:layout_height="wrap_content"
 *     android:text="@string/lau_next" />
 * ```
 *
 * 新增页面只要把按钮换成这个类，位置约束和文案仍由 XML 决定。
 */
class NextButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialButtonStyle
) : MaterialButton(context, attrs, defStyleAttr) {

    init {
        setMinWidth(dp(MIN_WIDTH_DP))
        setMinHeight(dp(MIN_HEIGHT_DP))
        isAllCaps = false
        setIconResource(R.drawable.ic_arrow_right)
        iconGravity = MaterialButton.ICON_GRAVITY_TEXT_END
        iconPadding = dp(ICON_PADDING_DP)
        // 箭头跟随文字颜色，深浅色主题下都不会糊成黑块
        iconTint = textColors
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()

    private companion object {
        const val MIN_WIDTH_DP = 120
        const val MIN_HEIGHT_DP = 48
        const val ICON_PADDING_DP = 8
    }
}
