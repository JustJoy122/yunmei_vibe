package com.yunmei.vibe.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
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
import com.yunmei.vibe.R
import com.yunmei.vibe.data.model.UnitCodes
import com.yunmei.vibe.ui.theme.LocalEnableBlur
import com.yunmei.vibe.ui.theme.isInDarkTheme
import com.yunmei.vibe.ui.util.BlurredBar
import com.yunmei.vibe.ui.util.rememberBlurBackdrop
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
import top.yukonga.miuix.kmp.theme.MiuixTheme.isDynamicColor
import top.yukonga.miuix.kmp.utils.PressFeedbackType
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
    // 首页状态卡：直接复用 KernelSU（KernelSU-main）HomeMiuix.kt StatusCard 原始实现。
    // 状态映射：已设默认门锁 = ksuActive（绿卡）；未设默认 / 无门锁 = 普通卡 + ErrorOutline（两者仅文字不同）。
    Column {
        val ready = state.defaultLock != null
        val default = state.defaultLock

        when {
            ready && default != null -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.defaultColors(
                            color = when {
                                isDynamicColor -> colorScheme.secondaryContainer
                                isInDarkTheme() -> Color(0xFF1A3825)
                                else -> Color(0xFFDFFAE4)
                            }
                        ),
                        onClick = {
                            actions.onOpenDetail(default)
                        },
                        showIndication = true,
                        pressFeedbackType = PressFeedbackType.Tilt
                    ) {
                        Box {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .offset(27.dp, 31.dp),
                                contentAlignment = Alignment.BottomEnd
                            ) {
                                Icon(
                                    modifier = Modifier.size(110.dp),
                                    imageVector = Icons.Rounded.CheckCircleOutline,
                                    tint = if (isDynamicColor) {
                                        colorScheme.primary.copy(alpha = 0.8f)
                                    } else {
                                        Color(0xFF36D167)
                                    },
                                    contentDescription = null
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp, 10.dp),
                                contentAlignment = Alignment.BottomStart,
                            ) {
                                Text(
                                    text = default.label.ifBlank { default.mac },
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp, 14.dp),
                                contentAlignment = Alignment.TopStart,
                            ) {
                                Column {
                                    Text(
                                        text = stringResource(R.string.home_status_ready_title),
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(Modifier.height(1.dp))
                                    Text(
                                        text = UnitCodes.name(default.schoolNo) ?: default.schoolNo,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            state.lockCount == 0 -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            actions.onOpenLocks()
                        },
                        showIndication = true,
                        pressFeedbackType = PressFeedbackType.Sink
                    ) {
                        BasicComponent(
                            title = stringResource(R.string.home_status_empty_title),
                            summary = stringResource(R.string.home_status_add_action),
                            startAction = {
                                Icon(
                                    Icons.Rounded.ErrorOutline,
                                    stringResource(R.string.home_status_empty_title),
                                    modifier = Modifier.padding(end = 6.dp),
                                    tint = colorScheme.onBackground,
                                )
                            },
                        )
                    }
                }
            }

            else -> {
                Card(
                    onClick = {
                        actions.onOpenLocks()
                    },
                    showIndication = true,
                    pressFeedbackType = PressFeedbackType.Sink
                ) {
                    BasicComponent(
                        title = stringResource(R.string.home_status_no_default_title),
                        summary = stringResource(R.string.home_status_set_default_action),
                        startAction = {
                            Icon(
                                Icons.Rounded.ErrorOutline,
                                stringResource(R.string.home_status_no_default_title),
                                modifier = Modifier.padding(end = 6.dp),
                                tint = colorScheme.onBackground,
                            )
                        }
                    )
                }
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
