package com.android.ty.systemservices.compose

import android.os.Bundle
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import com.android.ty.systemservices.BackButtonHelper
import com.android.ty.systemservices.R
import com.android.ty.systemservices.compose.ui.screen.DevScreen
import com.android.ty.systemservices.compose.ui.theme.TyTheme

/**
 * 开发调试页宿主：走 [CollapsingToolbarBaseActivity] 的大标题体系，
 * 头部（含返回按钮）由基类提供，Compose 内容放进 `content_frame`。
 */
class DevActivity : CollapsingToolbarBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 根据是否从本应用其它页面进入，决定返回按钮显隐
        setBackButton(BackButtonHelper.isFromOwnActivity(this))
        setLargeTitle(R.string.dev_title)

        // 内容放 content_frame，头部布局保持不变；
        // 注意不能调 setContent，那会替换掉基类的整套布局（含标题栏）
        val contentFrame = findViewById<FrameLayout>(R.id.content_frame)
        val composeView = ComposeView(this).apply {
            setContent {
                TyTheme {
                    DevScreen()
                }
            }
        }
        contentFrame.addView(
            composeView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
    }
}
