package com.yunmei.vibe.ui.util

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import androidx.core.content.pm.PackageInfoCompat
import com.yunmei.vibe.R
import com.yunmei.vibe.data.model.SignStatus

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

/**
 * 打卡状态行的统一文案，两套主题共用。
 *
 * - 已打卡：[SignStatus.signTime] 是 `HH:mm:ss`；
 * - 未打卡但有时段：带上「时段 22:00 - 23:00」，用户能判断是不是还没到点；
 * - 学校没配时段：只显示「未打卡」。
 */
@Composable
fun signStatusLine(status: SignStatus): String = when {
    status.signed -> stringResource(R.string.unlock_sign_status_signed, status.signTime)
    status.hasWindow -> stringResource(
        R.string.unlock_sign_status_window,
        status.startTime.ifBlank { "—" },
        status.endTime.ifBlank { "—" },
    )
    else -> stringResource(R.string.unlock_sign_status_unsigned)
}
