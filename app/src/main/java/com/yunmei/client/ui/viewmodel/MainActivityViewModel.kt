package com.yunmei.client.ui.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yunmei.client.YunMeiApp
import com.yunmei.client.data.repository.SettingsRepository
import com.yunmei.client.data.repository.SettingsRepositoryImpl
import com.yunmei.client.ui.UiMode
import com.yunmei.client.ui.theme.ThemeController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
