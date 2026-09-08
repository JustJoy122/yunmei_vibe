package com.yunmei.vibe.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yunmei.vibe.R
import com.yunmei.vibe.ui.LocalUiMode
import com.yunmei.vibe.ui.UiMode
import com.yunmei.vibe.ui.component.dialog.ConfirmResult
import com.yunmei.vibe.ui.component.dialog.rememberConfirmDialog
import com.yunmei.vibe.ui.navigation3.Navigator
import com.yunmei.vibe.ui.navigation3.Route
import com.yunmei.vibe.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingPager(
    navigator: Navigator,
    bottomInnerPadding: Dp,
) {
    val viewModel = viewModel<SettingsViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val functionalState by viewModel.functionalState.collectAsStateWithLifecycle()
    val confirmDialog = rememberConfirmDialog()
    val scope = rememberCoroutineScope()

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val state = SettingsScreenState(
        uiMode = uiState.uiMode,
        checkUpdate = uiState.checkUpdate,
        accounts = functionalState.accounts,
        settings = functionalState.settings,
    )

    val removeAccountTitle = stringResource(R.string.settings_remove_account)
    val confirmText = stringResource(R.string.confirm)
    val cancelText = stringResource(R.string.cancel)

    val actions = SettingsScreenActions(
        onSetCheckUpdate = viewModel::setCheckUpdate,
        onSetUiModeIndex = { index ->
            viewModel.setUiMode(if (index == 0) UiMode.Miuix.value else UiMode.Material.value)
        },
        onOpenTheme = { navigator.push(Route.ThemeSettings) },
        onLogin = { navigator.push(Route.Login) },
        onRemoveAccount = { user ->
            scope.launch {
                val result = confirmDialog.awaitConfirm(
                    title = removeAccountTitle,
                    content = user.username,
                    confirm = confirmText,
                    dismiss = cancelText,
                )
                if (result == ConfirmResult.Confirmed) {
                    viewModel.removeAccount(user)
                }
            }
        },
        onOpenAbout = { navigator.push(Route.About) },
        onSetAlwaysCode = viewModel::setAlwaysCode,
        onSetHideSign = viewModel::setHideSign,
        onSetHideCode = viewModel::setHideCode,
        onSetSignLocationMode = { index ->
            viewModel.setSignLocationMode(
                when (index) {
                    0 -> "ask"
                    1 -> "rel"
                    else -> "lst"
                }
            )
        },
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> SettingPagerMiuix(state, actions, bottomInnerPadding)
        UiMode.Material -> SettingPagerMaterial(state, actions, bottomInnerPadding)
    }
}
