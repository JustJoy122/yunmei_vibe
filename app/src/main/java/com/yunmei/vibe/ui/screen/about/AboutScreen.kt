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

    // 版本号按构建类型区分，统一在此生成（Material / Miuix 两个主题共用同一个 state，不各自硬编码）：
    // - Debug：测试版 {versionName} (CI #{run_number})；本地未注入 BUILD_STAMP 时显示「本地」
    // - Release：正式版 {versionName}
    val versionInfo = if (BuildConfig.DEBUG) {
        val buildStamp = BuildConfig.BUILD_STAMP
        if (buildStamp.isBlank() || buildStamp == "0") {
            stringResource(R.string.about_version_debug_local, BuildConfig.VERSION_NAME)
        } else {
            stringResource(R.string.about_version_debug, BuildConfig.VERSION_NAME, buildStamp)
        }
    } else {
        stringResource(R.string.about_version_release, BuildConfig.VERSION_NAME)
    }

    val state = AboutUiState(
        title = stringResource(R.string.about),
        appName = stringResource(R.string.app_name),
        versionInfo = versionInfo,
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
