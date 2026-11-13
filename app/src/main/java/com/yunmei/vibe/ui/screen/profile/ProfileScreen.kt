package com.yunmei.vibe.ui.screen.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yunmei.vibe.R
import com.yunmei.vibe.data.local.StoredUser
import com.yunmei.vibe.ui.component.material.SegmentedColumn
import com.yunmei.vibe.ui.component.material.SegmentedListItem
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.SmallTitle
import com.yunmei.vibe.ui.component.profile.ProfileAvatar
import com.yunmei.vibe.ui.UiMode
import com.yunmei.vibe.ui.LocalUiMode
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 个人中心：只读展示本人档案 + 本机账号管理。
 *
 * 布局照门锁详情页：顶栏是小号居中标题，内容区由「顶部身份块 + 若干信息分组」组成，
 * 信息分组沿用 `SegmentedColumn` / `SmallTitle + Card` 的样式，字段为空时整行不渲染。
 */
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onAddAccount: () -> Unit,
) {
    val viewModel = viewModel<ProfileViewModel>()
    viewModel.load()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = remember(viewModel, onBack, onAddAccount) {
        ProfileActions(
            onBack = onBack,
            onAddAccount = onAddAccount,
            onSwitchAccount = viewModel::switchAccount,
            onRemoveAccount = viewModel::removeAccount,
            onRetry = viewModel::refresh,
        )
    }
    when (LocalUiMode.current) {
        UiMode.Material -> ProfileScreenMaterial(state, actions)
        UiMode.Miuix -> ProfileScreenMiuix(state, actions)
    }
}

@Composable
private fun ProfileScreenMaterial(state: ProfileUiState, actions: ProfileActions) {
    val colorScheme = MaterialTheme.colorScheme
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        containerColor = colorScheme.surfaceContainer,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.profile_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.profile_title),
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
            Spacer(modifier = Modifier.height(16.dp))
            ProfileIdentity(
                avatarUrl = state.avatarUrl,
                name = state.displayName,
                containerColor = colorScheme.primaryContainer,
                contentColor = colorScheme.onPrimaryContainer,
            )

            if (state.error != null) {
                SegmentedColumn(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    content = listOf {
                        SegmentedListItem(
                            onClick = actions.onRetry,
                            headlineContent = { Text(stringResource(R.string.profile_load_failed)) },
                            supportingContent = { Text(state.error.orEmpty()) },
                        )
                    },
                )
            }

            ProfileInfoGroupMaterial(
                title = stringResource(R.string.profile_group_basic),
                rows = basicRows(state),
            )
            ProfileInfoGroupMaterial(
                title = stringResource(R.string.profile_group_education),
                rows = educationRows(state),
            )
            ProfileInfoGroupMaterial(
                title = stringResource(R.string.profile_group_dorm),
                rows = dormRows(state),
            )
            ProfileAccountGroupMaterial(state, actions)
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ProfileScreenMiuix(state: ProfileUiState, actions: ProfileActions) {
    val colorScheme = MiuixTheme.colorScheme
    val scrollBehavior = MiuixScrollBehavior()
    MiuixScaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.profile_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    MiuixIconButton(
                        onClick = actions.onBack,
                        modifier = Modifier.padding(start = 12.dp),
                    ) {
                        MiuixIcon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = stringResource(R.string.profile_title),
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
            ProfileIdentity(
                avatarUrl = state.avatarUrl,
                name = state.displayName,
                containerColor = colorScheme.primaryContainer,
                contentColor = colorScheme.onPrimaryContainer,
            )

            if (state.error != null) {
                Card(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth(),
                ) {
                    BasicComponent(
                        title = stringResource(R.string.profile_load_failed),
                        summary = state.error.orEmpty(),
                        onClick = actions.onRetry,
                    )
                }
            }

            ProfileInfoGroupMiuix(
                title = stringResource(R.string.profile_group_basic),
                rows = basicRows(state),
            )
            ProfileInfoGroupMiuix(
                title = stringResource(R.string.profile_group_education),
                rows = educationRows(state),
            )
            ProfileInfoGroupMiuix(
                title = stringResource(R.string.profile_group_dorm),
                rows = dormRows(state),
            )
            ProfileAccountGroupMiuix(state, actions)
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** 顶部身份块：没有卡片容器，头像与姓名上下居中。 */
@Composable
private fun ProfileIdentity(
    avatarUrl: String,
    name: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ProfileAvatar(
            url = avatarUrl,
            fallback = name,
            size = 80.dp,
            containerColor = containerColor,
            contentColor = contentColor,
            fontSize = 32,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = name.ifBlank { stringResource(R.string.profile_account_entry) },
            fontSize = 22.sp,
            fontWeight = FontWeight(600),
            color = if (LocalUiMode.current == UiMode.Miuix) {
                MiuixTheme.colorScheme.onBackground
            } else {
                MaterialTheme.colorScheme.onBackground
            },
        )
    }
}

/** 一行信息：标签 + 值；值空则由调用方过滤掉。 */
private data class ProfileRow(val label: String, val value: String)

@Composable
private fun basicRows(state: ProfileUiState): List<ProfileRow> {
    val stuNo = state.profile?.stuNo?.takeIf { it.isNotBlank() }
        ?: state.profile?.userId?.takeIf { it.isNotBlank() }
    return listOfNotNull(
        stuNo?.let { ProfileRow(stringResource(R.string.profile_stu_no), it) },
        state.displayTel.takeIf { it.isNotBlank() }
            ?.let { ProfileRow(stringResource(R.string.profile_mobile), it) },
    )
}

@Composable
private fun educationRows(state: ProfileUiState): List<ProfileRow> {
    val profile = state.profile
    return listOfNotNull(
        profile?.collegeName?.takeIf { it.isNotBlank() }
            ?.let { ProfileRow(stringResource(R.string.profile_college), it) },
        profile?.majorName?.takeIf { it.isNotBlank() }
            ?.let { ProfileRow(stringResource(R.string.profile_major), it) },
        profile?.schoolYear?.takeIf { it.isNotBlank() }
            ?.let { ProfileRow(stringResource(R.string.profile_school_year), it) },
        profile?.className?.takeIf { it.isNotBlank() }
            ?.let { ProfileRow(stringResource(R.string.profile_class), it) },
    )
}

@Composable
private fun dormRows(state: ProfileUiState): List<ProfileRow> {
    val profile = state.profile
    return listOfNotNull(
        profile?.areaName?.takeIf { it.isNotBlank() }
            ?.let { ProfileRow(stringResource(R.string.profile_area), it) },
        profile?.buildName?.takeIf { it.isNotBlank() }
            ?.let { ProfileRow(stringResource(R.string.profile_build), it) },
        profile?.dormName?.takeIf { it.isNotBlank() }
            ?.let { ProfileRow(stringResource(R.string.profile_dorm), it) },
        profile?.attr1?.takeIf { it.isNotBlank() }
            ?.let { ProfileRow(stringResource(R.string.profile_attr), it) },
    )
}

@Composable
private fun ProfileInfoGroupMaterial(title: String, rows: List<ProfileRow>) {
    if (rows.isEmpty()) return
    SegmentedColumn(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        title = title,
        content = rows.map { row ->
            {
                SegmentedListItem(
                    headlineContent = { Text(row.label) },
                    supportingContent = { Text(row.value) },
                )
            }
        },
    )
}

@Composable
private fun ProfileInfoGroupMiuix(title: String, rows: List<ProfileRow>) {
    if (rows.isEmpty()) return
    SmallTitle(
        text = title,
        insideMargin = PaddingValues(start = 16.dp, top = 12.dp, end = 28.dp, bottom = 0.dp),
    )
    Card(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth(),
    ) {
        Column {
            rows.forEach { row ->
                BasicComponent(title = row.label, summary = row.value)
            }
        }
    }
}

@Composable
private fun ProfileAccountGroupMaterial(state: ProfileUiState, actions: ProfileActions) {
    SegmentedColumn(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        title = stringResource(R.string.profile_group_account),
        content = buildList {
            state.accounts.forEach { account ->
                add { AccountRowMaterial(account, state, actions) }
            }
            add {
                SegmentedListItem(
                    onClick = actions.onAddAccount,
                    headlineContent = { Text(stringResource(R.string.profile_add_account)) },
                    leadingContent = { Icon(Icons.Rounded.Add, null) },
                )
            }
        },
    )
}

@Composable
private fun ProfileAccountGroupMiuix(state: ProfileUiState, actions: ProfileActions) {
    SmallTitle(
        text = stringResource(R.string.profile_group_account),
        insideMargin = PaddingValues(start = 16.dp, top = 12.dp, end = 28.dp, bottom = 0.dp),
    )
    Card(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth(),
    ) {
        Column {
            state.accounts.forEach { account ->
                AccountRowMiuix(account, state, actions)
            }
            ArrowPreference(
                title = stringResource(R.string.profile_add_account),
                onClick = actions.onAddAccount,
            )
        }
    }
}

@Composable
private fun AccountRowMaterial(
    account: StoredUser,
    state: ProfileUiState,
    actions: ProfileActions,
) {
    val isCurrent = account.usernameMd5 == state.currentUsernameMd5
    SegmentedListItem(
        onClick = { if (!isCurrent) actions.onSwitchAccount(account) },
        headlineContent = { Text(account.username) },
        supportingContent = if (isCurrent) {
            { Text(stringResource(R.string.profile_current_account)) }
        } else {
            null
        },
        leadingContent = if (isCurrent) {
            { Icon(Icons.Rounded.Check, null) }
        } else {
            null
        },
        trailingContent = {
            IconButton(onClick = { actions.onRemoveAccount(account) }) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.profile_remove_account),
                )
            }
        },
    )
}

@Composable
private fun AccountRowMiuix(
    account: StoredUser,
    state: ProfileUiState,
    actions: ProfileActions,
) {
    val colorScheme = MiuixTheme.colorScheme
    val isCurrent = account.usernameMd5 == state.currentUsernameMd5
    ArrowPreference(
        title = account.username,
        summary = if (isCurrent) stringResource(R.string.profile_current_account) else "",
        startAction = {
            if (isCurrent) {
                MiuixIcon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = colorScheme.primary,
                )
            }
        },
        endActions = {
            MiuixIconButton(onClick = { actions.onRemoveAccount(account) }) {
                MiuixIcon(
                    imageVector = MiuixIcons.Delete,
                    contentDescription = stringResource(R.string.profile_remove_account),
                    tint = colorScheme.onSurfaceVariantSummary,
                )
            }
        },
        onClick = { if (!isCurrent) actions.onSwitchAccount(account) },
    )
}
