package com.yunmei.vibe.ui.screen.themesettings

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.yunmei.vibe.YunMeiApp
import com.yunmei.vibe.ui.LocalUiMode
import com.yunmei.vibe.ui.UiMode
import com.yunmei.vibe.ui.navigation.LocalNavigator
import com.yunmei.vibe.ui.theme.ColorMode
import com.yunmei.vibe.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 开关动画播完后再应用 `setEnableOnBackInvokedCallback`：
 * 该隐藏 API 需要新的 Activity 窗口才生效，但同步 `recreate()` 会把模板 Switch 的切换动画直接打断，
 * 观感上就是「开关没有动画」。这里延后到动画结束再重建（时长对齐 M3 / Miuix Switch 的标准过渡）。
 */
private const val PREDICTIVE_BACK_APPLY_DELAY_MS = 320L

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
            // lifecycleScope 挂在 LifecycleOwner 上，这里显式收窄到 ComponentActivity。
            val host = activity as? ComponentActivity
            if (host == null) {
                YunMeiApp.app.enableOnBackInvokedCallback(enabled)
            } else {
                // 延迟重建挂在 Activity 的 lifecycleScope 上：不随本页离开组合而被取消，
                // 避免出现「设置已写入但没有重建窗口」的半状态。
                host.lifecycleScope.launch {
                    delay(PREDICTIVE_BACK_APPLY_DELAY_MS)
                    YunMeiApp.app.enableOnBackInvokedCallback(enabled)
                    if (!host.isFinishing && !host.isDestroyed) {
                        host.recreate()
                    }
                }
            }
        },
        onSetPredictiveBackAnimation = viewModel::setPredictiveBackAnimation,
        onSetPredictiveBackExitDirection = viewModel::setPredictiveBackExitDirection,
        onSetPageScale = viewModel::setPageScale,
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> ThemeSettingsMiuix(state, actions)
        UiMode.Material -> ThemeSettingsMaterial(state, actions)
    }
}
