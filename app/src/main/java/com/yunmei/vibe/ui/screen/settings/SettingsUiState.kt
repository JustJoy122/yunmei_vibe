package com.yunmei.vibe.ui.screen.settings

import androidx.compose.runtime.Immutable
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec

import com.yunmei.vibe.data.preferences.AppSettings
import com.yunmei.vibe.ui.UiMode
import com.yunmei.vibe.ui.animation.predictiveback.PredictiveBackAnimation
import com.yunmei.vibe.ui.animation.predictiveback.PredictiveBackExitDirection

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
    val enableSwipeDismiss: Boolean = true,
    val pagerInterceptionMode: Int = 1,
    val predictiveBackAnimation: String = PredictiveBackAnimation.DEFAULT.value,
    val predictiveBackExitDirection: String = PredictiveBackExitDirection.DEFAULT.value,
    val enableBlur: Boolean = true,
    val enableFloatingBottomBar: Boolean = true,
    val enableFloatingBottomBarBlur: Boolean = true,
    val pageScale: Float = 1.0f,
    /** 加密存储是否可用（设置页内联提示用，避免 Composable 直接读 AppContainer）。 */
    val secureStoreAvailable: Boolean = true,
)

/**
 * 主设置页渲染状态：模板精简布局（检查更新/账号/界面风格/主题设置/发送日志/关于）
 * + 业务功能开关（按「开门行为 / 界面显示 / 实验功能」分组）。
 */
@Immutable
data class SettingsScreenState(
    val uiMode: String = UiMode.DEFAULT_VALUE,
    val checkUpdate: Boolean = true,

    val settings: AppSettings = AppSettings(),
    /** 加密存储是否可用：由 SettingsViewModel 提供，页面只消费该字段。 */
    val secureStoreAvailable: Boolean = true,
)

@Immutable
data class SettingsScreenActions(
    val onSetCheckUpdate: (Boolean) -> Unit,
    val onSetUiModeIndex: (Int) -> Unit,
    val onOpenTheme: () -> Unit,
    val onOpenAbout: () -> Unit,
    // 二级页入口：开锁（快速连接/自动开门/开门后自动退出）、取码与打卡（自动获取密码/首个账号/打卡定位）
    val onOpenUnlockSettings: () -> Unit,
    val onOpenCodeAndSignSettings: () -> Unit,
    // 仍留在设置页一级页的「外观与个性化」开关
    val onSetHideSign: (Boolean) -> Unit,
    val onSetHideCode: (Boolean) -> Unit,
)
