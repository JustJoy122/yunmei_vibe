package com.yunmei.vibe.ui.screen.about

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.dropUnlessResumed
import com.yunmei.vibe.BuildConfig
import com.yunmei.vibe.R
import com.yunmei.vibe.ui.LocalUiMode
import com.yunmei.vibe.ui.UiMode
import com.yunmei.vibe.ui.navigation3.LocalNavigator
import com.yunmei.vibe.ui.navigation3.Route

/** 「查看源代码」目标仓库。 */
private const val SOURCE_CODE_URL = "https://github.com/JustJoy122/yunmei_vibe"

@Composable
fun AboutScreen() {
    val navigator = LocalNavigator.current
    val uriHandler = LocalUriHandler.current

    val state = AboutUiState(
        title = stringResource(R.string.about),
        appName = stringResource(R.string.app_name),
        // 与 InstallerX Revived 一致：「通道 版本名 (版本号)」。
        versionInfo = stringResource(
            id = R.string.about_version_info_format,
            stringResource(id = R.string.about_channel),
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE,
        ),
    )
    // 「获取更新」入口已移除（检查更新统一由设置页负责），关于页仅保留两项。
    val actions = AboutScreenActions(
        onBack = dropUnlessResumed { navigator.pop() },
        onOpenSource = { uriHandler.openUri(SOURCE_CODE_URL) },
        onOpenLicense = dropUnlessResumed { navigator.push(Route.OpenSourceLicense) },
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> AboutScreenMiuix(state, actions)
        UiMode.Material -> AboutScreenMaterial(state, actions)
    }
}
