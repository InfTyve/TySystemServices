package com.android.ty.systemservices.compose.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
 * - 悬浮窗走 `TYPE_APPLICATION_OVERLAY`，层级在所有应用之上，不抢焦点、不拦触摸；
 * - 没有「显示在其他应用上层」权限时，点击开关会跳到系统授权页。
 */
@Composable
fun DevScreen() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(TestModeOverlay.isEnabled(context)) }
    var hasPermission by remember { mutableStateOf(TestModeOverlay.canDrawOverlays(context)) }
    var checkSetupWizard by remember { mutableStateOf(ServiceDaemon.isSetupCheckEnabled(context)) }

    // 从系统授权页返回时刷新权限与开关状态
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = TestModeOverlay.canDrawOverlays(context)
                enabled = TestModeOverlay.isEnabled(context) && hasPermission
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                // 与 CollapsingToolbar 的 AppBar 联动
                .nestedScroll(rememberNestedScrollInteropConnection()),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
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
        }
    }
}
