package com.yunmei.vibe.ui.screen.resetpassword

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.yunmei.vibe.R
import com.yunmei.vibe.ui.component.miuix.EditText
import top.yukonga.miuix.kmp.preference.SwitchPreference
import com.yunmei.vibe.ui.component.miuix.WarningCard
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 重设密码页的 Miuix 实现。
 *
 * 与 Material 版同样的信息分组，只是卡片换成 Miuix 的 [Card] / [EditText] / [BasicComponent]。
 */
@Composable
fun ResetPasswordMiuix(state: ResetPasswordUiState, actions: ResetPasswordActions) {
    val colorScheme = MiuixTheme.colorScheme
    val scrollBehavior = MiuixScrollBehavior()
    val message = state.message
    MiuixScaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.reset_password_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    MiuixIconButton(
                        onClick = actions.onBack,
                        modifier = Modifier.padding(start = 12.dp),
                    ) {
                        MiuixIcon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = stringResource(R.string.reset_password_title),
                            tint = colorScheme.onSurface,
                        )
                    }
                },
            )
        },
        popupHost = { },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 12.dp),
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            if (message != null) {
                WarningCard(
                    message = message,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth(),
                )
            }

            Card(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth(),
            ) {
                EditText(
                    title = stringResource(R.string.reset_password_tel),
                    value = state.tel,
                    onValueChange = {},
                    enabled = false,
                )
                // 验证码与「获取验证码」同一行：输入区占满剩余宽度，按钮固定在右侧。
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EditText(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.reset_password_code),
                        value = state.code,
                        onValueChange = actions.onCodeChange,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    // 主题强调色实心按钮：底色 primary、文字 onPrimary，尺寸压到输入行里不抢眼。
                    MiuixButton(
                        onClick = actions.onSendCode,
                        enabled = state.canSendCode,
                        modifier = Modifier.padding(start = 8.dp, end = 12.dp),
                        cornerRadius = 12.dp,
                        minWidth = 0.dp,
                        minHeight = 36.dp,
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = if (state.countdown > 0) {
                                stringResource(R.string.reset_password_resend, state.countdown)
                            } else {
                                stringResource(R.string.reset_password_get_code)
                            },
                            style = MiuixTheme.textStyles.footnote1,
                        )
                    }
                }
                // 密码规则单独一行小字，避免挤在输入框里。
                Column {
                    EditText(
                        title = stringResource(R.string.reset_password_new_password),
                        value = state.password,
                        onValueChange = actions.onPasswordChange,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = if (state.showPassword) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                    )
                    Text(
                        text = stringResource(R.string.reset_password_rule),
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                        color = colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(start = 16.dp, top = 2.dp, end = 16.dp, bottom = 8.dp),
                    )
                }
                SwitchPreference(
                    title = stringResource(R.string.reset_password_show),
                    checked = state.showPassword,
                    onCheckedChange = { actions.onToggleShowPassword() },
                )
            }

            Card(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth(),
            ) {
                BasicComponent(
                    title = stringResource(
                        if (state.submitting) {
                            R.string.reset_password_submitting
                        } else {
                            R.string.reset_password_submit
                        }
                    ),
                    enabled = state.canSubmit,
                    onClick = actions.onSubmit,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
