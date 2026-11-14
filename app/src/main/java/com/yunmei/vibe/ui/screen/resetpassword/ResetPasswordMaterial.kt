package com.yunmei.vibe.ui.screen.resetpassword

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yunmei.vibe.R
import com.yunmei.vibe.ui.component.WarningLevel
import com.yunmei.vibe.ui.component.material.SegmentedCheckboxItem
import com.yunmei.vibe.ui.component.material.SegmentedColumn
import com.yunmei.vibe.ui.component.material.SegmentedListItem
import com.yunmei.vibe.ui.component.material.SegmentedTextField
import com.yunmei.vibe.ui.component.material.WarningCard

/**
 * 重设密码页的 Material 实现。
 *
 * 手机号只读（官方不允许在已登录态改绑定号码），验证码与「获取验证码」放在同一组，
 * 新密码单独一组并在下方常驻展示规则文案。
 */
@Composable
fun ResetPasswordMaterial(state: ResetPasswordUiState, actions: ResetPasswordActions) {
    val colorScheme = MaterialTheme.colorScheme
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val message = state.message
    Scaffold(
        containerColor = colorScheme.surfaceContainer,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.reset_password_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.reset_password_title),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.surfaceContainer,
                    scrolledContainerColor = colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
        ),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            if (message != null) {
                WarningCard(
                    message = message,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    level = WarningLevel.Error,
                )
            }

            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                content = listOf(
                    {
                        SegmentedTextField(
                            label = stringResource(R.string.reset_password_tel),
                            value = state.tel,
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        )
                    },
                    {
                        SegmentedTextField(
                            label = stringResource(R.string.reset_password_code),
                            value = state.code,
                            onValueChange = actions.onCodeChange,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    },
                    {
                        SegmentedListItem(
                            onClick = actions.onSendCode,
                            enabled = state.canSendCode,
                            headlineContent = {
                                Text(
                                    text = if (state.countdown > 0) {
                                        stringResource(R.string.reset_password_resend, state.countdown)
                                    } else {
                                        stringResource(R.string.reset_password_get_code)
                                    }
                                )
                            },
                            leadingContent = { Icon(Icons.Rounded.Sms, null) },
                        )
                    },
                    {
                        SegmentedTextField(
                            label = stringResource(R.string.reset_password_new_password),
                            value = state.password,
                            onValueChange = actions.onPasswordChange,
                            singleLine = true,
                            supportingContent = { Text(stringResource(R.string.reset_password_rule)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            visualTransformation = if (state.showPassword) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                        )
                    },
                    {
                        SegmentedCheckboxItem(
                            title = stringResource(R.string.reset_password_show),
                            checked = state.showPassword,
                            onCheckedChange = { actions.onToggleShowPassword() },
                        )
                    },
                ),
            )

            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                content = listOf {
                    SegmentedListItem(
                        onClick = actions.onSubmit,
                        enabled = state.canSubmit,
                        headlineContent = {
                            Text(
                                text = stringResource(
                                    if (state.submitting) {
                                        R.string.reset_password_submitting
                                    } else {
                                        R.string.reset_password_submit
                                    }
                                )
                            )
                        },
                        leadingContent = { Icon(Icons.Rounded.Check, null) },
                    )
                },
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
