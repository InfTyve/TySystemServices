/*
 * 移植自 Zalith Launcher 2（GPL-3.0-only）
 * 原文件：ui/screens/content/settings/layouts/Enum_Luch.kt
 *
 * 改动：
 * 1. 包名改为本项目；
 * 2. 去掉依赖 `EnumSettingUnit` 的重载，只保留受控版本（value + onRadioClick）；
 * 3. 条目描述统一用 SettingsItem，卡片走 BaseWidget_Luch，选项区复用 EnumOptionsRow；
 * 4. 修复原实现里 `return@EnumSettingsCard` 的非局部返回写法，该逻辑已随重载一并移除。
 */

package com.android.ty.systemservices.compose.ui.settings.luch

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.ty.systemservices.compose.ui.widget.BaseWidget_Luch
import com.android.ty.systemservices.compose.ui.widget.EnumOptionsRow
import com.android.ty.systemservices.compose.ui.widget.SettingsItem

/**
 * 枚举选项卡片：标题 / 摘要 + 一组单选按钮，按 [maxItemsInEachRow] 自动换行。
 *
 * 受控组件：选中项由 [value] 决定，点击回调 [onRadioClick]，不做内部持久化。
 * 选项区由 [EnumOptionsRow] 渲染，与其它卡片里的枚举行共用同一实现。
 */
@Composable
fun <E : Enum<E>> EnumSettingsCard(
    item: SettingsItem,
    value: E,
    entries: List<E>,
    modifier: Modifier = Modifier,
    getRadioText: @Composable (E) -> String,
    getRadioEnable: (E) -> Boolean = { true },
    maxItemsInEachRow: Int = Int.MAX_VALUE,
    onRadioClick: (E) -> Unit = {},
) {
    BaseWidget_Luch(
        item = item,
        modifier = modifier,
        content = {
            EnumOptionsRow(
                value = value,
                entries = entries,
                enabled = item.enabled,
                maxItemsInEachRow = maxItemsInEachRow,
                isEnabled = getRadioEnable,
                getText = getRadioText,
                onSelect = onRadioClick,
            )
        },
    )
}
