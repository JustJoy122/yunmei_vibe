package com.yunmei.vibe.ui.screen.changephone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.unit.dp
import com.yunmei.vibe.R
import com.yunmei.vibe.ui.component.miuix.EditText
import com.yunmei.vibe.ui.component.miuix.WarningCard
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 换绑手机号页的 Miuix 实现：与 Material 版同样的分组，只换渲染构件。 */
@Composable
fun ChangePhoneMiuix(state: ChangePhoneUiState, actions: ChangePhoneActions) {
    val colorScheme = MiuixTheme.colorScheme
    val scrollBehavior = MiuixScrollBehavior()
    val message = state.message
    MiuixScaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.change_phone_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    MiuixIconButton(
                        onClick = actions.onBack,
                        modifier = Modifier.padding(start = 12.dp),
                    ) {
                        MiuixIcon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = stringResource(R.string.change_phone_title),
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
                    title = stringResource(R.string.change_phone_current_tel),
                    value = state.currentTel.ifBlank {
                        stringResource(R.string.change_phone_current_tel_missing)
                    },
                    onValueChange = {},
                    enabled = false,
                )
                EditText(
                    title = stringResource(R.string.change_phone_new_tel),
                    value = state.newTel,
                    onValueChange = actions.onNewTelChange,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                // 验证码与「获取验证码」同一行：输入区占满剩余宽度，按钮固定在右侧。
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EditText(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.change_phone_code),
                        value = state.code,
                        onValueChange = actions.onCodeChange,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
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
                                stringResource(R.string.change_phone_resend, state.countdown)
                            } else {
                                stringResource(R.string.change_phone_get_code)
                            },
                            style = MiuixTheme.textStyles.footnote1,
                        )
                    }
                }
            }

            Card(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth(),
            ) {
                BasicComponent(
                    title = stringResource(
                        if (state.submitting) {
                            R.string.change_phone_submitting
                        } else {
                            R.string.change_phone_submit
                        }
                    ),
                    enabled = state.canSubmit,
                    onClick = actions.onSubmit,
                )
            }

            Text(
                text = stringResource(R.string.change_phone_notice),
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                color = colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp),
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
