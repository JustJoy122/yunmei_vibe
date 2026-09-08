package com.yunmei.vibe.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yunmei.vibe.R
import com.yunmei.vibe.YunMeiApp
import com.yunmei.vibe.data.ble.UnlockManager
import com.yunmei.vibe.data.local.StoredUser
import com.yunmei.vibe.data.model.Lock
import com.yunmei.vibe.ui.util.SystemInfo
import com.yunmei.vibe.ui.util.getAppVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface HomeEvent {
    /** 开门成功且开启「开门后自动退出」。 */
    data object AutoExitRequested : HomeEvent
}

class HomeViewModel : ViewModel() {

    private val container get() = YunMeiApp.app.container

    private fun str(resId: Int): String = YunMeiApp.app.getString(resId)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<HomeEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<HomeEvent> = _events.asSharedFlow()

    /** 已消费的自动开门触发值（去重，避免切页重组后重复开门）。 */
    private var handledAutoOpenTrigger = 0L

    private var currentLock: Lock? = null

    /** 最近一次加载协程（自动开门等场景需等待加载完成）。 */
    private var loadJob: kotlinx.coroutines.Job? = null

    init {
        container.unlockManager.onMacDiscovered = { lock, mac ->
            container.lockStore.setMac(lock.label, mac)
        }
    }

    fun refresh() {
        loadJob = viewModelScope.launch {
            loadLockAndSettings()
        }
    }

    private suspend fun loadLockAndSettings() {
        // 存储读取移到 IO 线程：底栏切换时页面首帧组合不应被主线程解密/IO 阻塞。
        val data = withContext(Dispatchers.IO) {
            val locks = container.lockStore.getAll()
            val default = container.lockStore.getDefault()
            val settings = container.appPreferences.settings.first()
            Triple(locks, default, settings)
        }
        val (locks, default, settings) = data
        currentLock = default ?: locks.firstOrNull()
        _uiState.update {
            // 开门过程中（如权限弹窗触发 resume）不重置进度与状态文字。
            val opening = it.isOpening
            it.copy(
                lock = currentLock,
                // 状态 Banner 的绿色判定：仅当用户在「门锁管理」中显式设置了默认门锁。
                defaultLock = default,
                lockCount = locks.size,
                showSignButton = !settings.hideSign,
                showCodeButton = !settings.hideCode,
                systemInfo = getAppVersion(YunMeiApp.app).let { v ->
                    SystemInfo(appVersion = "${v.versionName} (${v.versionCode})")
                },
                quickConnect = settings.quickConnect,
                settings = settings,
                progress = if (opening) it.progress else 0,
                statusText = if (opening) it.statusText else str(R.string.unlock_ready),
            )
        }
        // 自动获取密码：进入首页即取码（仅取一次）。
        if (settings.autoCode && currentLock != null && _uiState.value.code == null) {
            getCode()
        }
    }

    fun onAutoOpenTrigger(trigger: Long) {
        if (trigger <= 0 || trigger <= handledAutoOpenTrigger) return
        handledAutoOpenTrigger = trigger
        openDoor()
    }

    fun openDoor() {
        if (_uiState.value.isOpening) return
        viewModelScope.launch {
            // 首次进入或数据尚未加载完成时先加载，避免自动开门与刷新竞态。
            loadJob?.join()
            if (currentLock == null) {
                loadLockAndSettings()
            }
            doOpenDoor()
        }
    }

    private fun doOpenDoor() {
        val lock = currentLock ?: run {
            _uiState.update { it.copy(statusText = str(R.string.unlock_no_locks)) }
            return
        }
        if (_uiState.value.isOpening) return
        if (!lock.isUsable) {
            _uiState.update { it.copy(statusText = str(R.string.unlock_lock_unusable)) }
            return
        }
        _uiState.update { it.copy(isOpening = true, progress = 0, battery = null, statusText = str(R.string.unlock_preparing)) }
        container.unlockManager.openDoor(lock, _uiState.value.quickConnect, object : UnlockManager.Listener {
            override fun onProgress(percent: Int, message: String) {
                _uiState.update { it.copy(progress = percent, statusText = message) }
            }

            override fun onBattery(percent: Int) {
                _uiState.update { it.copy(battery = percent) }
            }

            override fun onSuccess() {
                _uiState.update { it.copy(isOpening = false, progress = 100, statusText = str(R.string.unlock_success)) }
                if (_uiState.value.settings.autoExit) {
                    _events.tryEmit(HomeEvent.AutoExitRequested)
                }
            }

            override fun onFailure(message: String) {
                _uiState.update { it.copy(isOpening = false, statusText = message) }
            }
        })
    }

    fun openDoorDenied() {
        _uiState.update { it.copy(isOpening = false, statusText = str(R.string.unlock_permission_denied)) }
    }

    fun setQuickConnect(value: Boolean) {
        _uiState.update { it.copy(quickConnect = value) }
        viewModelScope.launch {
            container.appPreferences.setQuickConnect(value)
        }
    }

    fun setAutoConnect(value: Boolean) = setSetting { container.appPreferences.setAutoConnect(value) }

    fun setAutoExit(value: Boolean) = setSetting { container.appPreferences.setAutoExit(value) }

    fun setAutoCode(value: Boolean) = setSetting { container.appPreferences.setAutoCode(value) }

    fun getCode() {
        val lock = currentLock ?: return
        if (_uiState.value.codeLoading) return
        _uiState.update { it.copy(codeLoading = true, codeError = null) }
        viewModelScope.launch {
            val user = findUser(lock)
            if (user == null) {
                _uiState.update { it.copy(codeLoading = false, codeError = str(R.string.unlock_no_account)) }
                return@launch
            }
            runCatching { container.repository.getLockPassword(user, lock) }
                .onSuccess { code ->
                    _uiState.update { it.copy(codeLoading = false, code = code, codeError = null) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(codeLoading = false, codeError = error.message ?: str(R.string.unlock_code_failed))
                    }
                }
        }
    }

    /** 打卡入口：按 signLocationMode 分派（与原项目 sigLoc 的 ask/lst/rel 一致）。 */
    fun sign() {
        val lock = currentLock ?: return
        if (findUser(lock) == null) {
            _uiState.update { it.copy(signMessage = str(R.string.unlock_no_account)) }
            return
        }
        viewModelScope.launch {
            val mode = _uiState.value.settings.signLocationMode
            when (mode) {
                "lst" -> useLastLocation()
                "rel" -> locateAndSign(save = true)
                else -> {
                    val last = container.appPreferences.getLastLocation()
                    _uiState.update { it.copy(signAsk = SignAskState(lastLocation = last)) }
                }
            }
        }
    }

    fun signLocationDenied() {
        _uiState.update { it.copy(signMessage = str(R.string.unlock_sign_location_denied)) }
    }

    fun resolveSignAsk(choice: SignAskChoice) {
        _uiState.update { it.copy(signAsk = null) }
        when (choice) {
            SignAskChoice.USE_LAST -> viewModelScope.launch { useLastLocation() }
            SignAskChoice.RELOCATE_SAVE -> viewModelScope.launch { locateAndSign(save = true) }
            SignAskChoice.LOCATE_ONLY -> viewModelScope.launch { locateAndSign(save = false) }
        }
    }

    fun dismissSignAsk() {
        _uiState.update { it.copy(signAsk = null) }
    }

    private fun findUser(lock: Lock): StoredUser? {
        return container.accountStore.getByUsernameMd5(lock.usernameMd5)
            ?: if (_uiState.value.settings.alwaysCode) {
                container.accountStore.getAll().firstOrNull()
            } else {
                null
            }
    }

    private suspend fun useLastLocation() {
        val last = container.appPreferences.getLastLocation()
        if (last.isBlank()) {
            _uiState.update { it.copy(signMessage = str(R.string.unlock_sign_last_missing)) }
        } else {
            doSign(last)
        }
    }

    private suspend fun locateAndSign(save: Boolean) {
        _uiState.update { it.copy(signing = true) }
        runCatching { container.locationProvider.getLocation() }
            .onSuccess { location ->
                if (save) {
                    container.appPreferences.setLastLocation(location)
                }
                doSign(location)
            }
            .onFailure { error ->
                _uiState.update { it.copy(signing = false, signMessage = error.message ?: str(R.string.unlock_sign_location_failed)) }
            }
    }

    private suspend fun doSign(location: String) {
        val lock = currentLock ?: return
        val user = findUser(lock) ?: return
        _uiState.update { it.copy(signing = true, signMessage = null) }
        runCatching { container.repository.sign(user, lock, location) }
            .onSuccess { message ->
                _uiState.update { it.copy(signing = false, signMessage = message) }
            }
            .onFailure { error ->
                _uiState.update { it.copy(signing = false, signMessage = error.message ?: str(R.string.unlock_sign_failed)) }
            }
    }

    private fun setSetting(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            loadLockAndSettings()
        }
    }
}
