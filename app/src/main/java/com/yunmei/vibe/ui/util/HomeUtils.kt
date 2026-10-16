package com.yunmei.vibe.ui.util

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.core.content.pm.PackageInfoCompat

@Immutable
data class AppVersion(
    val versionName: String,
    val versionCode: Long
)

@Immutable
data class SystemInfo(
    val appVersion: String,
)

fun getAppVersion(context: Context): AppVersion = runCatching {
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    AppVersion(
        versionName = packageInfo.versionName ?: "",
        versionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
    )
}.getOrDefault(AppVersion(versionName = "", versionCode = 0L))
