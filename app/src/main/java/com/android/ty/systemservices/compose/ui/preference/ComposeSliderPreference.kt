package com.android.ty.systemservices.compose.ui.preference

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceViewHolder
import com.android.ty.systemservices.R
import com.android.ty.systemservices.compose.ui.theme.CyanBlue
import com.android.ty.systemservices.compose.ui.theme.TyTheme
import com.android.ty.systemservices.compose.ui.widget.BaseWidget_Seek
import com.android.ty.systemservices.compose.ui.widget.CardPosition
import com.android.ty.systemservices.compose.ui.widget.LocalSegmentedItemShape
import com.android.ty.systemservices.compose.ui.widget.SettingsItem
import com.android.ty.systemservices.compose.ui.widget.toShape
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 用 Compose 渲染的滑杆 Preference（Material 3 Expressive 外观）。
 *
 * 卡片本身复用 [BaseWidget_Seek]：图标 / 标题 / 描述 / 右侧数值 + 底部滑杆，
 * 与同组其它条目同一套度量，也能通过 [LocalSegmentedItemShape] 和上下条目「粘连成一张卡」。
 *
 * 滑杆用的是 Material 3 1.5 的新版 Slider——也就是 Expressive 规格：
 * 16dp 圆角轨道、4dp × 44dp 的竖向 handle 拇指、轨道末端 stop indicator，
 * 拖动时 handle 会在轨道内缩放；刻度点颜色由 [SliderDefaults.colors] 显式给出，
 * 不再像旧版那样把 tick 全部涂成透明。
 *
 * 数值持久化沿用 Preference 框架：`android:defaultValue` + [persistFloat]。
 * 拖动过程中只更新界面 state，**松手时才写盘**，避免每帧落一次 SharedPreferences。
 *
 * 在 preference XML 中以**全限定类名**引用：
 * ```xml
 * <com.android.ty.systemservices.compose.ui.preference.ComposeSliderPreference
 *     android:key="demo_slider"
 *     android:title="滑杆示例"
 *     android:summary="拖动滑块调整数值"
 *     android:defaultValue="40"
 *     app:iconDrawable="@drawable/ic_settings_24dp"
 *     app:sliderMin="0"
 *     app:sliderMax="100"
 *     app:sliderStep="10"
 *     app:sliderValueSuffix="%" />
 * ```
 */
class ComposeSliderPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : Preference(context, attrs) {

    /** 当前值；拖动时由 Compose 侧更新，松手时落盘 */
    private var sliderValue by mutableFloatStateOf(0f)

    /** 图标 drawable 资源 ID，由 XML 属性 app:iconDrawable 指定 */
    var iconRes: Int = 0

    /** 卡片在粘连分组中的位置，由 XML 属性 app:cardPosition 指定，默认 AUTO 自动检测 */
    var cardPosition: CardPosition = CardPosition.AUTO

    /** 取值下限，对应 app:sliderMin */
    var valueFrom: Float = 0f

    /** 取值上限，对应 app:sliderMax */
    var valueTo: Float = 100f

    /** 步长，对应 app:sliderStep；0 表示连续滑动 */
    var stepSize: Float = 0f

    /** 数值后缀（例如 "%"、" dp"），对应 app:sliderValueSuffix */
    var valueSuffix: String? = null

    /** 当前值（外部只读；改值请走 [setValue] 或用户拖动） */
    val value: Float
        get() = sliderValue

    init {
        layoutResource = R.layout.preference_compose
        if (attrs != null) {
            val ta = context.obtainStyledAttributes(attrs, R.styleable.ComposeSliderPreference)
            cardPosition = CardPosition.fromValue(
                ta.getInt(R.styleable.ComposeSliderPreference_cardPosition, CardPosition.AUTO.value),
            )
            iconRes = ta.getResourceId(R.styleable.ComposeSliderPreference_iconDrawable, 0)
            valueFrom = ta.getFloat(R.styleable.ComposeSliderPreference_sliderMin, 0f)
            valueTo = ta.getFloat(R.styleable.ComposeSliderPreference_sliderMax, 100f)
            stepSize = ta.getFloat(R.styleable.ComposeSliderPreference_sliderStep, 0f)
            valueSuffix = ta.getString(R.styleable.ComposeSliderPreference_sliderValueSuffix)
            ta.recycle()
        }
        if (valueTo <= valueFrom) valueTo = valueFrom + 1f
    }

    /** 刻度数：Slider 的 steps 是「端点之间的分段数」，所以要减 1 */
    private val sliderSteps: Int
        get() = if (stepSize > 0f) {
            ((valueTo - valueFrom) / stepSize).roundToInt().minus(1).coerceAtLeast(0)
        } else {
            0
        }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any =
        a.getInt(index, valueFrom.roundToInt())

    override fun onSetInitialValue(defaultValue: Any?) {
        val fallback = (defaultValue as? Number)?.toFloat() ?: valueFrom
        sliderValue = if (isPersistent) getPersistedFloat(fallback) else fallback
    }

    /**
     * 自动检测卡片位置：根据在父 PreferenceGroup 中的索引和相邻 PreferenceCategory 边界推断。
     * 与 [ComposePreference] / [ComposeSwitchPreference] 同一套规则。
     */
    private fun detectCardPosition(): CardPosition {
        val parent = parent as? PreferenceGroup ?: return CardPosition.SINGLE
        val count = parent.preferenceCount
        val index = (0 until count).firstOrNull { parent.getPreference(it) === this }
            ?: return CardPosition.SINGLE
        val isFirst = index == 0 || parent.getPreference(index - 1) is PreferenceCategory
        val isLast = index == count - 1 || parent.getPreference(index + 1) is PreferenceCategory
        return when {
            isFirst && isLast -> CardPosition.SINGLE
            isFirst -> CardPosition.FIRST
            isLast -> CardPosition.LAST
            else -> CardPosition.MIDDLE
        }
    }

    /** 整数值不带小数点，带小数的保留一位；后面拼 XML 给的单位后缀 */
    private fun formatValue(value: Float): String {
        val rounded = value.roundToInt()
        val text = if (abs(value - rounded) < 0.001f) {
            rounded.toString()
        } else {
            String.format(Locale.getDefault(), "%.1f", value)
        }
        return valueSuffix?.let { text + it } ?: text
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val composeView = holder.findViewById(R.id.compose_view) as? ComposeView ?: return
        composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnDetachedFromWindow,
        )

        // 每次绑定都从持久化里取一次，避免 RecyclerView 复用导致显示的是别的条目的值
        sliderValue = if (isPersistent) getPersistedFloat(valueFrom) else sliderValue

        val effective = if (cardPosition == CardPosition.AUTO) detectCardPosition() else cardPosition
        val item = SettingsItem(
            title = title?.toString().orEmpty(),
            description = summary?.toString()?.takeIf { it.isNotEmpty() },
            iconRes = iconRes,
            enabled = isEnabled,
        )

        composeView.setContent {
            TyTheme {
                // 粘连卡片的垂直内边距：首卡只留顶部、中卡不留、末卡只留底部、单卡上下都留
                val verticalPadding = when (effective) {
                    CardPosition.FIRST -> Modifier.padding(top = 4.dp)
                    CardPosition.MIDDLE -> Modifier
                    CardPosition.LAST -> Modifier.padding(bottom = 4.dp)
                    CardPosition.SINGLE -> Modifier.padding(vertical = 4.dp)
                    CardPosition.AUTO -> Modifier.padding(vertical = 4.dp)
                }

                // Expressive 配色：主色轨道 + 与卡片同源的淡青底轨，
                // 刻度点跟随前景色（不再全透明），末端 stop indicator 由 framework 绘制
                val colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = CyanBlue.copy(alpha = 0.28f),
                    activeTickColor = MaterialTheme.colorScheme.onPrimary,
                    inactiveTickColor = CyanBlue,
                )

                CompositionLocalProvider(LocalSegmentedItemShape provides effective.toShape()) {
                    BaseWidget_Seek(
                        item = item,
                        progress = sliderValue,
                        onProgressChange = { sliderValue = it },
                        onProgressChangeFinished = { finalValue ->
                            sliderValue = finalValue
                            // 松手才落盘：拖动过程中每帧写 SharedPreferences 会明显掉帧
                            if (isPersistent) persistFloat(finalValue)
                            notifyChanged()
                        },
                        valueText = formatValue(sliderValue),
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .then(verticalPadding),
                        valueRange = valueFrom..valueTo,
                        steps = sliderSteps,
                        // 卡片外观与同组条目完全一致：浅青容器 + 青色图标与描述
                        containerColor = CyanBlue.copy(alpha = 0.14f),
                        accentColor = CyanBlue,
                        sliderColors = colors,
                    )
                }
            }
        }
    }
}
