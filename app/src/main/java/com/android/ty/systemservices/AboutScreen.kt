package com.android.ty.systemservices

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Code
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
import com.android.ty.systemservices.compose.ui.widget.InfoTipCard
import com.android.ty.systemservices.compose.ui.widget.NavigationItemWidget
import com.android.ty.systemservices.compose.ui.widget.SegmentedColumn

/** Logo 高度：字标要在这个尺寸下仍能看清，再小就退回到「图标」观感了 */
private val LogoHeight = 136.dp

/** 矢量本身的宽高比（viewport 813x856），固定高度后按它算宽度，避免被 contentScale 二次缩放 */
private const val LogoAspectRatio = 813f / 856f

private const val PROJECT_URL = "https://github.com/TYVEIT/TySystemServices"

@Composable
fun AboutScreen(
    projectUrl: String = PROJECT_URL,
) {
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
            // 外层已经有 16dp 的 contentPadding，这里用 noPadding 让卡片和上下两张对齐，
            // 只留纵向间距
            InfoTipCard(
                text = stringResource(R.string.teyun_studio),
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                noPadding = true,
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
                item {
                    NavigationItemWidget(
                        icon = Icons.Filled.Code,
                        title = stringResource(R.string.tysystem_project),
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(projectUrl))
                            try {
                                context.startActivity(intent)
                            } catch (_: ActivityNotFoundException) {
                                // 没有浏览器/能处理 http 的应用，给个提示而不是静默失败
                                Toast.makeText(
                                    context,
                                    R.string.link_open_failed,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
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
