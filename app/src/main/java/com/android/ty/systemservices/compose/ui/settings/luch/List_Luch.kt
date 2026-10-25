/*
 * 移植自 Zalith Launcher 2（GPL-3.0-only）
 * 原文件：ui/screens/content/settings/layouts/List_Luch.kt
 *
 * 改动：
 * 1. 包名改为本项目；
 * 2. 去掉依赖 StringSettingUnit / EnumSettingUnit / StringListSettingUnit 的重载，
 *    改为受控版本（currentId + onValueChange / selectedIds + onItemsChange）；
 * 3. 卡片底板统一为 BaseWidget_Luch：头部就是 BaseWidget 的 ListItem 排版，
 *    展开的列表进内容插槽，不再自己拼接 Row/Column 与圆角；
 * 4. 修复 `require(items.isNotEmpty())`：原实现直接在 composition 里抛异常，
 *    空列表会让整页崩溃；改为空列表时不渲染；
 * 5. 修复 `selectedItem` 可能指向旧对象的问题：原实现 remember(currentId) 持有 item
 *    实例，items 重建后仍指向旧实例；改为只记 id，每次由 items 反查；
 * 6. 修复 SimpleIDListCard 的内边距与其它列表卡不一致（原来只有 bottom = 4.dp，
 *    列表项会贴到卡片左右边缘）。
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.android.ty.systemservices.compose.ui.widget.BaseWidget_Luch

/** 列表类卡片的默认内边距：与内容插槽的 16dp 对齐后，列表项左右各留 8dp */
private val DefaultItemListPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 8.dp)

@Composable
private fun <E> BaseListSettingsCard(
    items: List<E>,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    title: String,
    itemLayout: @Composable (E) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    middleLayout: (@Composable ColumnScope.() -> Unit)? = null,
    trailingIcon: (@Composable RowScope.() -> Unit)? = null,
    enabled: Boolean = true,
    itemListPadding: PaddingValues = DefaultItemListPadding,
) {
    // 禁用时自动收起，避免"展开着但不可操作"
    LaunchedEffect(enabled) {
        if (!enabled) onExpandChange(false)
    }

    val rotation by animateFloatAsState(
        targetValue = if (expanded) -180f else 0f,
        animationSpec = getAnimateTween(),
        label = "expandIconRotation",
    )

    BaseWidget_Luch(
        title = title,
        modifier = modifier,
        description = summary,
        icon = icon,
        enabled = enabled,
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
                enabled = enabled,
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
                    items.forEach { item ->
                        itemLayout(item)
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
    items: List<E>,
    currentId: String,
    defaultId: String,
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    getItemText: @Composable (E) -> String,
    getItemId: (E) -> String,
    getItemSummary: (@Composable (E) -> Unit)? = null,
    trailingIcon: (@Composable RowScope.() -> Unit)? = null,
    enabled: Boolean = true,
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
        items = items,
        expanded = expanded,
        onExpandChange = { expanded = it },
        title = title,
        itemLayout = { item ->
            SimpleListItem(
                modifier = Modifier.fillMaxWidth(),
                selected = getItemId(selectedItem) == getItemId(item),
                itemName = getItemText(item),
                summary = getItemSummary?.let { summarySlot ->
                    { summarySlot(item) }
                },
                onClick = {
                    val id = getItemId(item)
                    if (expanded && getItemId(selectedItem) != id) {
                        selectedId = id
                        onValueChange(item)
                        if (autoCollapse) expanded = false
                    }
                },
            )
        },
        modifier = modifier,
        summary = summary,
        icon = icon,
        middleLayout = {
            Text(
                modifier = Modifier.alpha(0.7f),
                text = getItemText(selectedItem),
                style = MaterialTheme.typography.labelSmall,
            )
        },
        trailingIcon = trailingIcon,
        enabled = enabled,
        itemListPadding = itemListPadding,
    )
}

/**
 * 多选列表卡片：勾选结果由 [selectedIds] 决定，变化通过 [onItemsChange] 回调。
 *
 * @param onItemsChange 收到 (是否勾选, 条目)，返回新的 id 集合
 */
@Composable
fun <E> StringListSettingsCard(
    items: List<E>,
    selectedIds: Set<String>,
    onItemsChange: (checked: Boolean, item: E) -> Set<String>,
    getItemID: (E) -> String,
    getItemText: @Composable (E) -> String,
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    getItemSummary: (@Composable (E) -> Unit)? = null,
    getItemCheck: (contains: Boolean) -> Boolean = { it },
    trailingIcon: (@Composable RowScope.() -> Unit)? = null,
    enabled: Boolean = true,
    itemListPadding: PaddingValues = DefaultItemListPadding,
) {
    if (items.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }

    BaseListSettingsCard(
        items = items,
        expanded = expanded,
        onExpandChange = { expanded = it },
        title = title,
        itemLayout = { item ->
            val contains = getItemID(item) in selectedIds
            SimpleListItem(
                modifier = Modifier.fillMaxWidth(),
                checked = getItemCheck(contains),
                onCheckedChange = { checked ->
                    onItemsChange(checked, item)
                },
                itemName = getItemText(item),
                summary = getItemSummary?.let { summarySlot ->
                    { summarySlot(item) }
                },
                enabled = enabled,
            )
        },
        trailingIcon = trailingIcon,
        modifier = modifier,
        summary = summary,
        icon = icon,
        enabled = enabled,
        itemListPadding = itemListPadding,
    )
}

/** ID 型单选列表的便捷封装 */
@Composable
fun SimpleIDListCard(
    items: List<IDItem>,
    currentId: String,
    defaultId: String,
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    itemListPadding: PaddingValues = DefaultItemListPadding,
    onValueChange: (IDItem) -> Unit = {},
) {
    ListSettingsCard(
        modifier = modifier,
        items = items,
        currentId = currentId,
        defaultId = defaultId,
        title = title,
        summary = summary,
        icon = icon,
        getItemText = { it.title },
        getItemId = { it.id },
        enabled = enabled,
        itemListPadding = itemListPadding,
        onValueChange = onValueChange,
    )
}
