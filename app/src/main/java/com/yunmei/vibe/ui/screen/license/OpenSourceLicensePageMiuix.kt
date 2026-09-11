// SPDX-License-Identifier: GPL-3.0-only
// 提取自 InstallerX Revived：
// ui/page/miuix/settings/preferred/about/ossLicensePage/MiuixOpenSourceLicensePage.kt
// 包名迁移 + 配色/模糊适配：顶栏改用本项目既有 BlurredBar / rememberBlurBackdrop，
// 颜色全部取自 MiuixTheme.colorScheme（自动适配莫奈开/关两套色板）。
package com.yunmei.vibe.ui.screen.license

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.yunmei.vibe.R
import com.yunmei.vibe.ui.navigation3.LocalNavigator
import com.yunmei.vibe.ui.theme.LocalEnableBlur
import com.yunmei.vibe.ui.util.BlurredBar
import com.yunmei.vibe.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 开放源代码许可页（Miuix）——InstallerX Revived 原版结构：
 * Scaffold + TopAppBar + MiuixLibrariesContainer（LibraryCard + WindowDialog 许可证详情）。
 */
@Composable
fun MiuixOpenSourceLicensePage() {
    val navigator = LocalNavigator.current
    val libraries by produceLibraries(R.raw.aboutlibraries)
    val scrollBehavior = MiuixScrollBehavior()

    val layoutDirection = LocalLayoutDirection.current
    val horizontalSafeInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues()

    val enableBlur = LocalEnableBlur.current
    val topBarBackdrop = rememberBlurBackdrop(enableBlur)

    Scaffold(
        topBar = {
            BlurredBar(backdrop = topBarBackdrop, blurActive = topBarBackdrop != null) {
                TopAppBar(
                    color = if (topBarBackdrop != null) Color.Transparent else colorScheme.surface,
                    title = stringResource(id = R.string.about_open_source_license),
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = null,
                                tint = colorScheme.onBackground,
                            )
                        }
                    },
                )
            }
        },
    ) { paddingValues ->
        MiuixLibrariesContainer(
            modifier = Modifier
                .fillMaxSize()
                .then(topBarBackdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier)
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                start = horizontalSafeInsets.calculateStartPadding(layoutDirection),
                top = paddingValues.calculateTopPadding(),
                end = horizontalSafeInsets.calculateEndPadding(layoutDirection),
            ),
            libraries = libraries,
        )
    }
}
