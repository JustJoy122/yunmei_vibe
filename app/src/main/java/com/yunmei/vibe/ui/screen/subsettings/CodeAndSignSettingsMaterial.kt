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
import androidx.compose.material.icons.twotone.Key
import androidx.compose.material.icons.twotone.LocationOn
import androidx.compose.material.icons.twotone.SupervisorAccount
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
import com.yunmei.vibe.ui.SignLocationMode
import com.yunmei.vibe.ui.component.material.SegmentedColumn
import com.yunmei.vibe.ui.component.material.SegmentedDropdownItem
import com.yunmei.vibe.ui.component.material.SegmentedSwitchItem

/** 二级设置页「取码与打卡」的 Material 实现。 */
@Composable
fun CodeAndSignSettingsMaterial(
    state: CodeAndSignSettingsUiState,
    actions: CodeAndSignSettingsActions,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val signLocationItems = SignLocationMode.entries.map { stringResource(it.labelRes) }

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
                title = { Text(stringResource(R.string.settings_group_code_sign)) },
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
                            icon = Icons.TwoTone.Key,
                            title = stringResource(id = R.string.settings_auto_code),
                            summary = stringResource(id = R.string.settings_auto_code_summary),
                            checked = state.settings.autoCode,
                            onCheckedChange = actions.onSetAutoCode
                        )
                    },
                    {
                        SegmentedSwitchItem(
                            icon = Icons.TwoTone.SupervisorAccount,
                            title = stringResource(id = R.string.settings_always_code),
                            checked = state.settings.alwaysCode,
                            onCheckedChange = actions.onSetAlwaysCode
                        )
                    },
                    {
                        SegmentedDropdownItem(
                            icon = Icons.TwoTone.LocationOn,
                            title = stringResource(id = R.string.settings_sign_location_mode),
                            items = signLocationItems,
                            selectedIndex = SignLocationMode
                                .fromValue(state.settings.signLocationMode)
                                .ordinal,
                            onItemSelected = actions.onSetSignLocationMode,
                        )
                    },
                )
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
