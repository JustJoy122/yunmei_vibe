package com.yunmei.vibe.ui.screen.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yunmei.vibe.ui.LocalUiMode
import com.yunmei.vibe.ui.UiMode
import com.yunmei.vibe.ui.navigation3.LocalNavigator
import com.yunmei.vibe.ui.navigation3.Route

@Composable
fun LoginScreen() {
    val viewModel = viewModel<LoginViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    // 登录成功 → 进入主界面。
    LaunchedEffect(Unit) {
        viewModel.finished.collect {
            navigator.replaceAll(listOf(Route.Main))
        }
    }

    val actions = LoginActions(
        onUsernameChange = viewModel::onUsernameChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSaveCurrentChange = viewModel::onSaveCurrentChange,
        onLogin = viewModel::login,
        onPickSavedUser = viewModel::pickSavedUser,
        onPickSchool = viewModel::pickSchool,
        onPickLock = viewModel::pickLock,
        onToggleShowPassword = viewModel::onToggleShowPassword,
        onSkipSelection = viewModel::skipSelection,
        onMessageShown = viewModel::onMessageShown,
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> LoginScreenMiuix(uiState, actions)
        UiMode.Material -> LoginScreenMaterial(uiState, actions)
    }
}
