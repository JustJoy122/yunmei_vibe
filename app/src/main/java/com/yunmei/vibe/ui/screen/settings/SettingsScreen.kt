package com.yunmei.vibe.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yunmei.vibe.R
import com.yunmei.vibe.ui.LocalUiMode
import com.yunmei.vibe.ui.UiMode
import com.yunmei.vibe.ui.component.dialog.rememberConfirmDialog
import com.yunmei.vibe.ui.navigation.Navigator
import com.yunmei.vibe.ui.navigation.Route
import com.yunmei.vibe.ui.viewmodel.SettingsViewModel

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
        secureStoreAvailable = uiState.secureStoreAvailable,
        uiMode = uiState.uiMode,
        checkUpdate = uiState.checkUpdate,

        settings = functionalState.settings,
    )

    val removeAccountTitle = stringResource(R.string.settings_remove_account)
    val confirmText = stringResource(R.string.confirm)
    val cancelText = stringResource(R.string.cancel)

    val actions = remember(viewModel, navigator, scope, removeAccountTitle, confirmText, cancelText) {
        SettingsScreenActions(
        onSetCheckUpdate = viewModel::setCheckUpdate,
        onSetUiModeIndex = { index ->
            viewModel.setUiMode(if (index == 0) UiMode.Miuix.value else UiMode.Material.value)
        },
        onOpenTheme = { navigator.push(Route.ThemeSettings) },
        onOpenAbout = { navigator.push(Route.About) },
        onOpenUnlockSettings = { navigator.push(Route.UnlockSettings) },
        onOpenCodeAndSignSettings = { navigator.push(Route.CodeAndSignSettings) },
        onSetHideSign = viewModel::setHideSign,
        onSetHideCode = viewModel::setHideCode,
        )
    }

    when (LocalUiMode.current) {
        UiMode.Miuix -> SettingPagerMiuix(state, actions, bottomInnerPadding)
        UiMode.Material -> SettingPagerMaterial(state, actions, bottomInnerPadding)
    }
}