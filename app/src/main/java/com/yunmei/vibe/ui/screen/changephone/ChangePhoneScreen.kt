package com.yunmei.vibe.ui.screen.changephone

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
 * 换绑手机号二级页。
 *
 * 入口在个人中心「账号安全」分组。换绑成功后会话已被清空，
 * 因此这里不做 pop，而是交给上层回登录页重新登录。
 */
@Composable
fun ChangePhoneScreen(onChanged: () -> Unit) {
    val navigator = LocalNavigator.current
    val viewModel = viewModel<ChangePhoneViewModel>()
    viewModel.load()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.success) {
        if (state.success) onChanged()
    }

    val actions = remember(viewModel, navigator) {
        ChangePhoneActions(
            onBack = { navigator.pop() },
            onNewTelChange = viewModel::onNewTelChange,
            onCodeChange = viewModel::onCodeChange,
            onSendCode = viewModel::sendCode,
            onSubmit = viewModel::submit,
        )
    }

    when (LocalUiMode.current) {
        UiMode.Material -> ChangePhoneMaterial(state, actions)
        UiMode.Miuix -> ChangePhoneMiuix(state, actions)
    }
}
