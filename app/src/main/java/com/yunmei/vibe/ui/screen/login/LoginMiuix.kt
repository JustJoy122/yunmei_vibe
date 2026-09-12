package com.yunmei.vibe.ui.screen.login

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.Person
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.yunmei.vibe.R
import com.yunmei.vibe.ui.component.miuix.EditText
import com.yunmei.vibe.ui.component.miuix.WarningCard
import com.yunmei.vibe.ui.navigation3.LocalNavigator
import com.yunmei.vibe.ui.navigation3.Route
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

@Composable
fun LoginScreenMiuix(
    state: LoginUiState,
    actions: LoginActions,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val navigator = LocalNavigator.current
    val backToMain = {
        // 返回：多级栈出栈回主界面；若登录页孤立在栈底（旧状态恢复等异常），
        // 直接强清栈回主界面（等价 FLAG_ACTIVITY_NEW_TASK|CLEAR_TASK），绝不卡死。
        if (navigator.backStackSize() > 1) {
            navigator.pop()
        } else {
            navigator.replaceAll(listOf(Route.Main))
        }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.login_title),
                navigationIcon = {
                    IconButton(
                        onClick = backToMain,
                        modifier = Modifier.padding(start = 12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.lock_detail_back),
                            tint = colorScheme.onSurface,
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = 12.dp),
                contentPadding = innerPadding,
                overscrollEffect = null,
            ) {
                item {
                    Column {
                        state.message?.let { message ->
                            WarningCard(
                                message = message,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }

                        state.schoolChoices?.let { schools ->
                            Card(
                                modifier = Modifier
                                    .padding(top = 12.dp)
                                    .fillMaxWidth(),
                            ) {
                                Column {
                                    Text(
                                        text = stringResource(R.string.login_choose_school),
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        color = colorScheme.onSurfaceVariantSummary,
                                    )
                                    schools.forEach { school ->
                                        BasicComponent(
                                            title = school.school?.schoolName ?: school.schoolNo ?: "",
                                            onClick = { actions.onPickSchool(school) },
                                        )
                                    }
                                    BasicComponent(
                                        title = stringResource(R.string.login_skip_selection),
                                        summary = stringResource(R.string.login_skip_selection_hint),
                                        onClick = actions.onSkipSelection,
                                    )
                                }
                            }
                        }

                        state.lockChoices?.let { locks ->
                            Card(
                                modifier = Modifier
                                    .padding(top = 12.dp)
                                    .fillMaxWidth(),
                            ) {
                                Column {
                                    Text(
                                        text = stringResource(R.string.login_choose_lock),
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        color = colorScheme.onSurfaceVariantSummary,
                                    )
                                    locks.forEach { lock ->
                                        BasicComponent(
                                            title = lock.label,
                                            onClick = { actions.onPickLock(lock) },
                                        )
                                    }
                                    BasicComponent(
                                        title = stringResource(R.string.login_skip_selection),
                                        summary = stringResource(R.string.login_skip_selection_hint),
                                        onClick = actions.onSkipSelection,
                                    )
                                }
                            }
                        }

                        Card(
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .fillMaxWidth(),
                        ) {
                            EditText(
                                title = stringResource(R.string.login_username),
                                value = state.username,
                                onValueChange = actions.onUsernameChange,
                                textHint = "",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            )
                            EditText(
                                title = stringResource(R.string.login_password),
                                value = state.password,
                                onValueChange = actions.onPasswordChange,
                                // 已保存账号不回填密码（只存 MD5），用占位提示说明「无需重复输入」。
                                textHint = if (state.usingSavedCredential) {
                                    stringResource(R.string.login_password_saved)
                                } else {
                                    ""
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                visualTransformation = if (state.showPassword) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                            )
                            SwitchPreference(
                                title = stringResource(R.string.login_show_password),
                                summary = if (state.usingSavedCredential) {
                                    stringResource(R.string.login_show_password_saved)
                                } else {
                                    null
                                },
                                checked = state.showPassword,
                                // 已保存凭证时没有可供显示的密码明文，直接禁用开关，避免暴露 MD5 串。
                                enabled = !state.usingSavedCredential,
                                onCheckedChange = actions.onToggleShowPassword,
                            )
                        }

                        Card(
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .fillMaxWidth(),
                        ) {
                            SwitchPreference(
                                title = stringResource(R.string.login_save_current),
                                startAction = {
                                    Icon(
                                        Icons.Rounded.Person,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = stringResource(R.string.login_save_current),
                                        tint = colorScheme.onBackground,
                                    )
                                },
                                checked = state.saveCurrent,
                                onCheckedChange = actions.onSaveCurrentChange,
                            )
                        }

                        Card(
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .fillMaxWidth(),
                        ) {
                            BasicComponent(
                                title = if (state.loading) {
                                    stringResource(R.string.login_logging_in)
                                } else {
                                    stringResource(R.string.login_submit)
                                },
                                startAction = {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.Login,
                                        tint = colorScheme.onSurface,
                                        contentDescription = null,
                                    )
                                },
                                enabled = !state.loading,
                                onClick = actions.onLogin,
                            )
                        }

                        if (state.savedUsers.isNotEmpty()) {
                            Card(
                                modifier = Modifier
                                    .padding(top = 12.dp)
                                    .fillMaxWidth(),
                            ) {
                                Column {
                                    Text(
                                        text = stringResource(R.string.login_use_saved),
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        color = colorScheme.onSurfaceVariantSummary,
                                    )
                                    state.savedUsers.forEach { user ->
                                        BasicComponent(
                                            title = user.username,
                                            startAction = {
                                                Icon(
                                                    Icons.Rounded.Person,
                                                    tint = colorScheme.onSurface,
                                                    contentDescription = null,
                                                )
                                            },
                                            onClick = { actions.onPickSavedUser(user) },
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}
