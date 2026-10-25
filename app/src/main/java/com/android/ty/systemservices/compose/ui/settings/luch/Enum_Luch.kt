/*
 * 移植自 Zalith Launcher 2（GPL-3.0-only）
 * 原文件：ui/screens/content/settings/layouts/Enum_Luch.kt
 *
 * 改动：
 * 1. 包名改为本项目；
 * 2. 去掉依赖 `EnumSettingUnit` 的便捷重载（本工程没有那套 SettingUnit 基础设施），
 *    只保留受控组件版本（value + onRadioClick），语义完全等价；
 * 3. 卡片底板统一为 BaseWidget_Luch，选项区复用 EnumOptionsRow；
 * 4. 修复原实现里 `return@EnumSettingsCard` 的非局部返回写法，该逻辑已随重载一并移除。
 */

package com.android.ty.systemservices.compose.ui.settings.luch

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.android.ty.systemservices.compose.ui.widget.BaseWidget_Luch
import com.android.ty.systemservices.compose.ui.widget.EnumOptionsRow

/**
 * 枚举选项卡片：标题 / 摘要 + 一组单选按钮，按 [maxItemsInEachRow] 自动换行。
 *
 * 受控组件：选中项由 [value] 决定，点击回调 [onRadioClick]，不做内部持久化。
 * 选项区由 [EnumOptionsRow] 渲染，与其它卡片里的枚举行共用同一实现。
 */
@Composable
fun <E : Enum<E>> EnumSettingsCard(
    value: E,
    entries: List<E>,
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    getRadioText: @Composable (E) -> String,
    getRadioEnable: (E) -> Boolean = { true },
    maxItemsInEachRow: Int = Int.MAX_VALUE,
    onRadioClick: (E) -> Unit = {},
) {
    BaseWidget_Luch(
        title = title,
        modifier = modifier,
        description = summary,
        icon = icon,
        content = {
            EnumOptionsRow(
                value = value,
                entries = entries,
                maxItemsInEachRow = maxItemsInEachRow,
                isEnabled = getRadioEnable,
                getText = getRadioText,
                onSelect = onRadioClick,
            )
        },
    )
}
