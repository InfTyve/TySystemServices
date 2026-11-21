package com.android.ty.systemservices

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.ty.systemservices.compose.ContributorsActivity
import com.android.ty.systemservices.compose.ui.widget.NavigationItemWidget
import com.android.ty.systemservices.compose.ui.widget.SegmentedColumn

@Composable
fun AboutScreen() {
    val context = LocalContext.current   // ← 新增

    Box(modifier = Modifier.fillMaxSize()) {   // ← 补上 {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .nestedScroll(rememberNestedScrollInteropConnection()),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_ty),
                        contentDescription = "Logo",
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(16.dp)
                            .size(48.dp)
                    )
                }
            }
            item {
                SegmentedColumn(title = stringResource(id = R.string.space)) {
                    item {
                        NavigationItemWidget(
                            icon = Icons.Filled.Construction,
                            title = stringResource(R.string.lab),
                            description = stringResource(R.string.lab_desc),
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
}
