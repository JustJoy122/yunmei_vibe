package com.yunmei.vibe.ui.screen.settings

import androidx.compose.material.icons.twotone.Key
import androidx.compose.material.icons.twotone.LockOpen
import androidx.compose.material.icons.twotone.DisplaySettings
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.twotone.Login
import androidx.compose.material.icons.twotone.Bluetooth
import androidx.compose.material.icons.twotone.BugReport
import androidx.compose.material.icons.twotone.ContactPage
import androidx.compose.material.icons.twotone.Dashboard
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.KeyOff
import androidx.compose.material.icons.twotone.LocationOff
import androidx.compose.material.icons.twotone.LocationOn
import androidx.compose.material.icons.twotone.Palette
import androidx.compose.material.icons.twotone.Person
import androidx.compose.material.icons.twotone.SupervisorAccount
import androidx.compose.material.icons.twotone.Update
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
import com.yunmei.vibe.ui.SignLocationMode
import com.yunmei.vibe.data.local.StoredUser
import com.yunmei.vibe.ui.component.material.SegmentedColumn
import com.yunmei.vibe.ui.component.material.SegmentedDropdownItem
import com.yunmei.vibe.ui.component.material.SegmentedListItem
import com.yunmei.vibe.ui.component.material.SegmentedSwitchItem
import com.yunmei.vibe.YunMeiApp
import com.yunmei.vibe.ui.component.backup.BackupRestoreEntry
import com.yunmei.vibe.ui.component.backup.BackupSecureStoreWarning
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
    // 下拉顺序与文案统一取 SignLocationMode.entries（原先首页/设置动作/两个页面各自维护，易错位）。
    val signLocationModes = SignLocationMode.entries
    val signLocationItems = signLocationModes.map { stringResource(it.labelRes) }

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
                        icon = Icons.TwoTone.Update,
                        title = stringResource(id = R.string.settings_check_update),
                        summary = stringResource(id = R.string.settings_check_update_summary),
                        checked = state.checkUpdate,
                        onCheckedChange = actions.onSetCheckUpdate
                    )
                }
            )

            // 外观与个性化（界面风格 / 主题设置 / 两个隐藏入口开关）
            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
                title = stringResource(R.string.settings_group_appearance),
                content = buildList {
                    add {
                        SegmentedDropdownItem(
                            icon = Icons.TwoTone.DisplaySettings,
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
                            leadingContent = { Icon(Icons.TwoTone.Palette, stringResource(id = R.string.settings_theme)) },
                            trailingContent = {
                                Icon(
                                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    null
                                )
                            }
                        )
                    }
                    add {
                        SegmentedSwitchItem(
                            icon = Icons.TwoTone.LocationOff,
                            title = stringResource(R.string.settings_hide_sign),
                            checked = state.settings.hideSign,
                            onCheckedChange = actions.onSetHideSign,
                        )
                    }
                    add {
                        SegmentedSwitchItem(
                            icon = Icons.TwoTone.KeyOff,
                            title = stringResource(R.string.settings_hide_code),
                            checked = state.settings.hideCode,
                            onCheckedChange = actions.onSetHideCode,
                        )
                    }
                }
            )

            // 开锁（二级页入口）
            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
                content = listOf(
                    {
                        SegmentedListItem(
                            onClick = actions.onOpenUnlockSettings,
                            headlineContent = { Text(stringResource(R.string.settings_group_unlock)) },
                            leadingContent = { Icon(Icons.TwoTone.LockOpen, stringResource(R.string.settings_group_unlock)) },
                            trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) }
                        )
                    },
                )
            )

            // 取码与打卡（二级页入口）
            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
                content = listOf(
                    {
                        SegmentedListItem(
                            onClick = actions.onOpenCodeAndSignSettings,
                            headlineContent = { Text(stringResource(R.string.settings_group_code_sign)) },
                            leadingContent = { Icon(Icons.TwoTone.Key, stringResource(R.string.settings_group_code_sign)) },
                            trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) }
                        )
                    },
                )
            )


            // 其他
            // 安全存储不可用时的内联提示（方案 A：设置页，不弹阻断式对话框；恢复后自动隐藏）
            BackupSecureStoreWarning(show = !YunMeiApp.app.container.secureStoreAvailable)
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
                                    Icons.TwoTone.BugReport,
                                    stringResource(id = R.string.send_log)
                                )
                            },
                        )
                    },
                    {
                        BackupRestoreEntry()
                    },
                    {
                        SegmentedListItem(
                            onClick = actions.onOpenAbout,
                            headlineContent = { Text(stringResource(id = R.string.settings_about)) },
                            leadingContent = {
                                Icon(
                                    Icons.TwoTone.ContactPage,
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
