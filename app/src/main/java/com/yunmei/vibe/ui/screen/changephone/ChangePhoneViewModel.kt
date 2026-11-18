package com.yunmei.vibe.ui.screen.changephone

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
 * 换绑手机号 ViewModel。
 *
 * 只做三件事：回填当前号码、给新号码发验证码并跑倒计时、提交换绑。
 * 换绑成功后 Repository 会清掉会话，界面据此回登录页重新登录。
 */
class ChangePhoneViewModel : ViewModel() {

    private val container = YunMeiApp.app.container

    private val _uiState = MutableStateFlow(ChangePhoneUiState())
    val uiState: StateFlow<ChangePhoneUiState> = _uiState.asStateFlow()

    private var loaded = false

    /** 倒计时任务：重复点「获取验证码」时先取消旧的，保证只有一个计时器在跑。 */
    private var countdownJob: Job? = null

    private companion object {
        const val COUNTDOWN_SECONDS = 60
        const val CODE_LENGTH = 6
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
            _uiState.update { it.copy(currentTel = tel) }
        }
    }

    fun onNewTelChange(value: String) {
        // 手机号是纯数字；截断到 11 位，顺便把旧提示清掉。
        val digits = value.filter { it.isDigit() }.take(11)
        _uiState.update { it.copy(newTel = digits, message = null) }
    }

    fun onCodeChange(value: String) {
        val digits = value.filter { it.isDigit() }.take(CODE_LENGTH)
        _uiState.update { it.copy(code = digits, message = null) }
    }

    fun sendCode() {
        val state = _uiState.value
        if (state.sendingCode || state.countdown > 0) return
        if (!PHONE_RULE.matches(state.newTel)) {
            _uiState.update { it.copy(message = YunMeiApp.app.getString(R.string.change_phone_invalid_tel)) }
            return
        }
        if (state.newTel == state.currentTel) {
            _uiState.update { it.copy(message = YunMeiApp.app.getString(R.string.change_phone_same_tel)) }
            return
        }
        _uiState.update { it.copy(sendingCode = true, message = null) }
        viewModelScope.launch {
            try {
                val response = container.repository.requestPhoneCode(state.newTel)
                if (response.success) {
                    _uiState.update {
                        it.copy(
                            sendingCode = false,
                            message = YunMeiApp.app.getString(R.string.change_phone_code_sent),
                        )
                    }
                    startCountdown()
                } else {
                    _uiState.update {
                        it.copy(
                            sendingCode = false,
                            message = YunMeiApp.app.getString(
                                R.string.change_phone_code_failed,
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
                            R.string.change_phone_code_failed,
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
        if (!PHONE_RULE.matches(state.newTel)) {
            _uiState.update { it.copy(message = YunMeiApp.app.getString(R.string.change_phone_invalid_tel)) }
            return
        }
        if (state.newTel == state.currentTel) {
            _uiState.update { it.copy(message = YunMeiApp.app.getString(R.string.change_phone_same_tel)) }
            return
        }
        if (state.code.isBlank()) {
            _uiState.update { it.copy(message = YunMeiApp.app.getString(R.string.change_phone_missing_code)) }
            return
        }
        _uiState.update { it.copy(submitting = true, message = null) }
        viewModelScope.launch {
            try {
                container.repository.changePhone(state.newTel, state.code)
                countdownJob?.cancel()
                _uiState.update {
                    it.copy(
                        submitting = false,
                        countdown = 0,
                        success = true,
                        message = YunMeiApp.app.getString(R.string.change_phone_success),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        submitting = false,
                        message = YunMeiApp.app.getString(
                            R.string.change_phone_failed,
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
