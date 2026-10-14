package com.yunmei.vibe.ui.screen.subsettings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.twotone.Bluetooth
import androidx.compose.material.icons.twotone.LockOpen
import androidx.compose.material.icons.twotone.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yunmei.vibe.R
import com.yunmei.vibe.ui.component.material.SegmentedColumn
import com.yunmei.vibe.ui.component.material.SegmentedSwitchItem

/** 二级设置页「开锁」的 Material 实现（骨架与「主题设置」二级页一致）。 */
@Composable
fun UnlockSettingsMaterial(
    state: UnlockSettingsUiState,
    actions: UnlockSettingsActions,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(Unit) {
        scrollBehavior.state.heightOffset = scrollBehavior.state.heightOffsetLimit
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                },
                title = { Text(stringResource(R.string.settings_group_unlock)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                ),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
                content = listOf(
                    {
                        SegmentedSwitchItem(
                            icon = Icons.TwoTone.Bluetooth,
                            title = stringResource(id = R.string.unlock_quick_connect),
                            summary = stringResource(id = R.string.unlock_quick_connect_summary),
                            checked = state.settings.quickConnect,
                            onCheckedChange = actions.onSetQuickConnect
                        )
                    },
                    {
                        SegmentedSwitchItem(
                            icon = Icons.TwoTone.LockOpen,
                            title = stringResource(id = R.string.settings_auto_connect),
                            summary = stringResource(id = R.string.settings_auto_connect_summary),
                            checked = state.settings.autoConnect,
                            onCheckedChange = actions.onSetAutoConnect
                        )
                    },
                    {
                        SegmentedSwitchItem(
                            icon = Icons.TwoTone.PowerSettingsNew,
                            title = stringResource(id = R.string.settings_auto_exit),
                            checked = state.settings.autoExit,
                            onCheckedChange = actions.onSetAutoExit
                        )
                    },
                )
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
