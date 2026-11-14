package com.yunmei.vibe.ui.screen.resetpassword

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yunmei.vibe.ui.LocalUiMode
import com.yunmei.vibe.ui.UiMode
import com.yunmei.vibe.ui.navigation.LocalNavigator

/**
 * 重设密码二级页。
 *
 * 入口在个人中心「账号安全」分组；页面只做一件事：用短信验证码换一次改密机会。
 * 两套主题共用同一份 [ResetPasswordUiState] / [ResetPasswordActions]，只换渲染构件。
 */
@Composable
fun ResetPasswordScreen() {
    val navigator = LocalNavigator.current
    val viewModel = viewModel<ResetPasswordViewModel>()
    viewModel.load()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 改密成功：密码已经变了，停在原页没有意义，直接退回上一级。
    LaunchedEffect(state.success) {
        if (state.success) navigator.pop()
    }

    val actions = remember(viewModel, navigator) {
        ResetPasswordActions(
            onBack = { navigator.pop() },
            onCodeChange = viewModel::onCodeChange,
            onPasswordChange = viewModel::onPasswordChange,
            onToggleShowPassword = viewModel::toggleShowPassword,
            onSendCode = viewModel::sendCode,
            onSubmit = viewModel::submit,
        )
    }

    when (LocalUiMode.current) {
        UiMode.Material -> ResetPasswordMaterial(state, actions)
        UiMode.Miuix -> ResetPasswordMiuix(state, actions)
    }
}
