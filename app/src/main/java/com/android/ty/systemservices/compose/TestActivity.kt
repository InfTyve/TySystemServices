package com.android.ty.systemservices.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.android.ty.systemservices.compose.ui.screen.TestScreen
import com.android.ty.systemservices.compose.ui.theme.TyTheme

/**
 * 移植组件预览页的宿主。仅用于开发期查看 SettingsCard 系列效果。
 */
class TestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TyTheme {
                TestScreen(onBack = { finish() })
            }
        }
    }
}
