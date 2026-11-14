package com.yunmei.vibe.ui.screen.resetpassword

import androidx.compose.runtime.Immutable

/**
 * 重设密码页状态。
 *
 * [tel] 来自已登录会话（官方接口不给改手机号，所以这里只读展示），
 * [countdown] 是「获取验证码」按钮的剩余秒数，0 表示可以再次获取。
 */
@Immutable
data class ResetPasswordUiState(
    val tel: String = "",
    val code: String = "",
    val password: String = "",
    val showPassword: Boolean = false,
    val sendingCode: Boolean = false,
    val submitting: Boolean = false,
    val countdown: Int = 0,
    val message: String? = null,
    /** 改密成功：页面据此退回上一级。 */
    val success: Boolean = false,
) {
    /** 手机号存在、当前不在倒计时、也没有正在发送的请求时才允许点「获取验证码」。 */
    val canSendCode: Boolean
        get() = tel.isNotBlank() && countdown == 0 && !sendingCode

    val canSubmit: Boolean
        get() = tel.isNotBlank() && code.isNotBlank() && password.isNotBlank() && !submitting
}

@Immutable
data class ResetPasswordActions(
    val onBack: () -> Unit,
    val onCodeChange: (String) -> Unit,
    val onPasswordChange: (String) -> Unit,
    val onToggleShowPassword: () -> Unit,
    val onSendCode: () -> Unit,
    val onSubmit: () -> Unit,
)
