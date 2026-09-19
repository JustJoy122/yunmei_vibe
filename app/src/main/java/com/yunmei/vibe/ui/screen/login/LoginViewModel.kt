package com.yunmei.vibe.ui.screen.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yunmei.vibe.R
import com.yunmei.vibe.YunMeiApp
import com.yunmei.vibe.data.local.StoredUser
import com.yunmei.vibe.data.model.Lock
import com.yunmei.vibe.data.model.SchoolEntry
import com.yunmei.vibe.data.repository.YunMeiAuthException
import com.yunmei.vibe.data.security.Md5
import com.yunmei.vibe.ui.component.UiMessage
import com.yunmei.vibe.ui.component.UiMessageBus
import com.yunmei.vibe.ui.component.UiMessageTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginViewModel : ViewModel() {

    private val container get() = YunMeiApp.app.container

    private fun str(resId: Int): String = YunMeiApp.app.getString(resId)

    private fun str(resId: Int, vararg args: Any): String = YunMeiApp.app.getString(resId, *args)

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _finished = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val finished: SharedFlow<Unit> = _finished.asSharedFlow()

    /** 当前选中的已保存账号的密码 MD5（输入未改则直接复用，与原项目一致）。 */
    private var storedPasswordMd5: String? = null
    private var passwordMd5: String? = null

    fun refresh() {
        // 已保存账号来自加密存储（EncryptedSharedPreferences + JSON 解码），切到 IO 线程读取。
        viewModelScope.launch {
            val users = withContext(Dispatchers.IO) { container.accountStore.getAll() }
            _uiState.update { it.copy(savedUsers = users) }
        }
    }

    fun onUsernameChange(value: String) = _uiState.update { it.copy(username = value) }

    fun onPasswordChange(value: String) = _uiState.update {
        // 手动输入密码即视为改用新凭证：丢弃「使用已保存凭证」标记（保存的 MD5 仍留作比对）。
        it.copy(password = value, usingSavedCredential = false)
    }

    fun onSaveCurrentChange(value: Boolean) = _uiState.update { it.copy(saveCurrent = value) }

    fun onToggleShowPassword(value: Boolean) = _uiState.update { it.copy(showPassword = value) }

    /**
     * 选择已保存账号。
     *
     * 已保存账号只保存密码 **MD5**（`StoredUser.passwordMd5`，非明文、非 Token），
     * 因此**不能**把它回填进密码输入框——否则「显示密码」会把这串 MD5 当作明文展示，
     * 用户手动改动其中任一字符还会被再次 MD5，导致登录失败。
     * 这里改为：输入框留空 + 标记 [LoginUiState.usingSavedCredential]，登录时直接复用该 MD5。
     */
    fun pickSavedUser(user: StoredUser) {
        storedPasswordMd5 = user.passwordMd5
        _uiState.update {
            it.copy(
                username = user.username,
                password = "",
                usingSavedCredential = true,
                showPassword = false,
                message = null,
            )
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    fun login() {
        val username = _uiState.value.username.trim()
        val password = _uiState.value.password

        // 输入框为空且存在已保存凭证时，直接复用保存的 MD5（已保存账号场景，无需重复输入密码）；
        // 否则按原逻辑：输入的就是已保存 MD5 时不再重复 MD5，其余情况对明文做 MD5。
        val pwdMd5: String? = if (password.isNotBlank()) {
            if (storedPasswordMd5 != null && password == storedPasswordMd5) password else Md5.hex(password)
        } else {
            storedPasswordMd5
        }

        if (username.isBlank() || pwdMd5 == null) {
            showInPageMessage(str(R.string.login_error_empty_input))
            return
        }
        if (_uiState.value.loading) return

        passwordMd5 = pwdMd5

        _uiState.update { it.copy(loading = true, message = null) }
        viewModelScope.launch {
            try {
                container.repository.login(username, pwdMd5)
                val schools = container.repository.fetchSchools()
                if (schools.isEmpty()) {
                    showInPageMessage(str(R.string.login_error_no_school))
                    return@launch
                }
                if (schools.size == 1) {
                    continueAfterSchool(schools[0])
                } else {
                    _uiState.update { it.copy(loading = false, schoolChoices = schools) }
                }
            } catch (_: YunMeiAuthException) {
                // 服务端明确拒绝：账号或密码错误，给中文友好提示，不暴露服务端英文原文。
                showInPageMessage(str(R.string.login_error_credentials))
            } catch (error: Exception) {
                // 预期外错误（网络异常 / HTTP 500 / JSON 解析失败等）：保留原始信息并加中文前缀。
                showInPageMessage(str(R.string.login_error_failed, error.detailText()))
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
                // 未绑定任何门锁也不阻塞：直接放行进入主界面（游客/未绑定模式）。
                // 无门锁不是错误，用 Warning（tertiary）语义色提示，且必须是跨页面提示。
                locks.isEmpty() -> finishLogin(
                    lock = null,
                    text = str(R.string.login_result_no_lock),
                    tone = UiMessageTone.Warning,
                )

                locks.size == 1 -> finishLogin(
                    lock = locks[0],
                    text = str(R.string.login_result_lock_added),
                    tone = UiMessageTone.Success,
                )

                else -> _uiState.update { it.copy(loading = false, lockChoices = locks) }
            }
        } catch (error: Exception) {
            // 拉取门锁失败同样放行：账号已登录，先进入主界面，不把用户卡在登录页。
            finishLogin(
                lock = null,
                text = str(R.string.login_result_locks_failed, error.detailText()),
                tone = UiMessageTone.Warning,
            )
        }
    }

    fun pickLock(lock: Lock) {
        _uiState.update { it.copy(lockChoices = null, loading = true) }
        viewModelScope.launch {
            finishLogin(
                lock = lock,
                text = str(R.string.login_result_lock_added),
                tone = UiMessageTone.Success,
            )
        }
    }

    /** 多学校/多门锁选择列表里的「暂不添加」：只保存账号并直接进入主界面。 */
    fun skipSelection() {
        _uiState.update { it.copy(schoolChoices = null, lockChoices = null, loading = true) }
        viewModelScope.launch {
            finishLogin(
                lock = null,
                text = str(R.string.login_skip_selection_hint),
                tone = UiMessageTone.Warning,
            )
        }
    }

    /**
     * 登录流程收尾：保存账号 → 可选添加门锁 → 发送**跨页面提示** → 通知登录页完成导航。
     *
     * 提示不发到登录页自己的 `LoginUiState.message`（那是页面内提示，会随登录页被 replaceAll
     * 掉而一闪而过），而是发到 [UiMessageBus]：由主界面的全局提示宿主在返回后弹出，用户能看到。
     */
    private fun finishLogin(lock: Lock?, text: String, tone: UiMessageTone) {
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
        _uiState.update { it.copy(loading = false, message = null) }
        UiMessageBus.send(UiMessage(text = text, tone = tone))
        _finished.tryEmit(Unit)
    }

    /** 页面内提示（登录页顶部错误条）：仅用于尚未离开登录页的错误/信息。 */
    private fun showInPageMessage(message: String) {
        _uiState.update { it.copy(loading = false, message = message) }
    }

    /**
     * 预期外错误的可读摘要：优先异常 message，缺失时退回异常类名；
     * 折叠换行并截断，避免把整段 JSON 原文（含花括号）糊在提示里。
     */
    private fun Throwable.detailText(): String {
        val raw = message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName
        val oneLine = raw.replace(Regex("\\s+"), " ").trim()
        return if (oneLine.length > MAX_DETAIL_LENGTH) {
            oneLine.take(MAX_DETAIL_LENGTH) + "…"
        } else {
            oneLine
        }
    }

    private companion object {
        const val MAX_DETAIL_LENGTH = 120
    }
}
