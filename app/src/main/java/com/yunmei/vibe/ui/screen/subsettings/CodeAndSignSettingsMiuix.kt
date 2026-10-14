package com.yunmei.vibe.ui.screen.subsettings

import top.yukonga.miuix.kmp.icon.extended.Back
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.SupervisorAccount
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.yunmei.vibe.R
import com.yunmei.vibe.ui.SignLocationMode
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/** 二级设置页「取码与打卡」的 Miuix 实现。 */
@Composable
fun CodeAndSignSettingsMiuix(
    state: CodeAndSignSettingsUiState,
    actions: CodeAndSignSettingsActions,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val signLocationItems = SignLocationMode.entries.map { stringResource(it.labelRes) }

    Scaffold(
        topBar = {
            TopAppBar(
                color = colorScheme.surface,
                title = stringResource(R.string.settings_group_code_sign),
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        val layoutDirection = LocalLayoutDirection.current
                        Icon(
                            modifier = Modifier.graphicsLayer {
                                if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                            },
                            imageVector = MiuixIcons.Back,
                            contentDescription = null,
                            tint = colorScheme.onBackground
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp),
                contentPadding = innerPadding,
                overscrollEffect = null,
            ) {
                item {
                    Column(modifier = Modifier.padding(vertical = 12.dp)) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                SwitchPreference(
                                    title = stringResource(id = R.string.settings_auto_code),
                                    summary = stringResource(id = R.string.settings_auto_code_summary),
                                    startAction = {
                                        Icon(
                                            Icons.Rounded.Key,
                                            modifier = Modifier.padding(end = 6.dp),
                                            contentDescription = stringResource(id = R.string.settings_auto_code),
                                            tint = colorScheme.onBackground
                                        )
                                    },
                                    checked = state.settings.autoCode,
                                    onCheckedChange = actions.onSetAutoCode
                                )
                                SwitchPreference(
                                    title = stringResource(id = R.string.settings_always_code),
                                    startAction = {
                                        Icon(
                                            Icons.Rounded.SupervisorAccount,
                                            modifier = Modifier.padding(end = 6.dp),
                                            contentDescription = stringResource(id = R.string.settings_always_code),
                                            tint = colorScheme.onBackground
                                        )
                                    },
                                    checked = state.settings.alwaysCode,
                                    onCheckedChange = actions.onSetAlwaysCode
                                )
                                OverlayDropdownPreference(
                                    title = stringResource(id = R.string.settings_sign_location_mode),
                                    items = signLocationItems,
                                    startAction = {
                                        Icon(
                                            Icons.Rounded.LocationOn,
                                            modifier = Modifier.padding(end = 6.dp),
                                            contentDescription = stringResource(id = R.string.settings_sign_location_mode),
                                            tint = colorScheme.onBackground
                                        )
                                    },
                                    selectedIndex = SignLocationMode
                                        .fromValue(state.settings.signLocationMode)
                                        .ordinal,
                                    onSelectedIndexChange = actions.onSetSignLocationMode,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
