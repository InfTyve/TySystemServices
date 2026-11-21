package com.android.ty.systemservices

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.ty.systemservices.compose.ContributorsActivity
import com.android.ty.systemservices.compose.ui.theme.CornerRadius
import com.android.ty.systemservices.compose.ui.widget.NavigationItemWidget
import com.android.ty.systemservices.compose.ui.widget.SegmentedColumn

/** Logo 高度：字标要在这个尺寸下仍能看清，再小就退回到「图标」观感了 */
private val LogoHeight = 136.dp

/** 矢量本身的宽高比（viewport 813x856），固定高度后按它算宽度，避免被 contentScale 二次缩放 */
private const val LogoAspectRatio = 813f / 856f

@Composable
fun AboutScreen() {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .nestedScroll(rememberNestedScrollInteropConnection()),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        item {
            LogoHeader(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
        item {
            SegmentedColumn(title = stringResource(id = R.string.space)) {
                item {
                    NavigationItemWidget(
                        icon = Icons.Filled.Construction,
                        title = stringResource(R.string.contributors_title),
                        description = stringResource(R.string.contributor_role_maintainer),
                        onClick = {
                            val intent = Intent(context, ContributorsActivity::class.java)
                            context.startActivity(intent)
                        },
                    )
                }
            }
        }
    }
}

/**
 * Logo 头部卡片。
 *
 * 和下面的 [SegmentedColumn] 条目用同一套容器（surfaceBright + [CornerRadius]），
 * 因此左右边缘、圆角都能对齐；Logo 按高度定尺，宽度由矢量自身 813:856 的比例撑开。
 * 矢量本身是纯黑描边，这里统一 tint 成 onSurface，深色主题下才不会看不见。
 */
@Composable
private fun LogoHeader(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(CornerRadius))
            .background(colors.surfaceBright)
            .padding(vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_ty),
            contentDescription = stringResource(id = R.string.about),
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(colors.onSurface),
            modifier = Modifier
                .height(LogoHeight)
                .aspectRatio(LogoAspectRatio),
        )
    }
}
