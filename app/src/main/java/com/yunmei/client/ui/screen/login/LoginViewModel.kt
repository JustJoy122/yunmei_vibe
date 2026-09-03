package com.yunmei.client.ui.screen.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yunmei.client.YunMeiApp
import com.yunmei.client.data.local.StoredUser
import com.yunmei.client.data.model.Lock
import com.yunmei.client.data.model.SchoolEntry
import com.yunmei.client.data.security.Md5
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel : ViewModel() {

    private val container get() = YunMeiApp.app.container

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _finished = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val finished: SharedFlow<Unit> = _finished.asSharedFlow()

    /** 当前选中的已保存账号的密码 MD5（输入未改则直接复用，与原项目一致）。 */
    private var storedPasswordMd5: String? = null
    private var passwordMd5: String? = null

    fun refresh() {
        _uiState.update { it.copy(savedUsers = container.accountStore.getAll()) }
    }

    fun onUsernameChange(value: String) = _uiState.update { it.copy(username = value) }

    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value) }

    fun onSaveCurrentChange(value: Boolean) = _uiState.update { it.copy(saveCurrent = value) }

    fun onToggleShowPassword(value: Boolean) = _uiState.update { it.copy(showPassword = value) }

    fun pickSavedUser(user: StoredUser) {
        storedPasswordMd5 = user.passwordMd5
        _uiState.update {
            it.copy(username = user.username, password = user.passwordMd5, message = null)
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    fun login() {
        val username = _uiState.value.username.trim()
        val password = _uiState.value.password
        if (username.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(message = "请输入账号与密码") }
            return
        }
        if (_uiState.value.loading) return

        // 与原项目一致：如果输入的就是已保存的 MD5，不再重复 MD5。
        val pwdMd5 = if (storedPasswordMd5 != null && password == storedPasswordMd5) {
            password
        } else {
            Md5.hex(password)
        }
        passwordMd5 = pwdMd5

        _uiState.update { it.copy(loading = true, message = null) }
        viewModelScope.launch {
            try {
                container.repository.login(username, pwdMd5)
                val schools = container.repository.fetchSchools()
                if (schools.isEmpty()) {
                    _uiState.update { it.copy(loading = false, message = "账号不属于任何一所学校，请检查后重新添加") }
                    return@launch
                }
                if (schools.size == 1) {
                    continueAfterSchool(schools[0])
                } else {
                    _uiState.update { it.copy(loading = false, schoolChoices = schools) }
                }
            } catch (error: Exception) {
                _uiState.update { it.copy(loading = false, message = error.message ?: "发生了未知错误，请尝试重新提交") }
            }
        }
    }

    fun pickSchool(school: SchoolEntry) {
        _uiState.update { it.copy(schoolChoices = null, loading = true) }
        viewModelScope.launch {
            continueAfterSchool(school)
        }
    }

    private suspend fun continueAfterSchool(school: SchoolEntry) {
        try {
            val locks = container.repository.fetchLocks(school)
            when {
                locks.isEmpty() -> {
                    // 未绑定任何门锁也不阻塞：直接放行进入主界面（游客/未绑定模式）。
                    finishLogin(lock = null, message = "登录成功，当前账号暂无门锁，可通过扫码添加")
                }

                locks.size == 1 -> finishLogin(locks[0], message = "门锁添加完成")
                else -> _uiState.update { it.copy(loading = false, lockChoices = locks) }
            }
        } catch (error: Exception) {
            // 拉取门锁失败同样放行：账号已登录，先进入主界面，不把用户卡在登录页。
            finishLogin(lock = null, message = "登录成功（获取门锁失败：" + (error.message ?: "未知错误") + "）")
        }
    }

    fun pickLock(lock: Lock) {
        _uiState.update { it.copy(lockChoices = null, loading = true) }
        viewModelScope.launch {
            finishLogin(lock, message = "门锁添加完成")
        }
    }

    /** 多学校/多门锁选择列表里的「暂不添加」：只保存账号并直接进入主界面。 */
    fun skipSelection() {
        _uiState.update { it.copy(schoolChoices = null, lockChoices = null, loading = true) }
        viewModelScope.launch {
            finishLogin(lock = null, message = "登录成功，可稍后在「门锁」页扫码添加")
        }
    }

    private fun finishLogin(lock: Lock?, message: String) {
        val username = _uiState.value.username.trim()
        val pwdMd5 = passwordMd5 ?: Md5.hex(_uiState.value.password)
        if (_uiState.value.saveCurrent) {
            container.accountStore.add(
                StoredUser(
                    username = username,
                    usernameMd5 = Md5.hex(username),
                    passwordMd5 = pwdMd5,
                )
            )
        }
        if (lock != null) {
            try {
                container.lockStore.add(lock)
            } catch (_: IllegalStateException) {
                // 同名门锁已存在：视为添加完成（重复登录场景）。
            }
            if (container.lockStore.getDefault() == null) {
                container.lockStore.setDefault(lock)
            }
        }
        _uiState.update { it.copy(loading = false, message = message) }
        _finished.tryEmit(Unit)
    }
}
