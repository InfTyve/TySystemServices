package com.android.ty.systemservices

import android.os.Bundle
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import com.android.ty.systemservices.compose.ui.theme.TyTheme

class AboutActivity : CollapsingToolbarBaseActivity(TitleStyle.MAIN) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setBackButton(BackButtonHelper.isFromOwnActivity(this))
        setLargeTitle(R.string.about)

        val contentFrame = findViewById<FrameLayout>(R.id.content_frame)
        val composeView = ComposeView(this).apply {
            setContent {
                TyTheme {
                    AboutScreen()
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
