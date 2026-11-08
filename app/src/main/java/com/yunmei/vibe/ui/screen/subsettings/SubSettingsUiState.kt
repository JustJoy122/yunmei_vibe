package com.yunmei.vibe.ui.screen.subsettings

import androidx.compose.runtime.Immutable
import com.yunmei.vibe.data.preferences.AppSettings

/**
 * 二级设置页「开锁」渲染状态。
 *
 * 三个开关写的是同一套 AppPreferences（quickConnect / autoConnect / autoExit），
 * 本次调整只是把它们的入口从「首页开门卡片」与「设置页开门与打卡分组」移到这里，存储与业务逻辑不变。
 */
@Immutable
data class UnlockSettingsUiState(
    val settings: AppSettings = AppSettings(),
)

@Immutable
data class UnlockSettingsActions(
    val onBack: () -> Unit,
    val onSetQuickConnect: (Boolean) -> Unit,
    val onSetAutoConnect: (Boolean) -> Unit,
    val onSetAutoExit: (Boolean) -> Unit,
)

/**
 * 二级设置页「取码与打卡」渲染状态。
 *
 * 三个条目对应 autoCode / alwaysCode / signLocationMode，同样只搬位置不改存储。
 */
@Immutable
data class CodeAndSignSettingsUiState(
    val settings: AppSettings = AppSettings(),
)

@Immutable
data class CodeAndSignSettingsActions(
    val onBack: () -> Unit,
    val onSetAutoCode: (Boolean) -> Unit,
    val onSetAlwaysCode: (Boolean) -> Unit,
    val onSetSignLocationMode: (Int) -> Unit,
)
