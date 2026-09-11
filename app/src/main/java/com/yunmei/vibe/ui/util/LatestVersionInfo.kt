package com.yunmei.vibe.ui.util

/** 远端最新 Release 版本信息（与 KernelSU-Style-UI-Kit `ui/util/LatestVersionInfo.kt` 同构）。 */
data class LatestVersionInfo(
    val versionName: String = "",
    val downloadUrl: String = "",
    val changelog: String = "",
) {
    /** 是否存在可用更新（versionName 非空即视为有更新）。 */
    val hasUpdate: Boolean get() = versionName.isNotBlank()
}
