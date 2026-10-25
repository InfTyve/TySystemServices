package com.android.ty.systemservices.compose.ui.screen

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import com.android.ty.systemservices.compose.ui.preference.XmlPreferenceActivity
import com.android.ty.systemservices.compose.ui.widget.setting.BigTitleImpl
import com.android.ty.systemservices.compose.ui.widget.setting.DropDownMenuWidget
import com.android.ty.systemservices.compose.ui.widget.setting.MainTitleImpl
import com.android.ty.systemservices.compose.ui.widget.setting.loadBigTitleImpl
import com.android.ty.systemservices.compose.ui.widget.setting.loadMainTitleImpl
import com.android.ty.systemservices.compose.ui.widget.setting.saveBigTitleImpl
import com.android.ty.systemservices.compose.ui.widget.setting.saveMainTitleImpl
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.ty.systemservices.compose.LabActivity
import com.android.ty.systemservices.compose.TestActivity
import com.android.ty.systemservices.R
import com.android.ty.systemservices.compose.ui.animation.InteractiveHighlight
import com.android.ty.systemservices.compose.ui.theme.CyanBlue
import com.android.ty.systemservices.compose.ui.widget.BaseWidget
import com.android.ty.systemservices.compose.ui.settings.luch.EnumSettingsCard
import com.android.ty.systemservices.compose.ui.settings.luch.IDItem
import com.android.ty.systemservices.compose.ui.settings.luch.IntSliderSettingsCard
import com.android.ty.systemservices.compose.ui.settings.luch.ListSettingsCard
import com.android.ty.systemservices.compose.ui.settings.luch.SimpleIDListCard
import com.android.ty.systemservices.compose.ui.settings.luch.StringListSettingsCard
import com.android.ty.systemservices.compose.ui.settings.luch.SwitchSettingsCard
import com.android.ty.systemservices.compose.ui.settings.luch.TextInputSettingsCard
import com.android.ty.systemservices.compose.ui.widget.BaseWidget_Seek
import com.android.ty.systemservices.compose.ui.widget.SettingsItem
import com.android.ty.systemservices.compose.ui.widget.NavigationItemWidget
import com.android.ty.systemservices.compose.ui.widget.SegmentedColumn
import com.android.ty.systemservices.compose.ui.widget.StatusCard
import com.android.ty.systemservices.compose.ui.widget.SwitchWidget
import com.android.ty.systemservices.compose.ui.widget.SwitchOw
import com.android.ty.systemservices.compose.ui.widget.InfoTipCard
import com.android.ty.systemservices.compose.ui.widget.setting.ExpressiveBackButton
import com.android.ty.systemservices.compose.ui.widget.menu.useNewDropdownPopup
import com.android.ty.systemservices.compose.ui.widget.ExpandableCard
import com.android.ty.systemservices.service.OverlayPreferenceSettings

import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferencesScreen() {
    val context = LocalContext.current
    // 启动时从 SharedPreferences 恢复下拉菜单新/旧版开关（默认 false）
    LaunchedEffect(Unit) {
        useNewDropdownPopup = context.getSharedPreferences("systemservices_settings", Context.MODE_PRIVATE)
            .getBoolean("new_popup", false)
    }
    var adbVerify by remember { mutableStateOf(false) }
    var internetAccess by remember { mutableStateOf(true) }
    var ignoreBattery by remember { mutableStateOf(false) }
    var hideLauncherIcon by remember { mutableStateOf(false) }
    var seekStepMode by rememberSaveable { mutableStateOf(SeekStepMode.Normal) }
    var advancedExpanded by remember { mutableStateOf(false) }
    var advancedExpanded2 by remember { mutableStateOf(false) }
    var bigTitleImpl by remember { mutableStateOf(loadBigTitleImpl(context)) }
    var mainTitleImpl by remember { mutableStateOf(loadMainTitleImpl(context)) }

    // 主页大标题头部由 MingActivity 的 CollapsingToolbarBaseActivity 承载，
    // 这里不再包 Scaffold/TopAppBar；LazyColumn 必须带 nestedScroll 才能联动 AppBar 折叠。
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .nestedScroll(rememberNestedScrollInteropConnection()),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
            item {
                StatusCard(
                    appName = stringResource(R.string.app_name),
                    version = stringResource(R.string.status_version),
                    icon = painterResource(id = R.drawable.ic_tyve),
                    useBlur = true,
                )
            }
            item {
                InfoTipCard(stringResource(R.string.app_name))
            }

            item {
                SegmentedColumn(title = stringResource(R.string.section_personalization)) {
                    item {
                        DropDownMenuWidget(
                                icon = Icons.Filled.Settings,
                                title = stringResource(R.string.big_title_style),
                                description = stringResource(bigTitleImpl.labelRes),
                                choice = bigTitleImpl.ordinal,
                                data = BigTitleImpl.entries.map { stringResource(it.labelRes) },
                                onChoiceChange = { index ->
                                    bigTitleImpl = BigTitleImpl.entries[index]
                                    saveBigTitleImpl(context, bigTitleImpl)
                                },
                        )
                    }
                    item {
                        DropDownMenuWidget(
                                icon = Icons.Filled.Settings,
                                title = stringResource(R.string.main_big_title_style),
                                description = stringResource(mainTitleImpl.labelRes),
                                choice = mainTitleImpl.ordinal,
                                data = MainTitleImpl.entries.map { stringResource(it.labelRes) },
                                onChoiceChange = { index ->
                                    mainTitleImpl = MainTitleImpl.entries[index]
                                    saveMainTitleImpl(context, mainTitleImpl)
                                    (context as? Activity)?.recreate()
                                },
                        )
                    }
                    item {
                        NavigationItemWidget(
                            icon = Icons.Filled.Science,
                            title = "TestScreen",
                            description = "预览移植的 SettingsCard 系列组件",
                            onClick = {
                                context.startActivity(Intent(context, TestActivity::class.java))
                            },
                        )
                    }
                    //item {
                    //    NavigationItemWidget(
                    //        icon = Icons.Filled.Science,
                    //        title = stringResource(R.string.lab),
                    //        description = stringResource(R.string.lab_desc),
                    //        onClick = {
                    //            context.startActivity(Intent(context, LabActivity::class.java))
                    //        },
                    //    )
                    //}
                    //item {
                    //    NavigationItemWidget(
                    //        icon = Icons.Filled.Science,
                    //        title = stringResource(R.string.preference),
                    //        description = stringResource(R.string.preference_desc),
                    //        onClick = {
                    //            context.startActivity(Intent(context, XmlPreferenceActivity::class.java))
                    //        },
                    //    )
                    //}
                }
            }

            item {
                SegmentedColumn(title = stringResource(R.string.section_basic)) {
                    item {
                        SwitchWidget(
                            icon = Icons.Filled.ArrowDropDown,
                            title = stringResource(R.string.dropdown_popup_new),
                            description = stringResource(R.string.dropdown_popup_new_desc),
                            checked = useNewDropdownPopup,
                            onCheckedChange = { newValue ->
                                useNewDropdownPopup = newValue
                                context.getSharedPreferences("systemservices_settings", Context.MODE_PRIVATE)
                                    .edit().putBoolean("new_popup", newValue).apply()
                            },
                        )
                    }
                    item {
                        // 控制 overlay_preferences.xml 用哪种选择器：
                        // 开 → ComposeDropDownMenuPreference（M3 下拉菜单）
                        // 关 → ComposeListPreference（原生选择对话框）
                        SwitchWidget(
                            icon = Icons.Filled.Tune,
                            title = stringResource(R.string.overlay_dropdown_switch),
                            description = stringResource(R.string.overlay_dropdown_switch_desc),
                            checked = OverlayPreferenceSettings.useDropdown,
                            onCheckedChange = { newValue ->
                                OverlayPreferenceSettings.setDropdownEnabled(context, newValue)
                            },
                        )
                    }
                }
            }

            item {
                PortedSettingsGroup(
                    stepMode = seekStepMode,
                    onStepModeChange = { seekStepMode = it },
                )
            }

            item {
                ExpandableCard(
                    title = stringResource(R.string.advanced_options),
                    description = stringResource(R.string.advanced_options_hint),
                    icon = Icons.Filled.Build,
                    expanded = advancedExpanded,
                    onExpandedChange = { advancedExpanded = it },
                ) {
                    BaseWidget(
                        icon = Icons.Filled.Build,
                        title = stringResource(R.string.expanded_item),
                        description = stringResource(R.string.expanded_item_desc),
                    )
                }
            }
            item {
                ExpandableCard(
                    title = stringResource(R.string.advanced_options),
                    description = stringResource(R.string.advanced_options_hint),
                    icon = Icons.Filled.Build,
                    expanded = advancedExpanded2,
                    onExpandedChange = { advancedExpanded2 = it },
                ) {
                    BaseWidget(
                        icon = Icons.Filled.Build,
                        title = stringResource(R.string.expanded_item),
                        description = stringResource(R.string.expanded_item_desc),
                    )
                }
            }

            item {
                HighlightableCard()
            }
        }
}

/**
 * 折叠卡片：参照示例应用（MDDemo）OverlayCardContent 的
 * Card + AnimatedVisibility(expandVertically/shrinkVertically) 实现，
 * 替代原版 SegmentedColumn.expandableItem 的动态插删写法，规避 Android 12 闪退。
 */

/**
 * 演示 InteractiveHighlight：点按时出现 AGSL 水波高亮。
 */
@Composable
private fun HighlightableCard() {
    val scope = rememberCoroutineScope()
    // RuntimeShader 需要 API 33（Android 13），低版本退化为普通卡片、无高亮
    val highlight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        remember(scope) { InteractiveHighlight(scope) }
    } else {
        null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .then(highlight?.gestureModifier ?: Modifier)
            .then(highlight?.modifier ?: Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.TouchApp,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Column {
                Text(
                    text = stringResource(R.string.ripple_highlight),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.ripple_highlight_desc),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/**
 * 滑杆的档位粒度。选项与文案全部由枚举本身驱动：
 * 条目取 `SeekStepMode.entries`（不写死列表），文案取 [labelRes]（走 string 资源，不写死字符串），
 * 选中项只决定 [steps]，两者之间没有任何 when/if 分支映射。
 */
private enum class SeekStepMode(@StringRes val labelRes: Int, val steps: Int) {
    Coarse(R.string.seek_mode_coarse, 4),
    Normal(R.string.seek_mode_normal, 9),
    Fine(R.string.seek_mode_fine, 19),
}

// ---------------------------------------------------------------
// 移植组件的条目描述（SettingsItem）：图标 / 标题 / 描述全部声明在这里，
// Screen 里只引用常量，不再出现 stringResource(R.string.xxx) 这类解析代码。
// ---------------------------------------------------------------

private val seekItem = SettingsItem(
    titleRes = R.string.expressive_seekbar_title,
    descriptionRes = R.string.expressive_seekbar_desc,
    icon = Icons.Filled.Tune,
)

private val switchItem = SettingsItem(
    titleRes = R.string.ported_switch_title,
    descriptionRes = R.string.ported_switch_desc,
    icon = Icons.Filled.Wifi,
)

private val enumItem = SettingsItem(
    titleRes = R.string.ported_enum_title,
    descriptionRes = R.string.ported_enum_desc,
    icon = Icons.Filled.List,
)

private val sliderItem = SettingsItem(
    titleRes = R.string.ported_slider_title,
    descriptionRes = R.string.ported_slider_desc,
    icon = Icons.Filled.VolumeUp,
)

private val inputItem = SettingsItem(
    titleRes = R.string.ported_input_title,
    descriptionRes = R.string.ported_input_desc,
    icon = Icons.Filled.Edit,
)

private val listItem = SettingsItem(
    titleRes = R.string.ported_list_title,
    descriptionRes = R.string.ported_list_desc,
    icon = Icons.Filled.RadioButtonChecked,
)

private val multiItem = SettingsItem(
    titleRes = R.string.ported_multi_title,
    descriptionRes = R.string.ported_multi_desc,
    icon = Icons.Filled.CheckBox,
)

private val idItem = SettingsItem(
    titleRes = R.string.ported_id_title,
    descriptionRes = R.string.ported_id_desc,
    icon = Icons.Filled.Fingerprint,
)

/**
 * 滑杆条目：只做"标题 + 滑杆"，排版交给 [BaseWidget_Seek]。
 * 档位由外部传入 [steps]，与独立的枚举卡联动。
 */
@Composable
private fun ExpressiveSeekBarItem(steps: Int) {
    var progress by rememberSaveable { mutableFloatStateOf(40f) }

    BaseWidget_Seek(
        item = seekItem,
        progress = progress,
        onProgressChange = { progress = it },
        valueText = progress.roundToInt().toString(),
        valueRange = 0f..100f,
        steps = steps,
    )
}

/**
 * 移植组件合集：全部条目共用 [SettingsItem] 描述 + BaseWidget 血统的卡片，
 * 统一放进同一个 [SegmentedColumn]，圆角由 `LocalSegmentedItemShape` 自动推导，
 * 增删条目、调整顺序都不用改 position。
 */
@Composable
private fun PortedSettingsGroup(
    stepMode: SeekStepMode,
    onStepModeChange: (SeekStepMode) -> Unit,
) {
    var wifi by rememberSaveable { mutableStateOf(true) }
    var volume by rememberSaveable { mutableIntStateOf(40) }
    var deviceName by rememberSaveable { mutableStateOf("TySystemServices") }
    var listId by rememberSaveable { mutableStateOf("b") }
    var idItemId by rememberSaveable { mutableStateOf("item_2") }
    // Set 不是 Saveable，只做会话内状态
    var multiSelected by remember { mutableStateOf(setOf("a")) }
    val stepModeLabel = stringResource(stepMode.labelRes)

    SegmentedColumn(title = stringResource(R.string.section_ported)) {
        item {
            SwitchSettingsCard(
                item = switchItem,
                checked = wifi,
                onCheckedChange = { wifi = it },
            )
        }
        item {
            ExpressiveSeekBarItem(steps = stepMode.steps)
        }
        item {
            EnumSettingsCard(
                // 描述是"当前档位：xx"，属于运行期文案，用 copy 覆盖即可
                item = enumItem.copy(description = "当前档位：$stepModeLabel"),
                value = stepMode,
                entries = SeekStepMode.entries.toList(),
                getRadioText = { stringResource(it.labelRes) },
                onRadioClick = onStepModeChange,
            )
        }
        item {
            IntSliderSettingsCard(
                item = sliderItem,
                value = volume,
                valueRange = 0f..100f,
                steps = 9,
                suffix = "%",
                onValueChange = { volume = it },
            )
        }
        item {
            TextInputSettingsCard(
                item = inputItem,
                value = deviceName,
                onValueChange = { deviceName = it },
            )
        }
        item {
            ListSettingsCard(
                item = listItem,
                items = portedOptions,
                currentId = listId,
                defaultId = "a",
                getItemText = { it.title },
                getItemId = { it.id },
                getItemSummary = { option ->
                    Text(
                        text = option.desc,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                onValueChange = { listId = it.id },
            )
        }
        item {
            StringListSettingsCard(
                item = multiItem.copy(description = "已选 ${multiSelected.size} 项"),
                items = portedOptions,
                selectedIds = multiSelected,
                onSelectionChange = { multiSelected = it },
                getItemID = { it.id },
                getItemText = { it.title },
            )
        }
        item {
            SimpleIDListCard(
                item = idItem,
                items = portedIdItems,
                currentId = idItemId,
                defaultId = "item_1",
                onValueChange = { idItemId = it.id },
            )
        }
    }
}

/** 移植组件的演示数据 */
private data class PortedOption(
    val id: String,
    val title: String,
    val desc: String,
)

private val portedOptions = listOf(
    PortedOption("a", "选项 A", "第一项说明"),
    PortedOption("b", "选项 B", "第二项说明"),
    PortedOption("c", "选项 C", "第三项说明"),
)

private val portedIdItems = listOf(
    IDItem("item_1", "条目一"),
    IDItem("item_2", "条目二"),
    IDItem("item_3", "条目三"),
)
