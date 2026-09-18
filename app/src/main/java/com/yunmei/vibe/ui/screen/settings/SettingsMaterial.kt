package com.yunmei.vibe.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyOff
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Dashboard
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yunmei.vibe.R
import com.yunmei.vibe.data.local.StoredUser
import com.yunmei.vibe.ui.component.material.SegmentedColumn
import com.yunmei.vibe.ui.component.material.SegmentedDropdownItem
import com.yunmei.vibe.ui.component.material.SegmentedListItem
import com.yunmei.vibe.ui.component.material.SegmentedSwitchItem
import com.yunmei.vibe.ui.component.material.SendLogBottomSheet
import com.yunmei.vibe.ui.component.material.SnackBarHost

/**
 * 主设置页：与模板 KernelSU-Style-UI-Kit 一致的精简布局。
 */
@Composable
fun SettingPagerMaterial(
    state: SettingsScreenState,
    actions: SettingsScreenActions,
    bottomInnerPadding: Dp,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val snackBarHost = remember { SnackbarHostState() }
    var showBottomSheet by remember { mutableStateOf(false) }
    val signLocationItems = listOf(
        stringResource(R.string.settings_sign_location_ask),
        stringResource(R.string.settings_sign_location_relocate),
        stringResource(R.string.settings_sign_location_last),
    )

    Scaffold(
        topBar = {
            TopBar(scrollBehavior = scrollBehavior)
        },
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

            // 通用
            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
                title = stringResource(R.string.settings_group_general),
                content = listOf {
                    SegmentedSwitchItem(
                        icon = Icons.Filled.Update,
                        title = stringResource(id = R.string.settings_check_update),
                        summary = stringResource(id = R.string.settings_check_update_summary),
                        checked = state.checkUpdate,
                        onCheckedChange = actions.onSetCheckUpdate
                    )
                }
            )

            // 账号
            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
                title = stringResource(R.string.settings_group_account),
                content = buildList {
                    add {
                        SegmentedListItem(
                            onClick = actions.onLogin,
                            headlineContent = { Text(stringResource(R.string.settings_account_login)) },
                            supportingContent = { Text(stringResource(R.string.settings_account_hint)) },
                            leadingContent = { Icon(Icons.AutoMirrored.Filled.Login, null) },
                        )
                    }
                    state.accounts.forEach { user ->
                        add {
                            AccountRow(user, actions)
                        }
                    }
                }
            )

            // 个性化
            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
                title = stringResource(R.string.settings_group_personalization),
                content = buildList {
                    add {
                        SegmentedDropdownItem(
                            icon = Icons.Rounded.Dashboard,
                            title = stringResource(id = R.string.settings_ui_mode),
                            items = listOf("Miuix", "Material"),
                            selectedIndex = if (state.uiMode == "material") 1 else 0,
                            onItemSelected = actions.onSetUiModeIndex
                        )
                    }
                    add {
                        SegmentedListItem(
                            onClick = actions.onOpenTheme,
                            headlineContent = { Text(stringResource(id = R.string.settings_theme)) },
                            leadingContent = { Icon(Icons.Filled.Palette, stringResource(id = R.string.settings_theme)) },
                            trailingContent = {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    null
                                )
                            }
                        )
                    }
                }
            )

            // 开门行为（快速连接 / 取码 / 打卡；自动开门/自动退出/自动获取密码在首页开门卡片内）
            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
                title = stringResource(R.string.settings_group_door),
                content = buildList {
                    add {
                        // 快速连接：从首页开门卡片迁到设置页，仍写同一个 quick_connect 偏好。
                        SegmentedSwitchItem(
                            icon = Icons.Rounded.Bluetooth,
                            title = stringResource(id = R.string.unlock_quick_connect),
                            summary = stringResource(id = R.string.unlock_quick_connect_summary),
                            checked = state.settings.quickConnect,
                            onCheckedChange = actions.onSetQuickConnect
                        )
                    }
                    add {
                        SegmentedSwitchItem(
                            icon = Icons.Filled.SupervisorAccount,
                            title = stringResource(R.string.settings_always_code),
                            checked = state.settings.alwaysCode,
                            onCheckedChange = actions.onSetAlwaysCode,
                        )
                    }
                    add {
                        SegmentedDropdownItem(
                            icon = Icons.Filled.LocationOn,
                            title = stringResource(R.string.settings_sign_location_mode),
                            items = signLocationItems,
                            selectedIndex = when (state.settings.signLocationMode) {
                                "rel" -> 1
                                "lst" -> 2
                                else -> 0
                            },
                            onItemSelected = actions.onSetSignLocationMode,
                        )
                    }
                }
            )

            // 界面显示
            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
                title = stringResource(R.string.settings_group_display),
                content = listOf(
                    {
                        SegmentedSwitchItem(
                            icon = Icons.Filled.LocationOff,
                            title = stringResource(R.string.settings_hide_sign),
                            checked = state.settings.hideSign,
                            onCheckedChange = actions.onSetHideSign,
                        )
                    },
                    {
                        SegmentedSwitchItem(
                            icon = Icons.Filled.KeyOff,
                            title = stringResource(R.string.settings_hide_code),
                            checked = state.settings.hideCode,
                            onCheckedChange = actions.onSetHideCode,
                        )
                    },
                )
            )

            // 其他
            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
                title = stringResource(R.string.settings_group_other),
                content = listOf(
                    {
                        SegmentedListItem(
                            onClick = { showBottomSheet = true },
                            headlineContent = { Text(stringResource(id = R.string.send_log)) },
                            leadingContent = {
                                Icon(
                                    Icons.Filled.BugReport,
                                    stringResource(id = R.string.send_log)
                                )
                            },
                        )
                    },
                    {
                        SegmentedListItem(
                            onClick = actions.onOpenAbout,
                            headlineContent = { Text(stringResource(id = R.string.settings_about)) },
                            leadingContent = {
                                Icon(
                                    Icons.Filled.ContactPage,
                                    stringResource(id = R.string.settings_about)
                                )
                            },
                        )
                    }
                )
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (showBottomSheet) {
                SendLogBottomSheet(
                    onDismiss = { showBottomSheet = false },
                    snackbarHostState = snackBarHost,
                )
            }
            Spacer(modifier = Modifier.height(bottomInnerPadding))
        }
    }
}

@Composable
private fun TopBar(
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    LargeFlexibleTopAppBar(
        title = { Text(stringResource(R.string.settings)) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface
        ),
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        scrollBehavior = scrollBehavior
    )
}

@Composable
private fun AccountRow(
    user: StoredUser,
    actions: SettingsScreenActions,
) {
    SegmentedListItem(
        headlineContent = { Text(user.username) },
        supportingContent = { Text(stringResource(R.string.settings_saved_accounts)) },
        leadingContent = { Icon(Icons.Filled.Person, null) },
        trailingContent = {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.settings_remove_account),
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .clickable { actions.onRemoveAccount(user) }
                    .padding(8.dp),
            )
        },
    )
}
