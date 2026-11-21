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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import com.android.ty.systemservices.R
import com.android.ty.systemservices.compose.ui.settings.luch.EnumSettingsCard
import com.android.ty.systemservices.compose.ui.settings.luch.IntSliderSettingsCard
import com.android.ty.systemservices.compose.ui.settings.luch.ListSettingsCard
import com.android.ty.systemservices.compose.ui.settings.luch.SwitchSettingsCard
import com.android.ty.systemservices.compose.ui.settings.luch.TextInputSettingsCard
import com.android.ty.systemservices.compose.ui.widget.SegmentedColumn
import com.android.ty.systemservices.compose.ui.widget.SettingsItem
import com.android.ty.systemservices.compose.ui.widget.setting.ExpressiveBackButton

/** 演示用枚举 */
private enum class DemoMode(@StringRes val labelRes: Int) {
    Compact(R.string.test_mode_compact),
    Standard(R.string.test_mode_standard),
    Expanded(R.string.test_mode_expanded),
}

/** 演示用列表项 */
private data class DemoEntry(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
)

private val demoEntries = listOf(
    DemoEntry("a", R.string.demo_option_a_title, R.string.demo_option_a_summary),
    DemoEntry("b", R.string.demo_option_b_title, R.string.demo_option_b_summary),
    DemoEntry("c", R.string.demo_option_c_title, R.string.demo_option_c_summary),
)

// 条目描述集中声明：图标 / 标题 / 描述 / 可用状态，Screen 里不再解析资源
private val switchDemo = SettingsItem(
    titleRes = R.string.ported_switch_title,
    descriptionRes = R.string.ported_switch_desc,
    iconRes = R.drawable.ic_settings_24dp,
)

private val enumDemo = SettingsItem(
    titleRes = R.string.test_demo_enum_title,
    descriptionRes = R.string.test_demo_enum_desc,
    icon = Icons.Filled.Settings,
)

private val sliderDemo = SettingsItem(
    titleRes = R.string.test_demo_slider_title,
    descriptionRes = R.string.test_demo_slider_desc,
    icon = Icons.Filled.Build,
)

private val inputDemo = SettingsItem(
    titleRes = R.string.test_demo_input_title,
    descriptionRes = R.string.test_demo_input_desc,
    icon = Icons.Filled.Settings,
)

private val listDemo = SettingsItem(
    titleRes = R.string.test_demo_list_title,
    descriptionRes = R.string.test_demo_list_desc,
    icon = Icons.Filled.Build,
)

private val disabledDemo = SettingsItem(
    titleRes = R.string.test_demo_disabled_title,
    descriptionRes = R.string.test_demo_disabled_desc,
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
    val defaultName = LocalContext.current.getString(R.string.demo_device_name)
    var name by rememberSaveable { mutableStateOf(defaultName) }
    var mode by rememberSaveable { mutableStateOf(DemoMode.Standard) }
    var listId by rememberSaveable { mutableStateOf("b") }
    var expandedDemo by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.test_screen_title)) },
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
                SegmentedColumn(title = stringResource(R.string.test_section_all_enabled)) {
                    item {
                        SwitchSettingsCard(
                            item = switchDemo,
                            checked = wifi,
                            onCheckedChange = { wifi = it },
                        )
                    }
                    item {
                        EnumSettingsCard(
                            item = enumDemo.copy(
                                description = stringResource(
                                    R.string.demo_current_mode,
                                    stringResource(mode.labelRes),
                                ),
                            ),
                            value = mode,
                            entries = DemoMode.entries.toList(),
                            getRadioText = { stringResource(it.labelRes) },
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
                            getItemText = { stringResource(it.titleRes) },
                            getItemId = { it.id },
                            getItemSummary = { entry ->
                                Text(
                                    text = stringResource(entry.descRes),
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
