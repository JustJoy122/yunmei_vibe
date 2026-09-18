package com.yunmei.vibe.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yunmei.vibe.YunMeiApp
import com.yunmei.vibe.data.local.StoredUser
import com.yunmei.vibe.data.preferences.AppSettings
import com.yunmei.vibe.data.repository.SettingsRepository
import com.yunmei.vibe.data.repository.SettingsRepositoryImpl
import com.yunmei.vibe.ui.screen.settings.SettingsUiState
import com.yunmei.vibe.ui.theme.ColorMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(
    private val repo: SettingsRepository = SettingsRepositoryImpl()
) : ViewModel() {

    private val container get() = YunMeiApp.app.container

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /** 功能设置（DataStore）与已保存账号。 */
    private val _functionalState = MutableStateFlow(FunctionalSettingsState())
    val functionalState: StateFlow<FunctionalSettingsState> = _functionalState.asStateFlow()

    /** 上报对象编辑缓冲（点保存才写入）。 */

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            // DataStore/加密存储读取移到 IO 线程，避免设置页首次组合被主线程 IO 阻塞。
            val functional = withContext(Dispatchers.IO) {
                val settings = container.appPreferences.settings.firstOrNull() ?: AppSettings()
                val accounts = container.accountStore.getAll()
                settings to accounts
            }
            _uiState.update {
                it.copy(
                    uiMode = repo.uiMode,
                    checkUpdate = repo.checkUpdate,
                    themeMode = repo.themeMode,
                    miuixMonet = repo.miuixMonet,
                    amoled = repo.amoled,
                    keyColor = repo.keyColor,
                    colorStyle = repo.colorStyle,
                    colorSpec = repo.colorSpec,
                    enablePredictiveBack = repo.enablePredictiveBack,
                    predictiveBackAnimation = repo.predictiveBackAnimation,
                    predictiveBackExitDirection = repo.predictiveBackExitDirection,
                    enableBlur = repo.enableBlur,
                    enableFloatingBottomBar = repo.enableFloatingBottomBar,
                    enableFloatingBottomBarBlur = repo.enableFloatingBottomBarBlur,
                    pageScale = repo.pageScale,
                )
            }
            val (settings, accounts) = functional
            _functionalState.update {
                it.copy(settings = settings, accounts = accounts)
            }
        }
    }

    fun setCheckUpdate(enabled: Boolean) {
        repo.checkUpdate = enabled
        _uiState.update { it.copy(checkUpdate = enabled) }
    }

    fun setUiMode(mode: String) {
        val oldMode = repo.uiMode
        val currentThemeMode = repo.themeMode

        // 与模板 ThemeController 一致：切换风格时做 Monet 模式换算，避免深浅色跳变。
        val newThemeMode = when (oldMode) {
            "material" if mode == "miuix" -> {
                val colorMode = ColorMode.fromValue(currentThemeMode)
                val baseMode = if (colorMode == ColorMode.DARK_AMOLED) 2 else currentThemeMode
                if (repo.miuixMonet && !colorMode.isMonet) {
                    ColorMode.fromValue(baseMode).toMonetMode()
                } else if (!repo.miuixMonet && colorMode.isMonet) {
                    ColorMode.fromValue(baseMode).toNonMonetMode()
                } else baseMode
            }

            "miuix" if mode == "material" -> {
                val colorMode = ColorMode.fromValue(currentThemeMode)
                if (colorMode.isMonet) {
                    colorMode.toNonMonetMode()
                } else currentThemeMode
            }

            else -> currentThemeMode
        }

        repo.uiMode = mode
        repo.themeMode = newThemeMode
        _uiState.update { it.copy(uiMode = mode, themeMode = newThemeMode) }
    }

    fun setThemeMode(mode: Int) {
        val currentUiMode = repo.uiMode
        val effectiveMode = if (currentUiMode == "miuix" && _uiState.value.miuixMonet) {
            mode + 3
        } else {
            mode
        }
        repo.themeMode = effectiveMode
        _uiState.update { it.copy(themeMode = effectiveMode) }
    }

    fun setMiuixMonet(enabled: Boolean) {
        val colorMode = ColorMode.fromValue(repo.themeMode)
        val newThemeMode = if (enabled) {
            if (!colorMode.isMonet) colorMode.toMonetMode() else repo.themeMode
        } else {
            if (colorMode.isMonet) colorMode.toNonMonetMode() else repo.themeMode
        }
        repo.miuixMonet = enabled
        repo.themeMode = newThemeMode
        _uiState.update { it.copy(miuixMonet = enabled, themeMode = newThemeMode) }
    }

    fun setColorMode(mode: ColorMode) {
        repo.themeMode = mode.value
        _uiState.update { it.copy(themeMode = mode.value) }
    }

    /** AMOLED 纯黑背景：深色模式下的独立开关（Material 风格生效；Miuix 无纯黑方案，仅保存偏好）。 */
    fun setAmoled(enabled: Boolean) {
        repo.amoled = enabled
        _uiState.update { it.copy(amoled = enabled) }
    }

    fun setKeyColor(color: Int) {
        repo.keyColor = color
        _uiState.update { it.copy(keyColor = color) }
    }

    fun setColorStyle(style: String) {
        repo.colorStyle = style
        _uiState.update { it.copy(colorStyle = style) }
    }

    fun setColorSpec(spec: String) {
        repo.colorSpec = spec
        _uiState.update { it.copy(colorSpec = spec) }
    }

    fun setEnablePredictiveBack(enabled: Boolean) {
        repo.enablePredictiveBack = enabled
        _uiState.update { it.copy(enablePredictiveBack = enabled) }
    }

    /** 返回动画档位（aosp / miuix / scale / ksu_classic），取值与 InstallerX Revived 一致。 */
    fun setPredictiveBackAnimation(animation: String) {
        repo.predictiveBackAnimation = animation
        _uiState.update { it.copy(predictiveBackAnimation = animation) }
    }

    /** 返回方向（follow_gesture / always_right / always_left），仅「缩放」档生效。 */
    fun setPredictiveBackExitDirection(direction: String) {
        repo.predictiveBackExitDirection = direction
        _uiState.update { it.copy(predictiveBackExitDirection = direction) }
    }

    fun setPageScale(scale: Float) {
        repo.pageScale = scale
        _uiState.update { it.copy(pageScale = scale) }
    }

    fun setEnableBlur(enabled: Boolean) {
        repo.enableBlur = enabled
        _uiState.update { it.copy(enableBlur = enabled) }
    }

    fun setEnableFloatingBottomBar(enabled: Boolean) {
        repo.enableFloatingBottomBar = enabled
        _uiState.update { it.copy(enableFloatingBottomBar = enabled) }
    }

    fun setEnableFloatingBottomBarBlur(enabled: Boolean) {
        repo.enableFloatingBottomBarBlur = enabled
        _uiState.update { it.copy(enableFloatingBottomBarBlur = enabled) }
    }

    // ---- 功能设置（原项目 storage 偏好，行为与其一致） ----
    // 自动开门/自动退出/自动获取密码在首页开门卡片内（HomeViewModel 直接写入）；
    // 快速连接已从首页迁到设置页「通用」分组，仍写同一个 quick_connect 偏好。

    fun setQuickConnect(value: Boolean) = setSetting { container.appPreferences.setQuickConnect(value) }

    fun setAlwaysCode(value: Boolean) = setSetting { container.appPreferences.setAlwaysCode(value) }

    fun setHideSign(value: Boolean) = setSetting { container.appPreferences.setHideSign(value) }

    fun setHideCode(value: Boolean) = setSetting { container.appPreferences.setHideCode(value) }

    fun setSignLocationMode(value: String) = setSetting { container.appPreferences.setSignLocationMode(value) }



    fun removeAccount(user: StoredUser) {
        container.accountStore.removeByUsernameMd5(user.usernameMd5)
        refresh()
    }

    private fun setSetting(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            refresh()
        }
    }
}

data class FunctionalSettingsState(
    val settings: AppSettings = AppSettings(),
    val accounts: List<StoredUser> = emptyList(),
)
