package com.yunmei.vibe.ui.screen.subsettings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yunmei.vibe.ui.LocalUiMode
import com.yunmei.vibe.ui.UiMode
import com.yunmei.vibe.ui.navigation.LocalNavigator
import com.yunmei.vibe.ui.viewmodel.SettingsViewModel

/**
 * 二级设置页「开锁」：快速连接 / 自动开门 / 开门后自动退出。
 *
 * 与「主题设置」二级页同款结构（共享 SettingsViewModel + 按 UiMode 分发 Material / Miuix 实现）。
 *
 * 互斥确认（自动开门 ↔ 开门后自动退出）的状态机在 [SettingsViewModel.dangerToggle] 里，
 * 不放在这里：Composable 闭包读到的是组合时的 `state` 快照，而 DataStore 写入是异步的，
 * 两次点击间隔可能只有两百毫秒，闭包里的值还没刷新，互斥判断会被绕过（真机已复现）。
 */
@Composable
fun UnlockSettingsScreen() {
    val navigator = LocalNavigator.current
    val viewModel = viewModel<SettingsViewModel>()
    val functionalState by viewModel.functionalState.collectAsStateWithLifecycle()
    val dangerRequest by viewModel.dangerToggle.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val state = UnlockSettingsUiState(
        settings = functionalState.settings,
    )
    val onBack = dropUnlessResumed { navigator.pop() }

    val actions = UnlockSettingsActions(
        onBack = onBack,
        onSetQuickConnect = viewModel::setQuickConnect,
        onSetAutoConnect = viewModel::setAutoConnect,
        onSetAutoExit = viewModel::setAutoExit,
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> UnlockSettingsMiuix(state, actions)
        UiMode.Material -> UnlockSettingsMaterial(state, actions)
    }

    // 确认前不写入偏好，所以任何一级点「放弃」只需清空请求，开关自然回到原位置。
    DangerConfirmDialog(
        level = dangerRequest?.level ?: 0,
        onConfirm = viewModel::confirmDangerToggle,
        onDismiss = viewModel::dismissDangerToggle,
    )
}
