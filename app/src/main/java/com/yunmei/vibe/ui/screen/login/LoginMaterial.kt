package com.yunmei.vibe.ui.screen.login

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.twotone.Login
import androidx.compose.material.icons.twotone.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.yunmei.vibe.R
import com.yunmei.vibe.ui.component.material.SegmentedCheckboxItem
import com.yunmei.vibe.ui.component.material.SegmentedColumn
import com.yunmei.vibe.ui.component.material.SegmentedListItem
import com.yunmei.vibe.ui.component.material.SegmentedSwitchItem
import com.yunmei.vibe.ui.component.material.SegmentedTextField
import com.yunmei.vibe.ui.component.material.TonalCard
import com.yunmei.vibe.ui.navigation3.LocalNavigator
import com.yunmei.vibe.ui.navigation3.Route

@Composable
fun LoginScreenMaterial(
    state: LoginUiState,
    actions: LoginActions,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val navigator = LocalNavigator.current
    val backToMain = {
        // 返回/跳过：多级栈出栈回主界面；若登录页孤立在栈底（旧状态恢复等异常），
        // 直接强清栈回主界面（等价 FLAG_ACTIVITY_NEW_TASK|CLEAR_TASK），绝不卡死。
        if (navigator.backStackSize() > 1) {
            navigator.pop()
        } else {
            navigator.replaceAll(listOf(Route.Main))
        }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.login_title)) },
                navigationIcon = {
                    IconButton(onClick = backToMain) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.lock_detail_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                ),
                scrollBehavior = scrollBehavior
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            state.message?.let { message ->
                TonalCard(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    )
                }
            }

            state.schoolChoices?.let { schools ->
                TonalCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Column {
                        Text(
                            text = stringResource(R.string.login_choose_school),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(16.dp),
                        )
                        SegmentedColumn(
                            content = buildList {
                                schools.forEach { school ->
                                    add {
                                        SegmentedListItem(
                                            onClick = { actions.onPickSchool(school) },
                                            headlineContent = {
                                                Text(school.school?.schoolName ?: school.schoolNo ?: "")
                                            },
                                        )
                                    }
                                }
                                add {
                                    SegmentedListItem(
                                        onClick = actions.onSkipSelection,
                                        headlineContent = { Text(stringResource(R.string.login_skip_selection)) },
                                        supportingContent = { Text(stringResource(R.string.login_skip_selection_hint)) },
                                    )
                                }
                            }
                        )
                    }
                }
            }

            state.lockChoices?.let { locks ->
                TonalCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Column {
                        Text(
                            text = stringResource(R.string.login_choose_lock),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(16.dp),
                        )
                        SegmentedColumn(
                            content = buildList {
                                locks.forEach { lock ->
                                    add {
                                        SegmentedListItem(
                                            onClick = { actions.onPickLock(lock) },
                                            headlineContent = { Text(lock.label) },
                                        )
                                    }
                                }
                                add {
                                    SegmentedListItem(
                                        onClick = actions.onSkipSelection,
                                        headlineContent = { Text(stringResource(R.string.login_skip_selection)) },
                                        supportingContent = { Text(stringResource(R.string.login_skip_selection_hint)) },
                                    )
                                }
                            }
                        )
                    }
                }
            }

            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                content = listOf {
                    SegmentedTextField(
                        label = stringResource(R.string.login_username),
                        value = state.username,
                        onValueChange = actions.onUsernameChange,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        placeholder = { Text("") },
                    )
                }
            )

            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                content = listOf {
                    SegmentedTextField(
                        label = stringResource(R.string.login_password),
                        value = state.password,
                        onValueChange = actions.onPasswordChange,
                        singleLine = true,
                        visualTransformation = if (state.showPassword) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        // 已保存账号不回填密码（只存 MD5），用占位提示说明「无需重复输入」。
                        placeholder = {
                            Text(
                                if (state.usingSavedCredential) {
                                    stringResource(R.string.login_password_saved)
                                } else {
                                    ""
                                }
                            )
                        },
                    )
                }
            )

            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                content = listOf {
                    SegmentedCheckboxItem(
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
            )

            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                content = listOf {
                    SegmentedSwitchItem(
                        icon = Icons.TwoTone.Person,
                        title = stringResource(R.string.login_save_current),
                        checked = state.saveCurrent,
                        onCheckedChange = actions.onSaveCurrentChange,
                    )
                }
            )

            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                content = listOf {
                    SegmentedListItem(
                        onClick = actions.onLogin,
                        enabled = !state.loading,
                        headlineContent = {
                            Text(
                                if (state.loading) {
                                    stringResource(R.string.login_logging_in)
                                } else {
                                    stringResource(R.string.login_submit)
                                }
                            )
                        },
                        leadingContent = { Icon(Icons.AutoMirrored.TwoTone.Login, null) },
                    )
                }
            )

            if (state.savedUsers.isNotEmpty()) {
                SegmentedColumn(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    content = state.savedUsers.map { user ->
                        {
                            SegmentedListItem(
                                onClick = { actions.onPickSavedUser(user) },
                                headlineContent = { Text(user.username) },
                                supportingContent = { Text(stringResource(R.string.login_use_saved)) },
                                leadingContent = { Icon(Icons.TwoTone.Person, null) },
                            )
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
