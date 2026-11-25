package com.android.ty.systemservices.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.android.ty.systemservices.CollapsingToolbarBaseActivity
import com.android.ty.systemservices.compose.ui.screen.TestScreen
import com.android.ty.systemservices.compose.ui.theme.TyTheme

/**
 * 移植组件预览页的宿主。仅用于开发期查看 SettingsCard 系列效果。
 */
class TestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 调试开关：debug 包里由 OpenTestActivity 通过 extra 关闭返回按钮；
        // release 里没人传这个 extra，行为与原来完全一致。
        val hideBack = intent.getBooleanExtra(
            CollapsingToolbarBaseActivity.EXTRA_DEBUG_HIDE_BACK_BUTTON,
            false,
        )
        setContent {
            TyTheme {
                TestScreen(onBack = { finish() }, showBack = !hideBack)
            }
        }
    }
}
