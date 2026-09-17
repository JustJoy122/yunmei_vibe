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
    /** 「预测性返回手势」开关；关闭即不启用返回动画。 */
    val predictiveBackEnabled: Boolean,
    /** 返回动画档位（取值见 PredictiveBackAnimation）。 */
    val predictiveBackAnimation: String,
    /** 返回方向（取值见 PredictiveBackExitDirection）。 */
    val predictiveBackExitDirection: String,
)
