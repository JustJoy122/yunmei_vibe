package com.yunmei.vibe.ui.screen.home

import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.WhereToVote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
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
import com.yunmei.vibe.ui.component.ActionMenuItem
import com.yunmei.vibe.ui.component.miuix.ActionMenuDialog
import com.yunmei.vibe.ui.component.miuix.warningCardContainerColor
import com.yunmei.vibe.ui.component.miuix.warningCardContentColor
import com.yunmei.vibe.ui.theme.isInDarkTheme
import com.yunmei.vibe.ui.theme.LocalEnableBlur
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

/** 未设置默认门锁时首页卡片的灰显透明度（与顶部 Banner 的「未设置默认门锁」状态一致）。 */
private const val DISABLED_CARD_ALPHA = 0.38f

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
                        // 打卡放在「开门」按钮与「获取密码」之间（原顺序为 获取密码 → 打卡）。
                        if (state.showSignButton) {
                            SignCard(state, actions)
                        }
                        if (state.showCodeButton) {
                            CodeCard(state, actions)
                        }
                        DoorOptionsCard(state, actions)
                    }
                    Spacer(Modifier.height(bottomInnerPadding))
                }
            }
        }
    }

    state.signAsk?.let { ask ->
        SignAskDialog(
            ask = ask,
            onChoice = actions.onResolveSignAsk,
            onDismiss = actions.onDismissSignAsk,
        )
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
                MiuixStatusCard(
                    title = stringResource(R.string.home_status_ready_title),
                    subtitle = UnitCodes.name(default.schoolNo) ?: default.schoolNo,
                    footer = default.label.ifBlank { default.mac },
                    icon = Icons.Rounded.CheckCircleOutline,
                    iconTint = if (isDynamicColor) {
                        colorScheme.primary.copy(alpha = 0.8f)
                    } else {
                        Color(0xFF36D167)
                    },
                    containerColor = when {
                        isDynamicColor -> colorScheme.secondaryContainer
                        isInDarkTheme() -> Color(0xFF1A3825)
                        else -> Color(0xFFDFFAE4)
                    },
                    contentColor = null,
                    onClick = { actions.onOpenDetail(default) },
                )
            }

state.lockCount == 0 -> {
                MiuixStatusCard(
                    title = stringResource(R.string.home_status_empty_title),
                    subtitle = stringResource(R.string.home_status_add_action),
                    footer = null,
                    icon = Icons.Rounded.ErrorOutline,
                    iconTint = warningCardContentColor().copy(alpha = 0.8f),
                    containerColor = warningCardContainerColor(),
                    contentColor = warningCardContentColor(),
                    pressFeedback = PressFeedbackType.Sink,
                    onClick = actions.onOpenLocks,
                )
            }

else -> {
                MiuixStatusCard(
                    title = stringResource(R.string.home_status_no_default_title),
                    subtitle = stringResource(R.string.home_status_set_default_action),
                    footer = null,
                    icon = Icons.Rounded.ErrorOutline,
                    iconTint = warningCardContentColor().copy(alpha = 0.8f),
                    containerColor = warningCardContainerColor(),
                    contentColor = warningCardContentColor(),
                    pressFeedback = PressFeedbackType.Sink,
                    onClick = actions.onOpenLocks,
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
    // 仅在存在默认门锁时可用；未就绪时灰显且不响应点击（原因由顶部 Banner 说明）。
    val enabled = state.defaultLock != null
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else DISABLED_CARD_ALPHA),
        onClick = if (enabled) actions.onOpenDoor else null,
        showIndication = enabled,
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
private fun SignAskDialog(
    ask: SignAskState,
    onChoice: (SignAskChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    // 复用项目自带的通用弹窗菜单 ActionMenuDialog（ui/component/miuix/ActionMenuDialog.kt，
    // 「发送日志」「扫码添加门锁」用的同一组件）：标题 + 上次位置一行 + 图标操作行 + 底部取消。
    // 颜色全部取自 MiuixTheme.colorScheme，自动适配莫奈开/关两套色板。
    ActionMenuDialog(
        show = true,
        title = stringResource(R.string.unlock_sign_ask_title),
        summary = stringResource(R.string.unlock_sign_ask_msg, ask.lastLocation.ifBlank { "—" }),
        items = listOf(
            ActionMenuItem(Icons.Rounded.History, stringResource(R.string.unlock_sign_use_last)) {
                onChoice(SignAskChoice.USE_LAST)
            },
            ActionMenuItem(Icons.Rounded.LocationOn, stringResource(R.string.unlock_sign_locate)) {
                onChoice(SignAskChoice.LOCATE_ONLY)
            },
            ActionMenuItem(Icons.Rounded.Save, stringResource(R.string.unlock_sign_relocate)) {
                onChoice(SignAskChoice.RELOCATE_SAVE)
            },
        ),
        onDismissRequest = onDismiss,
    )
}

@Composable
private fun CodeCard(
    state: HomeUiState,
    actions: HomeActions,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        // 获取密码：仅在存在默认门锁时可用。Miuix 的 BasicComponent 在 enabled = false 时
        // 不会挂 clickable，所以既没有按压涟漪也没有点击行为，文字走组件自带禁用色。
        val enabled = state.defaultLock != null
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
            enabled = enabled,
            onClick = if (enabled) actions.onGetCode else null,
        )
        // 自动获取密码：开关本身不参与禁用逻辑，位置移到按钮下方。
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
    }
}

@Composable
private fun SignCard(
    state: HomeUiState,
    actions: HomeActions,
) {
    // 仅在存在默认门锁时可用；未就绪时由 BasicComponent 自带的禁用配色灰显、
    // 且不挂 clickable（无涟漪无点击），原因由顶部 Banner 说明。
    val enabled = state.defaultLock != null
    Card(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
            title = stringResource(R.string.unlock_sign),
            summary = state.signMessage ?: signLocationLabel(state.settings.signLocationMode),
            startAction = {
                Icon(
                    Icons.Rounded.WhereToVote,
                    tint = colorScheme.onSurface,
                    contentDescription = null,
                )
            },
            enabled = enabled,
            onClick = if (enabled) actions.onSign else null,
        )
    }
}


/**
 * 首页异常状态卡（Miuix）：与正常状态卡完全相同的版式——
 * 左侧标题 + 副标题，右侧一个巨大圆形感叹号图标被卡片右边缘裁切。
 *
 * 颜色沿用现有的错误语义色（warningCardContainerColor / warningCardContentColor），
 * 图标用同一内容色，保证与容器有足够对比度。
 */
@Composable
private fun MiuixStatusCard(
    title: String,
    subtitle: String?,
    footer: String?,
    icon: ImageVector,
    iconTint: Color,
    containerColor: Color,
    contentColor: Color? = null,
    pressFeedback: PressFeedbackType = PressFeedbackType.Tilt,
    onClick: () -> Unit,
) {
    // 首页三种状态共用这一种版式：左侧标题/副标题/底部补充信息，右侧一个巨大圆图标被卡片右边缘裁切。
    // 只通过参数区分文案、图标、语义色与点击行为，不再存在"扁平小图标"的异常版式。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = if (contentColor != null) {
                CardDefaults.defaultColors(color = containerColor, contentColor = contentColor)
            } else {
                CardDefaults.defaultColors(color = containerColor)
            },
            onClick = onClick,
            showIndication = true,
            pressFeedbackType = pressFeedback,
        ) {
            Box {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(27.dp, 31.dp),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    Icon(
                        modifier = Modifier.size(110.dp),
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp, 10.dp),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    if (footer != null) {
                        Text(
                            text = footer,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp, 14.dp),
                    contentAlignment = Alignment.TopStart,
                ) {
                    Column {
                        Text(
                            text = title,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (subtitle != null) {
                            Spacer(Modifier.height(1.dp))
                            Text(
                                text = subtitle,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}