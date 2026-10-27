package com.android.ty.systemservices.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.android.ty.systemservices.compose.ui.screen.DevScreen
import com.android.ty.systemservices.compose.ui.theme.TyTheme

/**
 * 开发调试页宿主：Compose 实现，页面内容见 [DevScreen]。
 */
class DevActivity : CollapsingToolbarBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setBackButton(BackButtonHelper.isFromOwnActivity(this))
        setLargeTitle(R.string.title_preferences)

        // 主页内容：ComposeView 放进 content_frame（无 Scaffold/TopAppBar，头部由基类承载）
        val contentFrame = findViewById<FrameLayout>(R.id.content_frame)
        val composeView = ComposeView(this)
        contentFrame.addView(
            composeView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        setContent {
            TyTheme {
                DevScreen(onBack = { finish() })
            }
        }
    }
}
