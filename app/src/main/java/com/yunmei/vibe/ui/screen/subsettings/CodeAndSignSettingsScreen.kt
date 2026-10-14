package com.yunmei.vibe.ui.screen.subsettings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yunmei.vibe.ui.LocalUiMode
import com.yunmei.vibe.ui.SignLocationMode
import com.yunmei.vibe.ui.UiMode
import com.yunmei.vibe.ui.navigation.LocalNavigator
import com.yunmei.vibe.ui.viewmodel.SettingsViewModel

/**
 * 二级设置页「取码与打卡」：自动获取密码 / 未保存账号时使用首个账号 / 打卡定位方式。
 *
 * 同样复用 SettingsViewModel；存储键（autoCode / alwaysCode / signLocationMode）不变。
 */
@Composable
fun CodeAndSignSettingsScreen() {
    val navigator = LocalNavigator.current
    val viewModel = viewModel<SettingsViewModel>()
    val functionalState by viewModel.functionalState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val state = CodeAndSignSettingsUiState(settings = functionalState.settings)
    val actions = CodeAndSignSettingsActions(
        onBack = dropUnlessResumed { navigator.pop() },
        onSetAutoCode = viewModel::setAutoCode,
        onSetAlwaysCode = viewModel::setAlwaysCode,
        onSetSignLocationMode = { index ->
            // 下拉列表顺序由 SignLocationMode.entries 决定，避免 index 与取值各自维护而错位。
            viewModel.setSignLocationMode(
                SignLocationMode.entries.getOrElse(index) { SignLocationMode.DEFAULT }.value
            )
        },
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> CodeAndSignSettingsMiuix(state, actions)
        UiMode.Material -> CodeAndSignSettingsMaterial(state, actions)
    }
}
