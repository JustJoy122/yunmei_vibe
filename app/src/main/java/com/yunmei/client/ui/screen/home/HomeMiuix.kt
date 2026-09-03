package com.yunmei.client.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.WhereToVote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunmei.client.R
import com.yunmei.client.data.model.UnitCodes
import com.yunmei.client.ui.theme.LocalEnableBlur
import com.yunmei.client.ui.util.BlurredBar
import com.yunmei.client.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun HomePagerMiuix(
    state: HomeUiState,
    actions: HomeActions,
    bottomInnerPadding: Dp,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface
    Scaffold(
        topBar = {
            TopBar(
                scrollBehavior = scrollBehavior,
                backdrop = backdrop,
                barColor = barColor,
            )
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = 12.dp),
                contentPadding = innerPadding,
                overscrollEffect = null,
            ) {
                item {
                    Column(
                        modifier = Modifier.padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // 状态 Banner + 高频操作（开门/取码/打卡），旧版门锁信息卡已并入 Banner。
                        LockStatusCardMiuix(state, actions)
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
                    }
                    Spacer(Modifier.height(bottomInnerPadding))
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    scrollBehavior: ScrollBehavior,
    backdrop: LayerBackdrop?,
    barColor: Color,
) {
    BlurredBar(backdrop) {
        TopAppBar(
            color = barColor,
            title = stringResource(R.string.app_name),
            scrollBehavior = scrollBehavior
        )
    }
}

@Composable
private fun LockStatusCardMiuix(
    state: HomeUiState,
    actions: HomeActions,
) {
    // 状态 Banner（模板 PermissionCardMiuix 结构）：仅当显式设置了默认门锁才显示绿色。
    val ready = state.defaultLock != null
    val empty = state.lockCount == 0
    val default = state.defaultLock
    val iconColor = if (ready) Color(0xFF36D167) else Color(0xFFF72727)
    val containerColor = if (ready) Color(0xFFDFFAE4) else Color(0xFFF8E2E2)
    val textColor = Color(0xFF111111)
    val title =
        if (ready) {
            stringResource(R.string.home_status_ready_title)
        } else if (empty) {
            stringResource(R.string.home_status_empty_title)
        } else {
            stringResource(R.string.home_status_no_default_title)
        }
    // 副标题：绿色显示单位名称；红色无副标题。
    val summary: String? =
        if (ready && default != null) {
            UnitCodes.name(default.schoolNo) ?: default.schoolNo
        } else {
            null
        }
    val action =
        if (ready && default != null) {
            // 底部操作区域：绿色直接显示当前门锁号（标签）。
            default.label.ifBlank { default.mac }
        } else if (empty) {
            stringResource(R.string.home_status_add_action)
        } else {
            stringResource(R.string.home_status_set_default_action)
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = containerColor),
        onClick = {
            if (default != null) {
                actions.onOpenDetail(default)
            } else {
                actions.onOpenLocks()
            }
        },
        showIndication = true,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(164.dp)
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = 70.dp, y = 44.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Icon(
                    modifier = Modifier.size(182.dp),
                    imageVector =
                        if (ready) Icons.Rounded.CheckCircleOutline else Icons.Rounded.Cancel,
                    tint = iconColor,
                    contentDescription = null,
                )
            }
            Column(
                modifier = Modifier
                    .matchParentSize()
                    .padding(start = 24.dp, top = 28.dp, end = 148.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor,
                    )
                    summary?.let {
                        Text(
                            text = it,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = textColor.copy(alpha = 0.72f),
                        )
                    }
                }
                Text(
                    text = action,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor.copy(alpha = 0.78f),
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = actions.onOpenDoor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { state.progress / 100f },
                    modifier = Modifier.size(112.dp),
                    color = colorScheme.primary,
                    trackColor = colorScheme.secondaryContainer,
                    strokeWidth = 8.dp,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${state.progress}%",
                        fontSize = MiuixTheme.textStyles.headline1.fontSize,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.unlock_open),
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            Text(
                text = state.statusText.ifBlank { stringResource(R.string.unlock_ready) },
                fontSize = MiuixTheme.textStyles.headline2.fontSize,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
            )
            state.battery?.let { battery ->
                Text(
                    text = stringResource(R.string.unlock_battery) + "：$battery%",
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = colorScheme.onSurfaceVariantSummary,
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
    Card(modifier = Modifier.fillMaxWidth()) {
        SwitchPreference(
            title = stringResource(R.string.unlock_quick_connect),
            summary = stringResource(R.string.unlock_quick_connect_summary),
            startAction = {
                Icon(
                    Icons.Rounded.Bluetooth,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = stringResource(R.string.unlock_quick_connect),
                    tint = colorScheme.onBackground,
                )
            },
            checked = state.quickConnect,
            onCheckedChange = actions.onSetQuickConnect,
        )
    }
}

@Composable
private fun DoorOptionsCard(
    state: HomeUiState,
    actions: HomeActions,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        SwitchPreference(
            title = stringResource(R.string.settings_auto_connect),
            summary = stringResource(R.string.settings_auto_connect_summary),
            startAction = {
                Icon(
                    Icons.Rounded.AutoMode,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = stringResource(R.string.settings_auto_connect),
                    tint = colorScheme.onBackground,
                )
            },
            checked = state.settings.autoConnect,
            onCheckedChange = actions.onSetAutoConnect,
        )
        SwitchPreference(
            title = stringResource(R.string.settings_auto_exit),
            summary = stringResource(R.string.settings_auto_exit_summary),
            startAction = {
                Icon(
                    Icons.AutoMirrored.Rounded.ExitToApp,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = stringResource(R.string.settings_auto_exit),
                    tint = colorScheme.onBackground,
                )
            },
            checked = state.settings.autoExit,
            onCheckedChange = actions.onSetAutoExit,
        )
    }
}

@Composable
private fun SignAskCard(
    ask: SignAskState,
    actions: HomeActions,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.unlock_sign_ask_title),
                    fontSize = MiuixTheme.textStyles.headline2.fontSize,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.unlock_sign_ask_msg, ask.lastLocation.ifBlank { "—" }),
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = colorScheme.onSurfaceVariantSummary,
                )
            }
            BasicComponent(
                title = stringResource(R.string.unlock_sign_relocate),
                onClick = { actions.onResolveSignAsk(SignAskChoice.RELOCATE_SAVE) },
            )
            BasicComponent(
                title = stringResource(R.string.unlock_sign_locate),
                onClick = { actions.onResolveSignAsk(SignAskChoice.LOCATE_ONLY) },
            )
            BasicComponent(
                title = stringResource(R.string.unlock_sign_use_last),
                onClick = { actions.onResolveSignAsk(SignAskChoice.USE_LAST) },
            )
            BasicComponent(
                title = stringResource(R.string.cancel),
                onClick = actions.onDismissSignAsk,
            )
        }
    }
}

@Composable
private fun CodeCard(
    state: HomeUiState,
    actions: HomeActions,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        SwitchPreference(
            title = stringResource(R.string.settings_auto_code),
            summary = stringResource(R.string.settings_auto_code_summary),
            startAction = {
                Icon(
                    Icons.Rounded.Password,
                    modifier = Modifier.padding(end = 6.dp),
                    contentDescription = stringResource(R.string.settings_auto_code),
                    tint = colorScheme.onBackground,
                )
            },
            checked = state.settings.autoCode,
            onCheckedChange = actions.onSetAutoCode,
        )
        BasicComponent(
            title = stringResource(R.string.unlock_get_code),
            summary = state.code ?: state.codeError
            ?: stringResource(R.string.unlock_code_hint),
            summaryColor = if (state.codeError != null) {
                top.yukonga.miuix.kmp.basic.BasicComponentDefaults.summaryColor(color = Color.Red)
            } else {
                top.yukonga.miuix.kmp.basic.BasicComponentDefaults.summaryColor()
            },
            startAction = {
                Icon(
                    Icons.Rounded.Key,
                    tint = colorScheme.onSurface,
                    contentDescription = null,
                )
            },
            onClick = actions.onGetCode,
        )
    }
}

@Composable
private fun SignCard(
    state: HomeUiState,
    actions: HomeActions,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
            title = stringResource(R.string.unlock_sign),
            summary = state.signMessage
                ?: stringResource(R.string.unlock_sign_ask_title) + " · " + state.settings.signLocationMode,
            startAction = {
                Icon(
                    Icons.Rounded.WhereToVote,
                    tint = colorScheme.onSurface,
                    contentDescription = null,
                )
            },
            onClick = actions.onSign,
        )
    }
}
