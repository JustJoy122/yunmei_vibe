package com.yunmei.vibe.ui.screen.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yunmei.vibe.R
import com.yunmei.vibe.data.local.StoredUser
import com.yunmei.vibe.ui.component.dialog.SteppedWarningDialog
import com.yunmei.vibe.ui.component.dialog.WarningStepText
import com.yunmei.vibe.ui.component.material.SegmentedColumn
import com.yunmei.vibe.ui.component.material.SegmentedListItem
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.SmallTitle
import com.yunmei.vibe.ui.component.profile.AvatarPickerMenu
import com.yunmei.vibe.ui.component.profile.ProfileAvatar
import com.yunmei.vibe.ui.UiMode
import com.yunmei.vibe.ui.LocalUiMode
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.overlay.OverlayDialog
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
    onResetPassword: () -> Unit,
    onChangePhone: () -> Unit,
    onAccountDestroyed: () -> Unit,
) {
    val viewModel = viewModel<ProfileViewModel>()
    viewModel.load()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // 删除本机账号同样需要二次确认：先把待删账号挂起，确认后再交给 ViewModel。
    var pendingRemove by remember { mutableStateOf<StoredUser?>(null) }
    // 换头像的来源菜单（拍照 / 相册）。
    var showAvatarMenu by remember { mutableStateOf(false) }
    // 注销是三级确认：0=不显示，1=标准警告，2=最后确认，3=最终警告（第 3 级确认后才真正注销）。
    var destroyStep by remember { mutableIntStateOf(0) }
    val destroyTitle = stringResource(R.string.profile_destroy_account_confirm_title)
    val destroyContent = stringResource(R.string.profile_destroy_account_confirm_content)
    val destroyContinue = stringResource(R.string.profile_destroy_continue)
    val destroyCancel = stringResource(R.string.profile_destroy_cancel)
    val destroyFinalTitle = stringResource(R.string.profile_destroy_final_title)
    val destroyFinalContent = stringResource(R.string.profile_destroy_final_content)
    val destroyFinalConfirm = stringResource(R.string.profile_destroy_final_confirm)
    val destroyFinalAbort = stringResource(R.string.profile_destroy_final_abort)
    val destroyLastTitle = stringResource(R.string.profile_destroy_last_title)
    val destroyLastContent = stringResource(R.string.profile_destroy_last_content)
    val actions = remember(
        viewModel,
        onBack,
        onAddAccount,
        onResetPassword,
        onChangePhone,
    ) {
        ProfileActions(
            onBack = onBack,
            onAddAccount = onAddAccount,
            onSwitchAccount = viewModel::switchAccount,
            onRemoveAccount = { account -> pendingRemove = account },
            onRetry = viewModel::refresh,
            onResetPassword = onResetPassword,
            onChangePhone = onChangePhone,
            onEditAvatar = { showAvatarMenu = true },
            // 注销不可逆：标准警告 → 最后确认 → 最终警告，三级都确认才真正执行。
            onDestroyAccount = { destroyStep = 1 },
        )
    }

    // 注销成功：账号在本机与服务端都已不存在，回登录页重新开始。
    LaunchedEffect(state.destroyed) {
        if (state.destroyed) onAccountDestroyed()
    }

    when (LocalUiMode.current) {
        UiMode.Material -> ProfileScreenMaterial(state, actions)
        UiMode.Miuix -> ProfileScreenMiuix(state, actions)
    }

    AvatarPickerMenu(
        show = showAvatarMenu,
        onDismissRequest = { showAvatarMenu = false },
        onPicked = { bytes ->
            showAvatarMenu = false
            viewModel.uploadAvatar(bytes)
        },
        onError = { message ->
            showAvatarMenu = false
            viewModel.onAvatarPickedFailed(message)
        },
    )

    RemoveAccountDialog(
        account = pendingRemove,
        onConfirm = {
            val target = pendingRemove
            pendingRemove = null
            if (target != null) viewModel.removeAccount(target)
        },
        onDismiss = { pendingRemove = null },
    )

    // 与开锁页的三级危险确认共用同一个组件，标题样式与过渡动画完全一致。
    SteppedWarningDialog(
        step = destroyStep,
        texts = { step ->
            when {
                step >= 3 -> WarningStepText(
                    title = destroyLastTitle,
                    message = destroyLastContent,
                    isStrong = true,
                )
                step == 2 -> WarningStepText(
                    title = destroyFinalTitle,
                    message = destroyFinalContent,
                    isStrong = true,
                )
                else -> WarningStepText(title = destroyTitle, message = destroyContent)
            }
        },
        confirmText = if (destroyStep >= 3) destroyFinalConfirm else destroyContinue,
        dismissText = if (destroyStep >= 2) destroyFinalAbort else destroyCancel,
        onConfirm = {
            if (destroyStep >= 3) {
                destroyStep = 0
                viewModel.destroyAccount()
            } else {
                destroyStep += 1
            }
        },
        onDismiss = { destroyStep = 0 },
    )
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
                onEditAvatar = actions.onEditAvatar,
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

            ProfileBasicGroupMaterial(state, actions)
            ProfileInfoGroupMaterial(
                title = stringResource(R.string.profile_group_education),
                rows = educationRows(state),
            )
            ProfileInfoGroupMaterial(
                title = stringResource(R.string.profile_group_dorm),
                rows = dormRows(state),
            )
            ProfileAccountGroupMaterial(state, actions)
            ProfileSecurityGroupMaterial(state, actions)
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
                onEditAvatar = actions.onEditAvatar,
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

            ProfileBasicGroupMiuix(state, actions)
            ProfileInfoGroupMiuix(
                title = stringResource(R.string.profile_group_education),
                rows = educationRows(state),
            )
            ProfileInfoGroupMiuix(
                title = stringResource(R.string.profile_group_dorm),
                rows = dormRows(state),
            )
            ProfileAccountGroupMiuix(state, actions)
            ProfileSecurityGroupMiuix(state, actions)
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
    onEditAvatar: () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 头像 + 右下角编辑角标：角标底色取主题主色，保证叠在任何头像上都看得清。
        Box {
            ProfileAvatar(
                url = avatarUrl,
                fallback = name,
                size = 80.dp,
                containerColor = containerColor,
                contentColor = contentColor,
                fontSize = 32,
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onEditAvatar),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PhotoCamera,
                    contentDescription = stringResource(R.string.profile_avatar_change),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
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
    // 手机号不再是普通信息行：它在「账号信息」分组末尾以可点击列表项呈现（换绑入口）。
    return listOfNotNull(
        stuNo?.let { ProfileRow(stringResource(R.string.profile_stu_no), it) },
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

/** 「账号信息」分组：普通信息行 + 末尾可点击的手机号行（点击进换绑页）。 */
@Composable
private fun ProfileBasicGroupMaterial(state: ProfileUiState, actions: ProfileActions) {
    val rows = basicRows(state)
    val tel = state.displayTel
    if (rows.isEmpty() && tel.isBlank()) return
    SegmentedColumn(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        title = stringResource(R.string.profile_group_basic),
        content = buildList {
            rows.forEach { row ->
                add {
                    SegmentedListItem(
                        headlineContent = { Text(row.label) },
                        supportingContent = { Text(row.value) },
                    )
                }
            }
            if (tel.isNotBlank()) {
                add {
                    // 手机号即换绑入口：整行可点击，右侧给箭头提示可跳转。
                    SegmentedListItem(
                        onClick = actions.onChangePhone,
                        headlineContent = { Text(stringResource(R.string.profile_mobile)) },
                        supportingContent = { Text(tel) },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                            )
                        },
                    )
                }
            }
        },
    )
}

/** 「账号信息」分组的 Miuix 版：手机号用 ArrowPreference，天然带右箭头。 */
@Composable
private fun ProfileBasicGroupMiuix(state: ProfileUiState, actions: ProfileActions) {
    val rows = basicRows(state)
    val tel = state.displayTel
    if (rows.isEmpty() && tel.isBlank()) return
    SmallTitle(
        text = stringResource(R.string.profile_group_basic),
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
            if (tel.isNotBlank()) {
                ArrowPreference(
                    title = stringResource(R.string.profile_mobile),
                    summary = tel,
                    onClick = actions.onChangePhone,
                )
            }
        }
    }
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
                    tint = MaterialTheme.colorScheme.error,
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
    // 删除是直接动作、不是跳转：用 BasicComponent（无右侧箭头），删除图标取 error 色，
    // 与门锁列表的删除一致。
    BasicComponent(
        title = account.username,
        summary = if (isCurrent) stringResource(R.string.profile_current_account) else null,
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
                    tint = colorScheme.error,
                )
            }
        },
        onClick = { if (!isCurrent) actions.onSwitchAccount(account) },
    )
}

@Composable
private fun ProfileSecurityGroupMaterial(state: ProfileUiState, actions: ProfileActions) {
    val colorScheme = MaterialTheme.colorScheme
    SegmentedColumn(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        title = stringResource(R.string.profile_group_security),
        content = listOf(
            {
                SegmentedListItem(
                    onClick = actions.onResetPassword,
                    headlineContent = { Text(stringResource(R.string.profile_reset_password)) },
                    supportingContent = { Text(stringResource(R.string.profile_reset_password_summary)) },
                    leadingContent = { Icon(Icons.Rounded.Lock, null) },
                )
            },
            {
                SegmentedListItem(
                    onClick = actions.onDestroyAccount,
                    enabled = !state.destroying,
                    headlineContent = {
                        Text(
                            text = stringResource(R.string.profile_destroy_account),
                            color = colorScheme.error,
                        )
                    },
                    supportingContent = { Text(stringResource(R.string.profile_destroy_account_summary)) },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.DeleteForever,
                            contentDescription = null,
                            tint = colorScheme.error,
                        )
                    },
                )
            },
        ),
    )
}

@Composable
private fun ProfileSecurityGroupMiuix(state: ProfileUiState, actions: ProfileActions) {
    SmallTitle(
        text = stringResource(R.string.profile_group_security),
        insideMargin = PaddingValues(start = 16.dp, top = 12.dp, end = 28.dp, bottom = 0.dp),
    )
    Card(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth(),
    ) {
        Column {
            ArrowPreference(
                title = stringResource(R.string.profile_reset_password),
                summary = stringResource(R.string.profile_reset_password_summary),
                onClick = actions.onResetPassword,
            )
            ArrowPreference(
                title = stringResource(R.string.profile_destroy_account),
                summary = stringResource(R.string.profile_destroy_account_summary),
                enabled = !state.destroying,
                onClick = actions.onDestroyAccount,
            )
        }
    }
}

/**
 * 删除本机账号的二次确认框。
 *
 * 与注销账号的区别要写清楚：只清理本地列表，不会注销云端账号。
 */
@Composable
private fun RemoveAccountDialog(
    account: StoredUser?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title = stringResource(R.string.profile_remove_account_confirm_title)
    val content = stringResource(R.string.profile_remove_account_confirm_content)
    when (LocalUiMode.current) {
        UiMode.Material -> {
            if (account == null) return
            val colorScheme = MaterialTheme.colorScheme
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(title) },
                text = {
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                confirmButton = {
                    TextButton(onClick = onConfirm) {
                        Text(
                            text = stringResource(R.string.profile_remove_account_confirm),
                            color = colorScheme.error,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.profile_destroy_cancel))
                    }
                },
            )
        }

        UiMode.Miuix -> {
            val colorScheme = MiuixTheme.colorScheme
            OverlayDialog(
                show = account != null,
                title = title,
                summary = content,
                onDismissRequest = onDismiss,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    MiuixTextButton(
                        text = stringResource(R.string.profile_destroy_cancel),
                        onClick = onDismiss,
                    )
                    MiuixTextButton(
                        text = stringResource(R.string.profile_remove_account_confirm),
                        onClick = onConfirm,
                        colors = ButtonDefaults.textButtonColors(
                            color = colorScheme.error,
                            textColor = colorScheme.onError,
                        ),
                    )
                }
            }
        }
    }
}