package com.yunmei.vibe.ui.screen.license

import androidx.compose.runtime.Composable
import com.yunmei.vibe.ui.LocalUiMode
import com.yunmei.vibe.ui.UiMode

/**
 * 「开放源代码许可」页分派入口。
 * 两套主题均直接复用 InstallerX Revived 的原版实现（AboutLibraries）：
 * - Material → [OpenSourceLicensePage]
 * - Miuix   → [MiuixOpenSourceLicensePage]
 */
@Composable
fun LicenseScreen() {
    when (LocalUiMode.current) {
        UiMode.Miuix -> MiuixOpenSourceLicensePage()
        UiMode.Material -> OpenSourceLicensePage()
    }
}
