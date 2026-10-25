/*
 * 移植自 Zalith Launcher 2（GPL-3.0-only）
 * 原文件：ui/screens/content/settings/layouts/List_Luch.kt
 *
 * 改动：
 * 1. 包名改为本项目；
 * 2. 去掉依赖 StringSettingUnit / EnumSettingUnit / StringListSettingUnit 的重载，
 *    改为受控版本（currentId + onValueChange / selectedIds + onItemsChange）；
 * 3. 条目描述统一用 SettingsItem，卡片走 BaseWidget_Luch，展开列表进内容插槽；
 * 4. 修复 `require(items.isNotEmpty())`：原实现直接在 composition 里抛异常，
 *    空列表会让整页崩溃；改为空列表时不渲染；
 * 5. 修复 `selectedItem` 可能指向旧对象的问题：改为只记 id，每次由 items 反查；
 * 6. 修复 SimpleIDListCard 的内边距与其它列表卡不一致的问题。
 */

package com.android.ty.systemservices.compose.ui.settings.luch

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import com.android.ty.systemservices.compose.ui.widget.BaseWidget_Luch
import com.android.ty.systemservices.compose.ui.widget.SettingsItem

/** 列表类卡片的默认内边距：与内容插槽的 16dp 对齐后，列表项左右各留 8dp */
private val DefaultItemListPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 8.dp)

@Composable
private fun <E> BaseListSettingsCard(
    item: SettingsItem,
    items: List<E>,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    itemLayout: @Composable (E) -> Unit,
    modifier: Modifier = Modifier,
    middleLayout: (@Composable ColumnScope.() -> Unit)? = null,
    trailingIcon: (@Composable RowScope.() -> Unit)? = null,
    itemListPadding: PaddingValues = DefaultItemListPadding,
) {
    // 禁用时自动收起，避免"展开着但不可操作"
    LaunchedEffect(item.enabled) {
        if (!item.enabled) onExpandChange(false)
    }

    val rotation by animateFloatAsState(
        targetValue = if (expanded) -180f else 0f,
        animationSpec = getAnimateTween(),
        label = "expandIconRotation",
    )

    BaseWidget_Luch(
        item = item,
        modifier = modifier,
        onClick = { onExpandChange(!expanded) },
        trailingContent = {
            trailingIcon?.let { trailing ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    trailing()
                }
            }
            IconButton(
                modifier = Modifier
                    .size(34.dp)
                    .rotate(rotation),
                enabled = item.enabled,
                onClick = { onExpandChange(!expanded) },
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                )
            }
        },
        content = {
            middleLayout?.invoke(this)
            AnimatedVisibility(
                modifier = Modifier.fillMaxWidth(),
                visible = expanded,
                enter = expandVertically(animationSpec = getAnimateTween()),
                exit = shrinkVertically(animationSpec = getAnimateTween()) + fadeOut(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(itemListPadding),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items.forEach { entry ->
                        itemLayout(entry)
                    }
                }
            }
        },
    )
}

/**
 * 单选列表卡片：展开后选择一项，自动收起。
 *
 * @param currentId 当前选中的 id，用于显示"已选"状态
 * @param defaultId 当前 id 找不到时回退的 id
 */
@Composable
fun <E> ListSettingsCard(
    item: SettingsItem,
    items: List<E>,
    currentId: String,
    defaultId: String,
    modifier: Modifier = Modifier,
    getItemText: @Composable (E) -> String,
    getItemId: (E) -> String,
    getItemSummary: (@Composable (E) -> Unit)? = null,
    trailingIcon: (@Composable RowScope.() -> Unit)? = null,
    autoCollapse: Boolean = true,
    itemListPadding: PaddingValues = DefaultItemListPadding,
    onValueChange: (E) -> Unit = {},
) {
    // 空列表直接不渲染：原实现在 composition 里 require() 会直接崩溃
    if (items.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }
    // 只记 id，item 每次由 items 反查，避免 items 重建后仍指向旧实例
    var selectedId by remember(currentId) {
        mutableStateOf(currentId.ifEmpty { defaultId })
    }
    val selectedItem = items.firstOrNull { getItemId(it) == selectedId }
        ?: items.firstOrNull { getItemId(it) == defaultId }
        ?: items.first()

    BaseListSettingsCard(
        item = item,
        items = items,
        expanded = expanded,
        onExpandChange = { expanded = it },
        itemLayout = { entry ->
            SimpleListItem(
                modifier = Modifier.fillMaxWidth(),
                selected = getItemId(selectedItem) == getItemId(entry),
                itemName = getItemText(entry),
                summary = getItemSummary?.let { summarySlot ->
                    { summarySlot(entry) }
                },
                onClick = {
                    val id = getItemId(entry)
                    if (expanded && getItemId(selectedItem) != id) {
                        selectedId = id
                        onValueChange(entry)
                        if (autoCollapse) expanded = false
                    }
                },
            )
        },
        modifier = modifier,
        middleLayout = {
            Text(
                modifier = Modifier.alpha(0.7f),
                text = getItemText(selectedItem),
                style = MaterialTheme.typography.labelSmall,
            )
        },
        trailingIcon = trailingIcon,
        itemListPadding = itemListPadding,
    )
}

/**
 * 多选列表卡片：勾选状态由 [selectedIds] 决定，变化通过 [onSelectionChange] 回传新的集合。
 *
 * 注意：这里回传的是**计算好的新集合**，调用方直接 `selectedIds = it` 即可。
 * 早期版本用的是 `(checked, item) -> Set`，调用方若只计算不赋值（或组件丢弃返回值），
 * 就会出现"点了没反应"的问题，因此改成这种不易误用的形式。
 */
@Composable
fun <E> StringListSettingsCard(
    item: SettingsItem,
    items: List<E>,
    selectedIds: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    getItemID: (E) -> String,
    getItemText: @Composable (E) -> String,
    modifier: Modifier = Modifier,
    getItemSummary: (@Composable (E) -> Unit)? = null,
    getItemCheck: (contains: Boolean) -> Boolean = { it },
    trailingIcon: (@Composable RowScope.() -> Unit)? = null,
    itemListPadding: PaddingValues = DefaultItemListPadding,
) {
    if (items.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }

    BaseListSettingsCard(
        item = item,
        items = items,
        expanded = expanded,
        onExpandChange = { expanded = it },
        itemLayout = { entry ->
            val id = getItemID(entry)
            val contains = id in selectedIds
            SimpleListItem(
                modifier = Modifier.fillMaxWidth(),
                checked = getItemCheck(contains),
                onCheckedChange = {
                    // 以当前集合为准做增删，回调新集合；不依赖调用方返回值
                    onSelectionChange(
                        if (contains) selectedIds - id else selectedIds + id,
                    )
                },
                itemName = getItemText(entry),
                summary = getItemSummary?.let { summarySlot ->
                    { summarySlot(entry) }
                },
                enabled = item.enabled,
            )
        },
        trailingIcon = trailingIcon,
        modifier = modifier,
        itemListPadding = itemListPadding,
    )
}

/** ID 型单选列表的便捷封装 */
@Composable
fun SimpleIDListCard(
    item: SettingsItem,
    items: List<IDItem>,
    currentId: String,
    defaultId: String,
    modifier: Modifier = Modifier,
    itemListPadding: PaddingValues = DefaultItemListPadding,
    onValueChange: (IDItem) -> Unit = {},
) {
    ListSettingsCard(
        item = item,
        modifier = modifier,
        items = items,
        currentId = currentId,
        defaultId = defaultId,
        getItemText = { it.title },
        getItemId = { it.id },
        itemListPadding = itemListPadding,
        onValueChange = onValueChange,
    )
}
