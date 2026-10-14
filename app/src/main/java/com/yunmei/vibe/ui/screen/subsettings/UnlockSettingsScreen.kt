package com.yunmei.vibe.ui.screen.subsettings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yunmei.vibe.R
import com.yunmei.vibe.ui.LocalUiMode
import com.yunmei.vibe.ui.UiMode
import com.yunmei.vibe.ui.component.dialog.ConfirmResult
import com.yunmei.vibe.ui.component.dialog.rememberConfirmDialog
import com.yunmei.vibe.ui.navigation.LocalNavigator
import com.yunmei.vibe.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

/**
 * 二级设置页「开锁」：快速连接 / 自动开门 / 开门后自动退出。
 *
 * 与「主题设置」二级页同款结构（共享 SettingsViewModel + 按 UiMode 分发 Material / Miuix 实现）。
 */
@Composable
fun UnlockSettingsScreen() {
    val navigator = LocalNavigator.current
    val viewModel = viewModel<SettingsViewModel>()
    val functionalState by viewModel.functionalState.collectAsStateWithLifecycle()
    val confirmDialog = rememberConfirmDialog()
    val scope = rememberCoroutineScope()

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val state = UnlockSettingsUiState(settings = functionalState.settings)
    val onBack = dropUnlessResumed { navigator.pop() }

    // 「自动开门」与「开门后自动退出」互斥：同时开启会变成「启动即开门、开门即退出」。
    // 这段三级危险确认原样沿用首页开门卡片（HomeScreen.requestDangerToggle）的行为——只搬位置，不改逻辑。
    val dangerTitle1 = stringResource(R.string.settings_danger_title)
    val dangerMsg1 = stringResource(R.string.settings_danger_msg)
    val dangerTitle2 = stringResource(R.string.settings_danger_again_title)
    val dangerMsg2 = stringResource(R.string.settings_danger_again_msg)
    val dangerTitle3 = stringResource(R.string.settings_danger_final_title)
    val dangerMsg3 = stringResource(R.string.settings_danger_final_msg)
    val dangerConfirm = stringResource(R.string.settings_danger_confirm)
    val dangerGiveUp = stringResource(R.string.settings_danger_give_up)

    fun requestDangerToggle(apply: () -> Unit) {
        scope.launch {
            val r1 = confirmDialog.awaitConfirm(
                title = dangerTitle1,
                content = dangerMsg1,
                confirm = dangerConfirm,
                dismiss = dangerGiveUp,
            )
            if (r1 != ConfirmResult.Confirmed) return@launch
            val r2 = confirmDialog.awaitConfirm(
                title = dangerTitle2,
                content = dangerMsg2,
                confirm = dangerConfirm,
                dismiss = dangerGiveUp,
            )
            if (r2 != ConfirmResult.Confirmed) return@launch
            val r3 = confirmDialog.awaitConfirm(
                title = dangerTitle3,
                content = dangerMsg3,
                confirm = dangerConfirm,
                dismiss = dangerGiveUp,
            )
            if (r3 != ConfirmResult.Confirmed) return@launch
            apply()
        }
    }

    fun onSetAutoConnect(value: Boolean) {
        if (value && state.settings.autoExit) {
            requestDangerToggle { viewModel.setAutoConnect(value) }
        } else {
            viewModel.setAutoConnect(value)
        }
    }

    fun onSetAutoExit(value: Boolean) {
        if (value && state.settings.autoConnect) {
            requestDangerToggle { viewModel.setAutoExit(value) }
        } else {
            viewModel.setAutoExit(value)
        }
    }

    val actions = UnlockSettingsActions(
        onBack = onBack,
        onSetQuickConnect = viewModel::setQuickConnect,
        onSetAutoConnect = ::onSetAutoConnect,
        onSetAutoExit = ::onSetAutoExit,
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> UnlockSettingsMiuix(state, actions)
        UiMode.Material -> UnlockSettingsMaterial(state, actions)
    }
}
