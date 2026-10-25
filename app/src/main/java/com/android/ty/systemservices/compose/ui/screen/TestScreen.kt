package com.android.ty.systemservices.compose.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Settings
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
import com.android.ty.systemservices.R
import com.android.ty.systemservices.compose.ui.settings.luch.EnumSettingsCard
import com.android.ty.systemservices.compose.ui.settings.luch.IDItem
import com.android.ty.systemservices.compose.ui.settings.luch.IntSliderSettingsCard
import com.android.ty.systemservices.compose.ui.settings.luch.ListSettingsCard
import com.android.ty.systemservices.compose.ui.settings.luch.SwitchSettingsCard
import com.android.ty.systemservices.compose.ui.settings.luch.TextInputSettingsCard
import com.android.ty.systemservices.compose.ui.widget.SegmentedColumn
import com.android.ty.systemservices.compose.ui.widget.SettingsItem
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
)

private val demoIdItems = listOf(
    IDItem("item_1", "条目一"),
    IDItem("item_2", "条目二"),
)

// 条目描述集中声明：图标 / 标题 / 描述 / 可用状态，Screen 里不再解析资源
private val switchDemo = SettingsItem(
    titleRes = R.string.ported_switch_title,
    descriptionRes = R.string.ported_switch_desc,
    iconRes = R.drawable.ic_settings_24dp,
)

private val enumDemo = SettingsItem(
    title = "枚举单选",
    description = "FlowRow 自动换行",
    icon = Icons.Filled.Settings,
)

private val sliderDemo = SettingsItem(
    title = "整数滑杆",
    description = "点右侧数值可精确输入",
    icon = Icons.Filled.Build,
)

private val inputDemo = SettingsItem(
    title = "文本输入",
    description = "单行约束",
    icon = Icons.Filled.Settings,
)

private val listDemo = SettingsItem(
    title = "列表单选",
    description = "展开后点选并收起",
    icon = Icons.Filled.Build,
)

private val disabledDemo = SettingsItem(
    title = "禁用态",
    description = "enabled = false",
    icon = Icons.Filled.Build,
    enabled = false,
)

/**
 * 移植组件预览页：所有条目只用 [SettingsItem] 声明外观，卡片本体负责排版。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestScreen(onBack: () -> Unit = {}) {
    var wifi by rememberSaveable { mutableStateOf(true) }
    var volume by rememberSaveable { mutableIntStateOf(40) }
    var name by rememberSaveable { mutableStateOf("TySystemServices") }
    var mode by rememberSaveable { mutableStateOf(DemoMode.Standard) }
    var listId by rememberSaveable { mutableStateOf("b") }
    var expandedDemo by remember { mutableStateOf(false) }

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
            item {
                SegmentedColumn(title = "全部启用") {
                    item {
                        SwitchSettingsCard(
                            item = switchDemo,
                            checked = wifi,
                            onCheckedChange = { wifi = it },
                        )
                    }
                    item {
                        EnumSettingsCard(
                            item = enumDemo.copy(description = "当前：${mode.label}"),
                            value = mode,
                            entries = DemoMode.entries.toList(),
                            getRadioText = { it.label },
                            maxItemsInEachRow = 3,
                            onRadioClick = { mode = it },
                        )
                    }
                    item {
                        IntSliderSettingsCard(
                            item = sliderDemo,
                            value = volume,
                            valueRange = 0f..100f,
                            steps = 9,
                            suffix = "%",
                            onValueChange = { volume = it },
                        )
                    }
                    item {
                        TextInputSettingsCard(
                            item = inputDemo,
                            value = name,
                            onValueChange = { name = it },
                        )
                    }
                    item {
                        ListSettingsCard(
                            item = listDemo,
                            items = demoEntries,
                            currentId = listId,
                            defaultId = "a",
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
                    item {
                        SwitchSettingsCard(
                            item = disabledDemo,
                            checked = false,
                            onCheckedChange = { expandedDemo = it },
                        )
                    }
                }
            }
        }
    }
}
