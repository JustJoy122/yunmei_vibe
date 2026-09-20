// SPDX-License-Identifier: GPL-3.0-only
// 提取自 InstallerX Revived：
// ui/page/main/settings/preferred/about/OpenSourceLicensePage.kt
// 仅做包名迁移与配色适配（页面底色/顶栏回归 KernelSU Kit 全局主题色，去掉 InstallerX 的模糊与 surfaceContainer 底色）。
package com.yunmei.vibe.ui.screen.license

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.m3.libraryColors
import com.mikepenz.aboutlibraries.ui.compose.m3.style.m3VariantColors
import com.mikepenz.aboutlibraries.ui.compose.variant.LibraryDetailMode
import com.mikepenz.aboutlibraries.ui.compose.variant.LibraryRow
import com.yunmei.vibe.R
import com.yunmei.vibe.ui.navigation.LocalNavigator

/** InstallerX `ui/theme/Shape.kt` 的圆角常量（16.dp）等值内联。 */
private val CornerRadius = 16.dp

/**
 * 开放源代码许可页（Material）——InstallerX Revived 原版结构：
 * Scaffold + LargeFlexibleTopAppBar + AboutLibraries LibrariesContainer + 许可证详情 AlertDialog。
 * 配色按 KernelSU Kit 规范：页面用 Scaffold 默认背景，顶栏 surface，列表色走 Material3 动态取色 token。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OpenSourceLicensePage() {
    val navigator = LocalNavigator.current
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)

    // 由 AboutLibraries Gradle 插件在构建期生成的清单。
    val libraries by produceLibraries(R.raw.aboutlibraries)

    var selectedLibrary by remember { mutableStateOf<Library?>(null) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeFlexibleTopAppBar(
                windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
                title = { Text(text = stringResource(id = R.string.about_open_source_license)) },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    Row {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = null,
                            )
                        }
                        Spacer(modifier = Modifier.size(16.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { paddingValues ->
        LibrariesContainer(
            libraries = libraries,
            modifier = Modifier.fillMaxSize(),
            contentPadding = paddingValues + PaddingValues(horizontal = 16.dp),
            colors = LibraryDefaults.libraryColors(
                libraryBackgroundColor = MaterialTheme.colorScheme.surfaceContainer,
                libraryContentColor = MaterialTheme.colorScheme.onSurface,
            ),
            variantColors = LibraryDefaults.m3VariantColors(
                rowBackground = MaterialTheme.colorScheme.surfaceBright,
                rowExpandedBackground = MaterialTheme.colorScheme.surfaceBright,
                rowOnBackground = MaterialTheme.colorScheme.onSurface,
                rowSubtleContent = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            detailMode = LibraryDetailMode.None,
            libraryRow = { _, library, expanded, toggle, style ->
                LibraryRow(
                    library = library,
                    expanded = expanded,
                    onToggle = toggle,
                    style = style,
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(CornerRadius)),
                )
            },
            onLibraryClick = { library: Library ->
                selectedLibrary = library
                true
            },
        )

        selectedLibrary?.let { library ->
            val uriHandler = LocalUriHandler.current
            AlertDialog(
                onDismissRequest = { selectedLibrary = null },
                confirmButton = {
                    Button(onClick = { selectedLibrary = null }) {
                        Text(stringResource(R.string.close))
                    }
                },
                dismissButton = {
                    library.website?.let { url ->
                        OutlinedButton(onClick = { uriHandler.openUri(url) }) {
                            Text(stringResource(R.string.visit_home_page))
                        }
                    }
                },
                title = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = library.name,
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                },
                text = {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(library.licenses.toList(), key = { it.hashCode() }) { license ->
                            OutlinedCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.outlinedCardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                ),
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                ) {
                                    Row {
                                        Text(
                                            text = license.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    license.url?.let { url ->
                                                        uriHandler.openUri(url)
                                                    }
                                                },
                                        )
                                    }

                                    Spacer(modifier = Modifier.size(8.dp))

                                    Text(
                                        text = license.licenseContent
                                            ?: stringResource(R.string.no_license_text),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                },
                properties = DialogProperties(usePlatformDefaultWidth = false),
                modifier = Modifier.padding(24.dp),
            )
        }
    }
}
