package com.yunmei.vibe.ui.screen.resetpassword

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yunmei.vibe.R
import com.yunmei.vibe.YunMeiApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 重设密码 ViewModel。
 *
 * 只做三件事：回填绑定手机号、发验证码并跑倒计时、提交新密码。
 * 密码规则与官方前端一致（8-16 位，必须同时含大写、小写与数字），校验放在提交前，
 * 避免为了一个格式错误白跑一次网络请求。
 */
class ResetPasswordViewModel : ViewModel() {

    private val container = YunMeiApp.app.container

    private val _uiState = MutableStateFlow(ResetPasswordUiState())
    val uiState: StateFlow<ResetPasswordUiState> = _uiState.asStateFlow()

    private var loaded = false

    /** 倒计时任务：重复点「获取验证码」时先取消旧的，保证只有一个计时器在跑。 */
    private var countdownJob: Job? = null

    private companion object {
        const val COUNTDOWN_SECONDS = 60
        const val CODE_LENGTH = 6

        /**
         * 官方前端的密码规则：8-16 位，至少各含一个大写、小写与数字。
         * 与 `loginuser/pub/findpwd` 的服务端校验保持同一条正则。
         */
        val PASSWORD_RULE = Regex("^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])[0-9a-zA-Z]{8,16}$")
    }

    fun load() {
        if (loaded) return
        loaded = true
        viewModelScope.launch {
            // 优先档案里的手机号；档案还没拉到时退回会话缓存（登录时写入的那份）。
            val fromProfile = runCatching { container.repository.cachedProfile() }.getOrNull()
                ?.userTel?.takeIf { it.isNotBlank() }
            val tel = fromProfile ?: runCatching { container.repository.cachedSession() }
                .getOrNull()?.userTel.orEmpty()
            _uiState.update { it.copy(tel = tel) }
        }
    }

    fun onCodeChange(value: String) {
        // 验证码是纯数字；顺手截断到 6 位，避免用户粘贴长串。
        val digits = value.filter { it.isDigit() }.take(CODE_LENGTH)
        _uiState.update { it.copy(code = digits, message = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, message = null) }
    }

    fun toggleShowPassword() {
        _uiState.update { it.copy(showPassword = !it.showPassword) }
    }

    fun sendCode() {
        val state = _uiState.value
        if (state.sendingCode || state.countdown > 0) return
        if (state.tel.isBlank()) {
            _uiState.update { it.copy(message = YunMeiApp.app.getString(R.string.reset_password_tel_missing)) }
            return
        }
        _uiState.update { it.copy(sendingCode = true, message = null) }
        viewModelScope.launch {
            try {
                val response = container.repository.requestPhoneCode(state.tel)
                if (response.success) {
                    _uiState.update {
                        it.copy(
                            sendingCode = false,
                            message = YunMeiApp.app.getString(R.string.reset_password_code_sent),
                        )
                    }
                    startCountdown()
                } else {
                    _uiState.update {
                        it.copy(
                            sendingCode = false,
                            message = YunMeiApp.app.getString(
                                R.string.reset_password_code_failed,
                                response.msg.orEmpty(),
                            ),
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        sendingCode = false,
                        message = YunMeiApp.app.getString(
                            R.string.reset_password_code_failed,
                            error.message.orEmpty(),
                        ),
                    )
                }
            }
        }
    }

    fun submit() {
        val state = _uiState.value
        if (state.submitting) return
        if (state.tel.isBlank()) {
            _uiState.update { it.copy(message = YunMeiApp.app.getString(R.string.reset_password_tel_missing)) }
            return
        }
        if (state.code.isBlank()) {
            _uiState.update { it.copy(message = YunMeiApp.app.getString(R.string.reset_password_missing_code)) }
            return
        }
        if (!PASSWORD_RULE.matches(state.password)) {
            _uiState.update { it.copy(message = YunMeiApp.app.getString(R.string.reset_password_invalid_password)) }
            return
        }
        _uiState.update { it.copy(submitting = true, message = null) }
        viewModelScope.launch {
            try {
                val response = container.repository.resetPassword(state.tel, state.code, state.password)
                if (response.success) {
                    countdownJob?.cancel()
                    _uiState.update {
                        it.copy(
                            submitting = false,
                            countdown = 0,
                            success = true,
                            message = YunMeiApp.app.getString(R.string.reset_password_success),
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            submitting = false,
                            message = YunMeiApp.app.getString(
                                R.string.reset_password_failed,
                                response.msg.orEmpty(),
                            ),
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        submitting = false,
                        message = YunMeiApp.app.getString(
                            R.string.reset_password_failed,
                            error.message.orEmpty(),
                        ),
                    )
                }
            }
        }
    }

    /**
     * 60 秒倒计时。
     *
     * 用 `viewModelScope` 里的单个 Job 每秒递减一次；页面销毁时 ViewModel 一起回收，
     * Job 随之取消，不需要额外清理。重复获取验证码会先 cancel 旧 Job 再起新的。
     */
    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            _uiState.update { it.copy(countdown = COUNTDOWN_SECONDS) }
            while (_uiState.value.countdown > 0) {
                delay(1_000L)
                _uiState.update { it.copy(countdown = (it.countdown - 1).coerceAtLeast(0)) }
            }
        }
    }

    override fun onCleared() {
        countdownJob?.cancel()
        super.onCleared()
    }
}
