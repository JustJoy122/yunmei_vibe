package com.yunmei.vibe.ui.screen.locks

import androidx.compose.material.icons.twotone.ContentPaste
import com.yunmei.vibe.data.local.StoredUser
import androidx.compose.material.icons.twotone.Person
import androidx.compose.material.icons.automirrored.twotone.Login
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.QrCodeScanner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.twotone.People
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yunmei.vibe.R
import com.yunmei.vibe.data.model.Lock
import com.yunmei.vibe.ui.component.material.SegmentedColumn
import com.yunmei.vibe.ui.component.material.SegmentedListItem
import com.yunmei.vibe.ui.component.material.SnackBarHost
import com.yunmei.vibe.ui.component.material.TonalCard

@Composable
fun LocksPagerMaterial(
    state: LocksUiState,
    actions: LocksActions,
    bottomInnerPadding: Dp,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val snackBarHost = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackBarHost.showSnackbar(message)
            actions.onMessageShown()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = { TopBar(scrollBehavior = scrollBehavior) },
        snackbarHost = { SnackBarHost(hostState = snackBarHost, modifier = Modifier.padding(bottom = bottomInnerPadding)) },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            if (state.locks.isEmpty()) {
                TonalCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.locks_empty),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.locks_empty_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    state.locks.forEach { lock ->
                        LockCard(
                            lock = lock,
                            isDefault = lock.label == state.defaultLabel,
                            actions = actions,
                        )
                    }
                }
            }

            // 账号（登录/切换账号 + 已保存账号；原「设置 → 账号」分组整体迁入）
            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                content = buildList {
                    add {
                        SegmentedListItem(
                            onClick = actions.onLogin,
                            headlineContent = {
                                Text(
                                    stringResource(
                                        if (state.accounts.isEmpty()) R.string.locks_add_login
                                        else R.string.settings_account_login
                                    )
                                )
                            },
                            supportingContent = { Text(stringResource(R.string.settings_account_hint)) },
                            leadingContent = { Icon(Icons.AutoMirrored.TwoTone.Login, null) },
                        )
                    }
                    state.accounts.forEach { user ->
                        add {
                            AccountRow(user, actions.onRemoveAccount)
                        }
                    }
                }
            )

            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                content = listOf(
                    {
                        SegmentedListItem(
                            onClick = actions.onAddScan,
                            headlineContent = { Text(stringResource(R.string.locks_add_scan)) },
                            supportingContent = { Text(stringResource(R.string.locks_add_scan_hint)) },
                            leadingContent = { Icon(Icons.TwoTone.QrCodeScanner, null) },
                        )
                    },
                    {
                        SegmentedListItem(
                            onClick = actions.onPasteLink,
                            headlineContent = { Text(stringResource(R.string.scan_paste_link)) },
                            supportingContent = { Text(stringResource(R.string.scan_paste_link_hint)) },
                            leadingContent = { Icon(Icons.TwoTone.ContentPaste, null) },
                        )
                    },
                )
            )
            Spacer(modifier = Modifier.height(bottomInnerPadding))
        }
    }
}

@Composable
private fun TopBar(
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    LargeFlexibleTopAppBar(
        title = { Text(stringResource(R.string.locks)) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        scrollBehavior = scrollBehavior
    )
}

@Composable
private fun LockCard(
    lock: Lock,
    isDefault: Boolean,
    actions: LocksActions,
) {
    TonalCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 10.dp)
        ) {
            // 对应 ModuleItem 的「标题 + 版本 + 作者」三行：0.8 宽列、bodySmall、onSurfaceVariant。
            Column(modifier = Modifier.fillMaxWidth(0.8f)) {
                Text(
                    text = lock.label.ifBlank { stringResource(R.string.home_status_empty_title) },
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                )
                Text(
                    text = lock.schoolName,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                // 说明行展示电量：与首页同一优先级（BLE 实时 > 服务端 lockElectricity，见 HomeMaterial）。
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.unlock_battery) + "：" +
                        (lock.lockElectricity?.let { "$it%" } ?: stringResource(R.string.lock_label_unknown)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            // KernelSU 的 META 标签是说明文字下方独立的一行，而不是贴在标题右侧。
            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                if (isDefault) {
                    DefaultTag(modifier = Modifier.padding(bottom = 4.dp))
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                thickness = Dp.Hairline,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // 与 KernelSU 的 CombinedClickableButton 一致：浅色底胶囊，而不是裸 IconButton。
                    FilledTonalButton(
                        onClick = { actions.onOpenDetail(lock) },
                        modifier = Modifier.defaultMinSize(52.dp, 32.dp),
                        contentPadding = ButtonDefaults.TextButtonContentPadding,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = stringResource(R.string.locks),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    FilledTonalButton(
                        onClick = { actions.onOpenMembers(lock) },
                        modifier = Modifier.defaultMinSize(52.dp, 32.dp),
                        contentPadding = ButtonDefaults.TextButtonContentPadding,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.People,
                            contentDescription = stringResource(R.string.dorm_members),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                FilledTonalButton(
                    onClick = { actions.onDelete(lock) },
                    modifier = Modifier.defaultMinSize(52.dp, 32.dp),
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        modifier = Modifier.padding(start = 7.dp),
                        text = stringResource(R.string.locks_delete),
                        fontFamily = MaterialTheme.typography.labelMedium.fontFamily,
                        fontSize = MaterialTheme.typography.labelMedium.fontSize,
                    )
                }
            }
        }
    }
}

/** 「默认」标签：与 KernelSU 模块卡片的 META 标签同款（浅色底 + 小号字）。 */
@Composable
private fun DefaultTag(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            color = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(4.dp),
        )
    ) {
        Text(
            text = stringResource(R.string.locks_default_tag),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun AccountRow(
    user: StoredUser,
    onRemove: (StoredUser) -> Unit,
) {
    SegmentedListItem(
        headlineContent = { Text(user.username) },
        supportingContent = { Text(stringResource(R.string.settings_saved_accounts)) },
        leadingContent = { Icon(Icons.TwoTone.Person, null) },
        trailingContent = {
            Icon(
                imageVector = Icons.TwoTone.Delete,
                contentDescription = stringResource(R.string.settings_remove_account),
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .clickable { onRemove(user) }
                    .padding(8.dp),
            )
        },
    )
}