package com.yunmei.vibe.ui.screen.changephone

import androidx.compose.runtime.Immutable

/**
 * 换绑手机号页状态。
 *
 * [currentTel] 来自已登录会话，只读展示；[newTel] 是本次要绑定的新号码。
 * [countdown] 是「获取验证码」按钮的剩余秒数，0 表示可以再次获取。
 */
@Immutable
data class ChangePhoneUiState(
    val currentTel: String = "",
    val newTel: String = "",
    val code: String = "",
    val sendingCode: Boolean = false,
    val submitting: Boolean = false,
    val countdown: Int = 0,
    val message: String? = null,
    /** 换绑成功：页面据此回登录页。 */
    val success: Boolean = false,
) {
    /** 新号码格式正确、不在倒计时、也没有正在发送的请求时才允许点「获取验证码」。 */
    val canSendCode: Boolean
        get() = PHONE_RULE.matches(newTel) && countdown == 0 && !sendingCode

    val canSubmit: Boolean
        get() = PHONE_RULE.matches(newTel) && code.isNotBlank() && !submitting
}

/**
 * 手机号规则：11 位数字且以 1 开头。
 *
 * 与官方 `phonecode/pub/getcode` 的入参要求一致；发验证码前先本地挡掉明显错误，
 * 避免白跑一次网络请求（服务端对错误号码只回一句泛化提示）。
 */
val PHONE_RULE = Regex("^1\\d{10}$")

@Immutable
data class ChangePhoneActions(
    val onBack: () -> Unit,
    val onNewTelChange: (String) -> Unit,
    val onCodeChange: (String) -> Unit,
    val onSendCode: () -> Unit,
    val onSubmit: () -> Unit,
)
