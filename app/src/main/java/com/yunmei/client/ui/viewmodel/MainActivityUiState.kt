package com.yunmei.client.ui.viewmodel

import androidx.compose.runtime.Immutable
import com.yunmei.client.ui.UiMode
import com.yunmei.client.ui.theme.AppSettings

@Immutable
data class MainActivityUiState(
    val appSettings: AppSettings,
    val pageScale: Float,
    val enableBlur: Boolean,
    val enableFloatingBottomBar: Boolean,
    val enableFloatingBottomBarBlur: Boolean,
    val uiMode: UiMode,
)
