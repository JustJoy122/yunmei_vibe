package com.yunmei.vibe.ui.screen.home

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
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.WhereToVote
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yunmei.vibe.R
import com.yunmei.vibe.data.model.UnitCodes
import com.yunmei.vibe.ui.component.material.ExpressiveSwitch
import com.yunmei.vibe.ui.component.material.SegmentedColumn
import com.yunmei.vibe.ui.component.material.SegmentedListItem
import com.yunmei.vibe.ui.component.material.SegmentedSwitchItem
import com.yunmei.vibe.ui.component.material.TonalCard
import com.yunmei.vibe.ui.component.statustag.StatusTag

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
            // 打卡放在「开门」按钮与「获取密码」之间（原顺序为 获取密码 → 打卡）。
            if (state.showSignButton) {
                SignCard(state, actions)
            }
            if (state.showCodeButton) {
                CodeCard(state, actions)
            }
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
    // 首页状态卡：直接复用 KernelSU（KernelSU-main）HomeMaterial.kt StatusCard 原始实现。
    // 状态映射：已设默认门锁 = ksuActive（绿）；未设默认 / 无门锁 = 红色（errorContainer，两者仅文字不同）。
    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        val ready = state.defaultLock != null
        val default = state.defaultLock

        val containerColor = if (ready) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        }
        val contentColor = MaterialTheme.colorScheme.contentColorFor(containerColor)

        val statusIcon = if (ready) Icons.Outlined.CheckCircle else Icons.Outlined.Warning
        val statusTitle =
            if (ready) {
                stringResource(R.string.home_status_ready_title)
            } else if (state.lockCount == 0) {
                stringResource(R.string.home_status_empty_title)
            } else {
                stringResource(R.string.home_status_no_default_title)
            }
        val statusSummary =
            if (ready && default != null) {
                UnitCodes.name(default.schoolNo) ?: default.schoolNo
            } else if (state.lockCount == 0) {
                stringResource(R.string.home_status_add_action)
            } else {
                stringResource(R.string.home_status_set_default_action)
            }

        val statusTrailing: (@Composable () -> Unit)? = if (ready && default != null) {
            {
                StatusTag(
                    label = default.label.ifBlank { default.mac },
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    backgroundColor = MaterialTheme.colorScheme.primary
                )
            }
        } else null

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = containerColor,
            contentColor = contentColor,
            shape = MaterialTheme.shapes.large,
            onClick = {
                if (default != null) {
                    actions.onOpenDetail(default)
                } else {
                    actions.onOpenLocks()
                }
            }
        ) {
            ListItem(
                modifier = Modifier,
                leadingContent = {
                    Icon(statusIcon, contentDescription = statusTitle)
                },
                trailingContent = statusTrailing,
                overlineContent = null,
                supportingContent = {
                    Text(
                        text = statusSummary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                colors = ListItemDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = contentColor,
                    leadingContentColor = contentColor,
                    trailingContentColor = contentColor,
                    supportingContentColor = contentColor.copy(alpha = 0.7f)
                ),
                elevation = ListItemDefaults.elevation(),
                content = {
                    Text(
                        text = statusTitle,
                        style = MaterialTheme.typography.titleMediumEmphasized
                    )
                },
            )
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
                        text = state.signMessage ?: signLocationLabel(state.settings.signLocationMode),
                        color = MaterialTheme.colorScheme.outline,
                    )
                },
                leadingContent = { Icon(Icons.Filled.WhereToVote, null) },
            )
        }
    )
}
