package com.yunmei.client.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.WhereToVote
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yunmei.client.R
import com.yunmei.client.data.model.UnitCodes
import com.yunmei.client.ui.component.material.ExpressiveSwitch
import com.yunmei.client.ui.component.material.SegmentedColumn
import com.yunmei.client.ui.component.material.SegmentedListItem
import com.yunmei.client.ui.component.material.SegmentedSwitchItem
import com.yunmei.client.ui.component.material.TonalCard

@Composable
fun HomePagerMaterial(
    state: HomeUiState,
    actions: HomeActions,
    bottomInnerPadding: Dp,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        topBar = { TopBar(scrollBehavior = scrollBehavior) },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LockStatusCard(state, actions)
            UnlockActionCard(state, actions)
            if (state.showCodeButton) {
                CodeCard(state, actions)
            }
            if (state.showSignButton) {
                SignCard(state, actions)
            }
            QuickConnectCard(state, actions)
            DoorOptionsCard(state, actions)
            state.signAsk?.let { ask ->
                SignAskCard(ask, actions)
            }
            Spacer(Modifier.height(bottomInnerPadding))
        }
    }
}

@Composable
private fun TopBar(
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    LargeFlexibleTopAppBar(
        title = { Text(stringResource(R.string.app_name)) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface
        ),
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        scrollBehavior = scrollBehavior
    )
}

@Composable
private fun LockStatusCard(
    state: HomeUiState,
    actions: HomeActions,
) {
    // 状态 Banner（模板 PermissionCard 结构）：仅当显式设置了默认门锁才显示绿色。
    val ready = state.defaultLock != null
    val empty = state.lockCount == 0
    val default = state.defaultLock
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            if (default != null) {
                actions.onOpenDetail(default)
            } else {
                actions.onOpenLocks()
            }
        },
        colors = CardDefaults.cardColors(
            containerColor =
                if (ready) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.errorContainer,
            contentColor =
                if (ready) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text =
                            if (ready) {
                                stringResource(R.string.home_status_ready_title)
                            } else if (empty) {
                                stringResource(R.string.home_status_empty_title)
                            } else {
                                stringResource(R.string.home_status_no_default_title)
                            },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    // 副标题位置：绿色显示单位名称；红色无副标题。
                    if (ready && default != null) {
                        Text(
                            text = UnitCodes.name(default.schoolNo) ?: default.schoolNo,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                AssistChip(
                    onClick = { },
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor =
                            if (ready) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            },
                        leadingIconContentColor =
                            if (ready) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            },
                    ),
                    label = {
                        Text(
                            if (ready && default != null) {
                                // 右侧区域：绿色直接显示当前门锁号（标签）。
                                default.label.ifBlank { default.mac }
                            } else if (empty) {
                                stringResource(R.string.home_status_add_action)
                            } else {
                                stringResource(R.string.home_status_set_default_action)
                            }
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector =
                                if (ready) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                            contentDescription = null,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun UnlockActionCard(
    state: HomeUiState,
    actions: HomeActions,
) {
    TonalCard(onClick = actions.onOpenDoor) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { state.progress / 100f },
                    modifier = Modifier.size(112.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeWidth = 8.dp,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${state.progress}%",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.unlock_open),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            Text(
                text = state.statusText.ifBlank { stringResource(R.string.unlock_ready) },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            state.battery?.let { battery ->
                Text(
                    text = stringResource(R.string.unlock_battery) + "：$battery%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun QuickConnectCard(
    state: HomeUiState,
    actions: HomeActions,
) {
    TonalCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.unlock_quick_connect),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.unlock_quick_connect_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            ExpressiveSwitch(
                checked = state.quickConnect,
                onCheckedChange = actions.onSetQuickConnect,
            )
        }
    }
}

@Composable
private fun DoorOptionsCard(
    state: HomeUiState,
    actions: HomeActions,
) {
    SegmentedColumn(
        content = listOf(
            {
                SegmentedSwitchItem(
                    icon = Icons.Filled.AutoMode,
                    title = stringResource(R.string.settings_auto_connect),
                    summary = stringResource(R.string.settings_auto_connect_summary),
                    checked = state.settings.autoConnect,
                    onCheckedChange = actions.onSetAutoConnect,
                )
            },
            {
                SegmentedSwitchItem(
                    icon = Icons.AutoMirrored.Filled.ExitToApp,
                    title = stringResource(R.string.settings_auto_exit),
                    summary = stringResource(R.string.settings_auto_exit_summary),
                    checked = state.settings.autoExit,
                    onCheckedChange = actions.onSetAutoExit,
                )
            },
        )
    )
}

@Composable
private fun SignAskCard(
    ask: SignAskState,
    actions: HomeActions,
) {
    TonalCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.unlock_sign_ask_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Text(
                text = stringResource(R.string.unlock_sign_ask_msg, ask.lastLocation.ifBlank { "—" }),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            SegmentedColumn(
                content = listOf(
                    {
                        SegmentedListItem(
                            onClick = { actions.onResolveSignAsk(SignAskChoice.RELOCATE_SAVE) },
                            headlineContent = { Text(stringResource(R.string.unlock_sign_relocate)) },
                        )
                    },
                    {
                        SegmentedListItem(
                            onClick = { actions.onResolveSignAsk(SignAskChoice.LOCATE_ONLY) },
                            headlineContent = { Text(stringResource(R.string.unlock_sign_locate)) },
                        )
                    },
                    {
                        SegmentedListItem(
                            onClick = { actions.onResolveSignAsk(SignAskChoice.USE_LAST) },
                            headlineContent = { Text(stringResource(R.string.unlock_sign_use_last)) },
                        )
                    },
                    {
                        SegmentedListItem(
                            onClick = actions.onDismissSignAsk,
                            headlineContent = { Text(stringResource(R.string.cancel)) },
                        )
                    },
                )
            )
        }
    }
}

@Composable
private fun CodeCard(
    state: HomeUiState,
    actions: HomeActions,
) {
    SegmentedColumn(
        content = listOf(
            {
                SegmentedSwitchItem(
                    icon = Icons.Filled.Password,
                    title = stringResource(R.string.settings_auto_code),
                    summary = stringResource(R.string.settings_auto_code_summary),
                    checked = state.settings.autoCode,
                    onCheckedChange = actions.onSetAutoCode,
                )
            },
            {
                SegmentedListItem(
                    onClick = actions.onGetCode,
                    headlineContent = { Text(stringResource(R.string.unlock_get_code)) },
                    supportingContent = {
                        Text(
                            text = state.code ?: state.codeError
                            ?: stringResource(R.string.unlock_code_hint),
                            color = if (state.codeError != null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                        )
                    },
                    leadingContent = { Icon(Icons.Filled.Key, null) },
                )
            },
        )
    )
}

@Composable
private fun SignCard(
    state: HomeUiState,
    actions: HomeActions,
) {
    SegmentedColumn(
        content = listOf {
            SegmentedListItem(
                onClick = actions.onSign,
                headlineContent = { Text(stringResource(R.string.unlock_sign)) },
                supportingContent = {
                    Text(
                        text = state.signMessage
                            ?: stringResource(R.string.unlock_sign_ask_title) + " · " + state.settings.signLocationMode,
                        color = MaterialTheme.colorScheme.outline,
                    )
                },
                leadingContent = { Icon(Icons.Filled.WhereToVote, null) },
            )
        }
    )
}
