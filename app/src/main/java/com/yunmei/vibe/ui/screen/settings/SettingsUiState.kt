package com.yunmei.vibe.ui.screen.settings

import androidx.compose.runtime.Immutable
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.yunmei.vibe.data.local.StoredUser
import com.yunmei.vibe.data.preferences.AppSettings
import com.yunmei.vibe.ui.UiMode

@Immutable
data class SettingsUiState(
    val uiMode: String = UiMode.DEFAULT_VALUE,
    val checkUpdate: Boolean = true,
    val themeMode: Int = 0,
    val miuixMonet: Boolean = false,
    val amoled: Boolean = false,
    val keyColor: Int = 0,
    val colorStyle: String = PaletteStyle.TonalSpot.name,
    val colorSpec: String = ColorSpec.SpecVersion.Default.name,
    val enablePredictiveBack: Boolean = false,
    val enableBlur: Boolean = true,
    val enableFloatingBottomBar: Boolean = true,
    val enableFloatingBottomBarBlur: Boolean = true,
    val pageScale: Float = 1.0f,
)

/**
 * 主设置页渲染状态：模板精简布局（检查更新/账号/界面风格/主题设置/发送日志/关于）
 * + 业务功能开关（按「开门行为 / 界面显示 / 实验功能」分组）。
 */
@Immutable
data class SettingsScreenState(
    val uiMode: String = UiMode.DEFAULT_VALUE,
    val checkUpdate: Boolean = true,
    val accounts: List<StoredUser> = emptyList(),
    val settings: AppSettings = AppSettings(),
)

@Immutable
data class SettingsScreenActions(
    val onSetCheckUpdate: (Boolean) -> Unit,
    val onSetUiModeIndex: (Int) -> Unit,
    val onOpenTheme: () -> Unit,
    val onLogin: () -> Unit,
    val onRemoveAccount: (StoredUser) -> Unit,
    val onOpenAbout: () -> Unit,
    // 业务功能（原项目 storage 偏好）：
    // 快速连接/自动开门/自动退出/自动获取密码已迁至首页开门卡片内。
    val onSetAlwaysCode: (Boolean) -> Unit,
    val onSetHideSign: (Boolean) -> Unit,
    val onSetHideCode: (Boolean) -> Unit,
    val onSetSignLocationMode: (Int) -> Unit,
)
