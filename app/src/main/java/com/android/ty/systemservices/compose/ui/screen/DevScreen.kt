package com.android.ty.systemservices.compose.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import com.android.ty.systemservices.compose.ui.widget.AdaptiveContentGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.android.ty.systemservices.R
import com.android.ty.systemservices.compose.ui.widget.InfoTipCard
import com.android.ty.systemservices.compose.ui.widget.SegmentedColumn
import com.android.ty.systemservices.compose.ui.widget.SwitchWidget
import com.android.ty.systemservices.service.ServiceDaemon
import com.android.ty.systemservices.service.TestModeOverlay

/**
 * 开发调试页：一个 TestMode 悬浮窗开关。
 *
 * - 标题栏由宿主 [com.android.ty.systemservices.compose.DevActivity] 的
 *   CollapsingToolbarBaseActivity 提供，这里不再重复 Scaffold 的 TopAppBar；
 * - 开关状态持久化在 `dev_settings`，重启应用由 ServiceSettings 恢复；
 * - 浮层优先走系统层级 TYPE_SYSTEM_ALERT（INTERNAL_SYSTEM_WINDOW），
 *   并由 TestModeOverlay 把 appop 置回 allowed，不受「显示在其他应用上层」开关影响；
 * - 没有系统权限时回退 TYPE_APPLICATION_OVERLAY，点击开关会跳到系统授权页。
 */
@Composable
fun DevScreen() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(TestModeOverlay.isEnabled(context)) }
    var hasPermission by remember { mutableStateOf(TestModeOverlay.canDrawOverlays(context)) }
    var checkSetupWizard by remember { mutableStateOf(ServiceDaemon.isSetupCheckEnabled(context)) }
    // 浮层诊断：挂载数量 / 窗口层级 / 权限与 appop 状态，排查「开关开着却没浮窗」
    var diagnostic by remember { mutableStateOf(TestModeOverlay.diagnostic(context)) }

    // 从系统授权页返回时刷新权限与开关状态
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = TestModeOverlay.canDrawOverlays(context)
                enabled = TestModeOverlay.isEnabled(context) && hasPermission
                diagnostic = TestModeOverlay.diagnostic(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { padding ->
        AdaptiveContentGrid(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                // 与 CollapsingToolbar 的 AppBar 联动
                .nestedScroll(rememberNestedScrollInteropConnection()),
        ) {
            if (!hasPermission) {
                item {
                    InfoTipCard(stringResource(R.string.dev_overlay_permission_hint))
                }
            }
            item {
                SegmentedColumn {
                    item {
                        SwitchWidget(
                            icon = Icons.Filled.Visibility,
                            title = stringResource(R.string.dev_test_mode_title),
                            description = stringResource(R.string.dev_test_mode_desc),
                            checked = enabled,
                            onCheckedChange = { want ->
                                if (want && !hasPermission) {
                                    // 先去要权限，拿到后再开
                                    TestModeOverlay.requestPermission(context)
                                } else {
                                    // 返回值就是最终状态，直接回写开关
                                    enabled = TestModeOverlay.setEnabled(context, want)
                                    hasPermission = TestModeOverlay.canDrawOverlays(context)
                                    diagnostic = TestModeOverlay.diagnostic(context)
                                }
                            },
                        )
                    }
                    item {
                        SwitchWidget(
                            icon = Icons.Filled.Checklist,
                            title = stringResource(R.string.dev_setup_check_title),
                            description = stringResource(R.string.dev_setup_check_desc),
                            checked = checkSetupWizard,
                            onCheckedChange = { want ->
                                checkSetupWizard = want
                                ServiceDaemon.setSetupCheckEnabled(context, want)
                            },
                        )
                    }
                }
            }
            item {
                // 浮层实时状态：views 是实际挂上的窗口数，appop 是系统里那个开关。
                // 「开关开着却没浮窗」时看这一行就能定位卡在哪一层。
                Text(
                    text = diagnostic,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
    }
}
