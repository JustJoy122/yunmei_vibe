package com.yunmei.vibe.ui.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yunmei.vibe.YunMeiApp
import com.yunmei.vibe.data.repository.SettingsRepository
import com.yunmei.vibe.data.repository.SettingsRepositoryImpl
import com.yunmei.vibe.ui.UiMode
import com.yunmei.vibe.ui.theme.ThemeController
import com.yunmei.vibe.ui.util.LatestVersionInfo
import com.yunmei.vibe.ui.util.checkNewVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivityViewModel(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val prefs = YunMeiApp.app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val settingRepo: SettingsRepository = SettingsRepositoryImpl()
    private val mainPageState = MainPageState(savedStateHandle)
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key in observedKeys) {
            _uiState.value = readUiState()
        }
    }

    private val _uiState = MutableStateFlow(readUiState())
    val uiState: StateFlow<MainActivityUiState> = _uiState.asStateFlow()
    val selectedMainPage: StateFlow<Int> = mainPageState.selectedPage

    /** 自动开门请求（autoConnect）：主界面加载完成后触发一次，同一进程内只消费一次。 */
    private val _autoOpenTrigger = MutableStateFlow(0L)
    val autoOpenTrigger: StateFlow<Long> = _autoOpenTrigger.asStateFlow()
    private var autoOpenConsumed = false

    /** 应用启动自检发现的可用更新（仅 Release 版本会写入，Debug 恒为 null）。 */
    private val _latestVersion = MutableStateFlow<LatestVersionInfo?>(null)
    val latestVersion: StateFlow<LatestVersionInfo?> = _latestVersion.asStateFlow()
    private var updateCheckStarted = false

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun onCleared() {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
        super.onCleared()
    }

    fun setSelectedMainPage(page: Int) {
        mainPageState.updateSelectedPage(page)
    }

    /**
     * 应用启动自检更新（对齐 KernelSU-Style-UI-Kit 的 `HomeViewModel.refresh()`）：
     * 读取设置页「检查更新」开关（`check_update`）后执行一次 `checkNewVersion()`；
     * 仅当存在更高的正式 Release 版本时才写入状态，交由壳层弹窗提示。
     */
    fun checkUpdateOnStartup() {
        if (updateCheckStarted) return
        updateCheckStarted = true
        viewModelScope.launch {
            if (!settingRepo.checkUpdate) return@launch
            val info = withContext(Dispatchers.IO) { checkNewVersion() }
            if (info.hasUpdate) {
                _latestVersion.value = info
            }
        }
    }

    fun dismissUpdate() {
        _latestVersion.value = null
    }

    fun requestAutoOpen() {
        if (autoOpenConsumed) return
        viewModelScope.launch {
            if (autoOpenConsumed) return@launch
            autoOpenConsumed = true
            val settings = YunMeiApp.app.container.appPreferences.settings.first()
            val locks = YunMeiApp.app.container.lockStore.getAll()
            if (settings.autoConnect && locks.isNotEmpty()) {
                mainPageState.updateSelectedPage(MainPagerConfig.PAGE_HOME)
                _autoOpenTrigger.value += 1
            }
        }
    }

    private fun readUiState(): MainActivityUiState {
        return MainActivityUiState(
            appSettings = ThemeController.getAppSettings(YunMeiApp.app),
            pageScale = settingRepo.pageScale,
            enableBlur = settingRepo.enableBlur,
            enableFloatingBottomBar = settingRepo.enableFloatingBottomBar,
            enableFloatingBottomBarBlur = settingRepo.enableFloatingBottomBarBlur,
            uiMode = UiMode.fromValue(settingRepo.uiMode),
            predictiveBackEnabled = settingRepo.enablePredictiveBack,
            predictiveBackAnimation = settingRepo.predictiveBackAnimation,
            predictiveBackExitDirection = settingRepo.predictiveBackExitDirection,
        )
    }

    private companion object {
        val observedKeys = setOf(
            "color_mode",
            "key_color",
            "color_style",
            "color_spec",
            "miuix_monet",
            "amoled",
            "check_update",
            "enable_blur",
            "enable_floating_bottom_bar",
            "enable_floating_bottom_bar_blur",
            "enable_predictive_back",
            "predictive_back_animation",
            "predictive_back_exit_direction",
            "page_scale",
            "ui_mode",
        )
    }
}

private const val SELECTED_MAIN_PAGE_KEY = "selected_main_page"

private class MainPageState(
    private val savedStateHandle: SavedStateHandle,
) {
    init {
        val savedPage = savedStateHandle.get<Int>(SELECTED_MAIN_PAGE_KEY) ?: 0
        savedStateHandle[SELECTED_MAIN_PAGE_KEY] = MainPagerConfig.coercePage(savedPage)
    }

    val selectedPage: StateFlow<Int> = savedStateHandle.getStateFlow(SELECTED_MAIN_PAGE_KEY, 0)

    fun updateSelectedPage(page: Int) {
        savedStateHandle[SELECTED_MAIN_PAGE_KEY] = MainPagerConfig.coercePage(page)
    }
}

object MainPagerConfig {
    const val PAGE_COUNT = 3
    const val PAGE_HOME = 0
    const val PAGE_LOCKS = 1
    const val PAGE_SETTINGS = 2
    const val LAST_PAGE_INDEX = PAGE_COUNT - 1

    fun coercePage(page: Int): Int = page.coerceIn(0, LAST_PAGE_INDEX)
}
