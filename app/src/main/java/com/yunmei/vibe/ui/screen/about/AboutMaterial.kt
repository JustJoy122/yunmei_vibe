package com.yunmei.vibe.ui.screen.about

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.twotone.Code
import androidx.compose.material.icons.twotone.Copyright
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yunmei.vibe.R
import com.yunmei.vibe.ui.component.setting.NavigationItemWidget
import com.yunmei.vibe.ui.component.setting.SegmentedColumn
import com.yunmei.vibe.ui.theme.LocalEnableBlur

/**
 * 关于页（Material）。
 *
 * 结构 1:1 复用 InstallerX Revived 的 AboutPage：
 * 顶部应用信息卡（StatusWidget / CardWidget）+ 由 [SegmentedColumn] 包裹的功能项列表
 * （[NavigationItemWidget] / BaseWidget）。间距、内边距、圆角与上游完全一致：
 *
 * - 卡片项：`Box(Modifier.padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 12.dp))`
 * - 功能项：[SegmentedColumn] 自带 `horizontal 16dp / vertical 8dp` 内边距，
 *   项与项之间由 `ListItemDefaults.SegmentedGap` 留缝，首尾项 16dp 大圆角、中间连接处 5dp 小圆角
 *   （圆角由 SegmentedColumn 通过 `LocalSegmentedItemShape` 下发给 BaseWidget）
 * - 列表：`LazyColumn(contentPadding = innerPadding, horizontalAlignment = CenterHorizontally)`
 *
 * 页面底色保持本项目既有约定（KernelSU Kit 全局主题色，即 surface/background），
 * 卡片取 surfaceContainerHigh，深色下与页面底色形成清晰层级（见 [AboutStatusCard]）。
 */
@Composable
fun AboutScreenMaterial(
    state: AboutUiState,
    actions: AboutScreenActions,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    // 与 InstallerX AboutPage 的 `useBlur` 语义对齐：模糊关闭时卡片为实色 + 默认 elevation。
    val useBlur = LocalEnableBlur.current

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(state.title) },
                navigationIcon = {
                    IconButton(
                        onClick = actions.onBack
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                ),
                scrollBehavior = scrollBehavior
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = paddingValues,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(top = 8.dp, bottom = 12.dp),
                ) {
                    AboutStatusCard(
                        appName = state.appName,
                        versionInfo = state.versionInfo,
                        useBlur = useBlur,
                    )
                }
            }
            item {
                // 与 InstallerX 一致：功能项由 SegmentedColumn 成组，避免自研 Column 布局
                // 造成项间无间隙、四角全圆角而互相"咬角"。
                SegmentedColumn {
                    item {
                        NavigationItemWidget(
                            icon = Icons.TwoTone.Code,
                            title = stringResource(R.string.about_view_source_code),
                            description = stringResource(R.string.about_view_source_code_summary),
                            onClick = actions.onOpenSource,
                        )
                    }
                    item {
                        NavigationItemWidget(
                            icon = Icons.TwoTone.Copyright,
                            title = stringResource(R.string.about_open_source_license),
                            description = stringResource(R.string.about_open_source_license_summary),
                            onClick = actions.onOpenLicense,
                        )
                    }
                }
            }
        }
    }
}
