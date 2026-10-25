package com.android.ty.systemservices.compose.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.ty.systemservices.compose.ui.settings.luch.EnumSettingsCard
import com.android.ty.systemservices.compose.ui.settings.luch.IDItem
import com.android.ty.systemservices.compose.ui.settings.luch.IntSliderSettingsCard
import com.android.ty.systemservices.compose.ui.settings.luch.ListSettingsCard
import com.android.ty.systemservices.compose.ui.settings.luch.SimpleIDListCard
import com.android.ty.systemservices.compose.ui.settings.luch.StringListSettingsCard
import com.android.ty.systemservices.compose.ui.settings.luch.SwitchSettingsCard
import com.android.ty.systemservices.compose.ui.settings.luch.TextInputSettingsCard
import com.android.ty.systemservices.compose.ui.widget.setting.ExpressiveBackButton

/** 演示用枚举 */
private enum class DemoMode(val label: String) {
    Compact("紧凑"),
    Standard("标准"),
    Expanded("扩展"),
}

/** 演示用列表项 */
private data class DemoEntry(
    val id: String,
    val title: String,
    val desc: String,
)

private val demoEntries = listOf(
    DemoEntry("a", "选项 A", "第一项说明"),
    DemoEntry("b", "选项 B", "第二项说明"),
    DemoEntry("c", "选项 C", "第三项说明"),
    DemoEntry("d", "选项 D", "第四项说明"),
)

private val demoIdItems = listOf(
    IDItem("item_1", "条目一"),
    IDItem("item_2", "条目二"),
    IDItem("item_3", "条目三"),
)

/**
 * 移植组件预览页：把 `compose/ui/settings/luch/` 下的 SettingsCard 系列逐个摆出来，
 * 方便对照视觉效果与交互。仅在开发调试时使用。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestScreen(onBack: () -> Unit = {}) {
    var wifi by rememberSaveable { mutableStateOf(true) }
    var volume by rememberSaveable { mutableIntStateOf(40) }
    var deviceName by rememberSaveable { mutableStateOf("TySystemServices") }
    var mode by rememberSaveable { mutableStateOf(DemoMode.Standard) }
    var listId by rememberSaveable { mutableStateOf("b") }
    var idListItem by rememberSaveable { mutableStateOf("item_2") }
    // Set 不是 Saveable，这里只做会话内状态
    var multiSelected by remember { mutableStateOf(setOf("a")) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                title = { Text(text = "TestScreen") },
                navigationIcon = { ExpressiveBackButton(onClick = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---------- 基础三件套：开关 / 滑杆 / 输入框 ----------
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("基础卡片（Top / Middle / Bottom 粘连）")
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        SwitchSettingsCard(
                            checked = wifi,
                            onCheckedChange = { wifi = it },
                            title = "无线网络",
                            summary = "整卡可点，右侧开关联动",
                        )
                        IntSliderSettingsCard(
                            value = volume,
                            title = "音量",
                            summary = "点击右侧数值可精确输入",
                            valueRange = 0f..100f,
                            steps = 9,
                            suffix = "%",
                            onValueChange = { volume = it },
                        )
                        TextInputSettingsCard(
                            value = deviceName,
                            title = "设备名称",
                            summary = "单行输入，回车不会换行",
                            onValueChange = { deviceName = it },
                        )
                    }
                }
            }

            // ---------- 枚举单选 ----------
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("枚举单选（EnumSettingsCard）")
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        EnumSettingsCard(
                            value = mode,
                            entries = DemoMode.entries.toList(),
                            title = "布局密度",
                            summary = "当前：${mode.label}",
                            getRadioText = { it.label },
                            onRadioClick = { mode = it },
                            maxItemsInEachRow = 3,
                        )
                    }
                }
            }

            // ---------- 列表单选 ----------
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("列表单选（ListSettingsCard）")
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        ListSettingsCard(
                            items = demoEntries,
                            currentId = listId,
                            defaultId = "a",
                            title = "选择一项",
                            summary = "展开后点击即选中并自动收起",
                            getItemText = { it.title },
                            getItemId = { it.id },
                            getItemSummary = { entry ->
                                Text(
                                    text = entry.desc,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            onValueChange = { listId = it.id },
                        )
                    }
                }
            }

            // ---------- 列表多选 ----------
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("列表多选（StringListSettingsCard）")
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        StringListSettingsCard(
                            items = demoEntries,
                            selectedIds = multiSelected,
                            onItemsChange = { checked, item ->
                                if (checked) multiSelected + item.id else multiSelected - item.id
                            },
                            getItemID = { it.id },
                            getItemText = { it.title },
                            title = "多选示例",
                            summary = "已选 ${multiSelected.size} 项",
                        )
                    }
                }
            }

            // ---------- ID 列表 ----------
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("ID 列表（SimpleIDListCard）")
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        SimpleIDListCard(
                            items = demoIdItems,
                            currentId = idListItem,
                            defaultId = "item_1",
                            title = "ID 单选",
                            summary = "使用 IDItem(id, title) 数据",
                            onValueChange = { idListItem = it.id },
                        )
                    }
                }
            }

            // ---------- 禁用态 ----------
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("禁用态（DisabledAlpha）")
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        SwitchSettingsCard(
                            checked = false,
                            onCheckedChange = {},
                            title = "不可用的开关",
                            summary = "enabled = false",
                            enabled = false,
                        )
                        IntSliderSettingsCard(
                            value = 30,
                            title = "不可用的滑杆",
                            valueRange = 0f..100f,
                            enabled = false,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp),
    )
}
