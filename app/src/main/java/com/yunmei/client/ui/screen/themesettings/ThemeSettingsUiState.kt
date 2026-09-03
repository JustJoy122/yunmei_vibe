package com.yunmei.client.ui.screen.themesettings

import androidx.compose.runtime.Immutable
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.yunmei.client.ui.screen.settings.SettingsUiState
import com.yunmei.client.ui.theme.ColorMode

/** 主题设置二级页状态：只包含主题、颜色与界面外观相关设置。 */
@Immutable
data class ThemeSettingsUiState(
    val uiState: SettingsUiState,
    val currentColorMode: ColorMode,
    val currentPaletteStyle: PaletteStyle,
    val currentColorSpec: ColorSpec.SpecVersion,
)

@Immutable
data class ThemeSettingsActions(
    val onBack: () -> Unit,
    val onSetThemeMode: (Int) -> Unit,
    val onSetColorMode: (ColorMode) -> Unit,
    val onSetMiuixMonet: (Boolean) -> Unit,
    val onSetAmoled: (Boolean) -> Unit,
    val onSetKeyColor: (Int) -> Unit,
    val onSetColorStyle: (String) -> Unit,
    val onSetColorSpec: (String) -> Unit,
    val onSetEnableBlur: (Boolean) -> Unit,
    val onSetEnableFloatingBottomBar: (Boolean) -> Unit,
    val onSetEnableFloatingBottomBarBlur: (Boolean) -> Unit,
    val onSetEnablePredictiveBack: (Boolean) -> Unit,
    val onSetPageScale: (Float) -> Unit,
)
