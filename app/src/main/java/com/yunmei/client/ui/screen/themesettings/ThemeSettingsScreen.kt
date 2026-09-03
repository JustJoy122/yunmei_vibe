package com.yunmei.client.ui.screen.themesettings

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.yunmei.client.YunMeiApp
import com.yunmei.client.ui.LocalUiMode
import com.yunmei.client.ui.UiMode
import com.yunmei.client.ui.navigation3.LocalNavigator
import com.yunmei.client.ui.theme.ColorMode
import com.yunmei.client.ui.viewmodel.SettingsViewModel

@Composable
fun ThemeSettingsScreen() {
    val navigator = LocalNavigator.current
    val activity = LocalActivity.current
    val viewModel = viewModel<SettingsViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val currentPaletteStyle = try {
        PaletteStyle.valueOf(uiState.colorStyle)
    } catch (_: Exception) {
        PaletteStyle.TonalSpot
    }
    val currentColorSpec = try {
        ColorSpec.SpecVersion.valueOf(uiState.colorSpec)
    } catch (_: Exception) {
        ColorSpec.SpecVersion.SPEC_2021
    }

    val state = ThemeSettingsUiState(
        uiState = uiState,
        currentColorMode = ColorMode.fromValue(uiState.themeMode),
        currentPaletteStyle = currentPaletteStyle,
        currentColorSpec = currentColorSpec,
    )

    val actions = ThemeSettingsActions(
        onBack = dropUnlessResumed { navigator.pop() },
        onSetThemeMode = viewModel::setThemeMode,
        onSetColorMode = viewModel::setColorMode,
        onSetMiuixMonet = viewModel::setMiuixMonet,
        onSetAmoled = viewModel::setAmoled,
        onSetKeyColor = viewModel::setKeyColor,
        onSetColorStyle = viewModel::setColorStyle,
        onSetColorSpec = viewModel::setColorSpec,
        onSetEnableBlur = viewModel::setEnableBlur,
        onSetEnableFloatingBottomBar = viewModel::setEnableFloatingBottomBar,
        onSetEnableFloatingBottomBarBlur = viewModel::setEnableFloatingBottomBarBlur,
        onSetEnablePredictiveBack = { enabled ->
            viewModel.setEnablePredictiveBack(enabled)
            YunMeiApp.app.enableOnBackInvokedCallback(enabled)
            activity?.recreate()
        },
        onSetPageScale = viewModel::setPageScale,
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> ThemeSettingsMiuix(state, actions)
        UiMode.Material -> ThemeSettingsMaterial(state, actions)
    }
}
