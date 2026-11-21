/*
 * 移植自 Zalith Launcher 2（GPL-3.0-only）
 * 原文件：ui/components 与 ui/screens/content/elements 下的若干基础组件
 *
 * 说明：这些是 SettingsCard 系列依赖的基础组件，本项目里没有对应实现，
 * 因此按原语义在本包内重建，避免跨项目依赖。
 */

package com.android.ty.systemservices.compose.ui.settings.luch

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import kotlin.math.roundToInt

/** 禁用态统一透明度，与原项目一致 */
const val DisabledAlpha = 0.38f

/** 统一的展开 / 折叠动画曲线 */
fun <T> getAnimateTween(): TweenSpec<T> =
    tween(durationMillis = 260, easing = FastOutSlowInEasing)

/**
 * 列表型卡片里的单行：单选（radio）或多选（checkbox）两种形态。
 */
@Composable
fun SimpleListItem(
    itemName: String,
    modifier: Modifier = Modifier,
    summary: (@Composable () -> Unit)? = null,
    selected: Boolean? = null,
    checked: Boolean? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) {
                onClick?.invoke()
                if (onCheckedChange != null) onCheckedChange(!(checked ?: false))
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            selected != null -> RadioButton(
                selected = selected,
                onClick = null,
                enabled = enabled,
            )

            checked != null -> Checkbox(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = itemName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            summary?.invoke()
        }
    }
}

/** 列表项数据 */
data class IDItem(
    val id: String,
    val title: String,
)

/** 统一开关样式 */
@Composable
fun DefaultSwitch(
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
    )
}

/**
 * 滑杆 + 右侧数值文本，文本可点（用于精确输入）。
 */
@Composable
fun SimpleTextSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    suffix: String? = null,
    toInt: Boolean = false,
    onValueChangeFinished: () -> Unit = {},
    onTextClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            onValueChangeFinished = onValueChangeFinished,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = { onTextClick?.invoke() },
            enabled = enabled && onTextClick != null,
        ) {
            val shown = if (toInt) {
                value.roundToInt().toString()
            } else {
                String.format("%.2f", value)
            }
            Text(
                text = shown + (suffix ?: ""),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
}

/**
 * 精确输入数值的对话框（替代拖滑杆）。
 */
@Composable
fun SliderValueEditDialog(
    onDismissRequest: () -> Unit,
    title: String,
    valueRange: ClosedFloatingPointRange<Float>,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit = {},
    intCheck: Boolean = false,
) {
    var text by remember(value) {
        mutableStateOf(
            if (intCheck) value.roundToInt().toString() else value.toString(),
        )
    }

    fun commit() {
        val parsed = text.toFloatOrNull()
        if (parsed != null) {
            onValueChange(parsed.coerceIn(valueRange.start, valueRange.endInclusive))
            onValueChangeFinished()
        }
        onDismissRequest()
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (intCheck) KeyboardType.Number else KeyboardType.Decimal,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { commit() }) {
                Text(text = stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(android.R.string.cancel))
            }
        },
    )
}

/**
 * 单行文本约束：输入里出现换行时，立刻回调去掉换行的结果。
 */
@Composable
fun SingleLineTextCheck(
    text: String,
    onSingleLined: (String) -> Unit,
) {
    LaunchedEffect(text) {
        if (text.contains('\n')) {
            onSingleLined(text.replace("\n", ""))
        }
    }
}

/** 统一样式的输入框 */
@Composable
fun OwnOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    isError: Boolean = false,
    label: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    shape: Shape = MaterialTheme.shapes.large,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = textStyle,
        isError = isError,
        label = label,
        supportingText = supportingText,
        prefix = prefix,
        suffix = suffix,
        singleLine = singleLine,
        minLines = minLines,
        shape = shape,
    )
}
