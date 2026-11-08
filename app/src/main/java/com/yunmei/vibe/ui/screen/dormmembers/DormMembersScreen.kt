package com.yunmei.vibe.ui.screen.dormmembers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yunmei.vibe.R
import com.yunmei.vibe.ui.LocalUiMode
import com.yunmei.vibe.ui.UiMode
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 群组成员页（占位）。
 *
 * 门锁列表卡片的「群组成员」入口已经打通路由；页面本身按本轮要求不实现，
 * 只提供一个可返回的空页面，避免入口点了没反应。
 */
@Composable
fun DormMembersScreen(label: String) {
    when (LocalUiMode.current) {
        UiMode.Material -> MaterialPlaceholder(label)
        UiMode.Miuix -> MiuixPlaceholder(label)
    }
}

@Composable
private fun MaterialPlaceholder(label: String) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dorm_members)) },
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label + "\n" + stringResource(R.string.dorm_members_placeholder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun MiuixPlaceholder(label: String) {
    MiuixScaffold(
        topBar = {
            MiuixTopAppBar(title = stringResource(R.string.dorm_members))
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            MiuixText(
                text = label + "\n" + stringResource(R.string.dorm_members_placeholder),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}
