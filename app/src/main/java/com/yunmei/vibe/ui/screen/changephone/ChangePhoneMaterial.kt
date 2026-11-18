package com.yunmei.vibe.ui.screen.changephone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yunmei.vibe.R
import com.yunmei.vibe.ui.component.WarningLevel
import com.yunmei.vibe.ui.component.material.SegmentedColumn
import com.yunmei.vibe.ui.component.material.SegmentedListItem
import com.yunmei.vibe.ui.component.material.SegmentedTextField
import com.yunmei.vibe.ui.component.material.WarningCard

/**
 * 换绑手机号页的 Material 实现。
 *
 * 当前号码只读；新号码与验证码同一组，验证码右侧挂「获取验证码」；
 * 底部单独一组放「确认换绑」，页面末尾常驻安全提示。
 */
@Composable
fun ChangePhoneMaterial(state: ChangePhoneUiState, actions: ChangePhoneActions) {
    val colorScheme = MaterialTheme.colorScheme
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val message = state.message
    Scaffold(
        containerColor = colorScheme.surfaceContainer,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.change_phone_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.change_phone_title),
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
                            label = stringResource(R.string.change_phone_current_tel),
                            value = state.currentTel.ifBlank {
                                stringResource(R.string.change_phone_current_tel_missing)
                            },
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        )
                    },
                    {
                        SegmentedTextField(
                            label = stringResource(R.string.change_phone_new_tel),
                            value = state.newTel,
                            onValueChange = actions.onNewTelChange,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        )
                    },
                    {
                        // 验证码与「获取验证码」同一行：输入区占满剩余宽度，按钮固定在右侧。
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SegmentedTextField(
                                modifier = Modifier.weight(1f),
                                label = stringResource(R.string.change_phone_code),
                                value = state.code,
                                onValueChange = actions.onCodeChange,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            )
                            TextButton(
                                onClick = actions.onSendCode,
                                enabled = state.canSendCode,
                            ) {
                                Text(
                                    text = if (state.countdown > 0) {
                                        stringResource(R.string.change_phone_resend, state.countdown)
                                    } else {
                                        stringResource(R.string.change_phone_get_code)
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
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
                                        R.string.change_phone_submitting
                                    } else {
                                        R.string.change_phone_submit
                                    }
                                )
                            )
                        },
                        leadingContent = { Icon(Icons.Rounded.Check, null) },
                    )
                },
            )

            Text(
                text = stringResource(R.string.change_phone_notice),
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp),
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
