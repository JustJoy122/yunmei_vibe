package com.yunmei.client.ui.screen.about

import androidx.compose.runtime.Immutable

@Immutable
data class AboutUiState(
    val title: String,
    val appName: String,
    /** 「通道 版本名 (版本号)」格式，与 InstallerX Revived 的 app_version_info_format 对齐。 */
    val versionInfo: String,
    val links: List<LinkInfo>,
)

@Immutable
data class AboutScreenActions(
    val onBack: () -> Unit,
    val onOpenLink: (String) -> Unit,
)
