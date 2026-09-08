package com.yunmei.vibe.ui.viewmodel

import androidx.compose.runtime.Immutable
import com.yunmei.vibe.ui.UiMode
import com.yunmei.vibe.ui.theme.AppSettings

@Immutable
data class MainActivityUiState(
    val appSettings: AppSettings,
    val pageScale: Float,
    val enableBlur: Boolean,
    val enableFloatingBottomBar: Boolean,
    val enableFloatingBottomBarBlur: Boolean,
    val uiMode: UiMode,
)
