package com.yunmei.vibe.ui.screen.about

import androidx.compose.runtime.Immutable

@Immutable
data class AboutUiState(
    val title: String,
    val appName: String,
    /** 「通道 版本名 (版本号)」格式，与 InstallerX Revived 的 app_version_info_format 对齐。 */
    val versionInfo: String,
)

@Immutable
data class AboutScreenActions(
    val onBack: () -> Unit,
    /** 查看源代码（跳转本仓库 GitHub）。 */
    val onOpenSource: () -> Unit,
    /** 开放源代码许可（跳转 License 页）。 */
    val onOpenLicense: () -> Unit,
)
