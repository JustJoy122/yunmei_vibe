package com.yunmei.vibe.ui.screen.home

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yunmei.vibe.R
import com.yunmei.vibe.ui.LocalMainPagerState
import com.yunmei.vibe.ui.LocalUiMode
import com.yunmei.vibe.ui.SignLocationMode
import com.yunmei.vibe.ui.UiMode
import com.yunmei.vibe.ui.component.dialog.ConfirmResult
import com.yunmei.vibe.ui.component.dialog.rememberConfirmDialog
import com.yunmei.vibe.ui.navigation3.Navigator
import com.yunmei.vibe.ui.navigation3.Route
import com.yunmei.vibe.ui.util.BLE_PERMISSIONS
import com.yunmei.vibe.ui.util.LOCATION_PERMISSIONS
import com.yunmei.vibe.ui.util.rememberPermissionRequester
import com.yunmei.vibe.ui.viewmodel.MainPagerConfig
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Composable
fun HomePager(
    navigator: Navigator,
    bottomInnerPadding: Dp,
    isCurrentPage: Boolean = true,
    autoOpenTrigger: StateFlow<Long> = kotlinx.coroutines.flow.MutableStateFlow(0L),
) {
    val viewModel = viewModel<HomeViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val confirmDialog = rememberConfirmDialog()
    val scope = rememberCoroutineScope()
    val mainPagerState = LocalMainPagerState.current

    var hasActivated by remember { mutableStateOf(false) }
    if (isCurrentPage) hasActivated = true

    if (hasActivated) {
        LaunchedEffect(Unit) {
            viewModel.refresh()
        }
    }
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    // 自动开门触发（首页「自动开门」开关）：去重后执行一次开门。
    val autoTrigger by autoOpenTrigger.collectAsStateWithLifecycle()
    LaunchedEffect(autoTrigger) {
        viewModel.onAutoOpenTrigger(autoTrigger)
    }

    // 开门成功 + 自动退出：提示后退出应用（原项目 autoExit 行为）。
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                HomeEvent.AutoExitRequested -> {
                    Toast.makeText(context, R.string.unlock_auto_exit, Toast.LENGTH_LONG).show()
                    val activity = context as? android.app.Activity ?: return@collect
                    Handler(Looper.getMainLooper()).postDelayed({
                        activity.finish()
                    }, 3000)
                }
            }
        }
    }

    val hasAllPermissions: (Array<String>) -> Boolean = { permissions ->
        permissions.all {
            ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }
    val bleRequester = rememberPermissionRequester(
        permissions = BLE_PERMISSIONS,
        onGranted = { viewModel.openDoor() },
        onDenied = { viewModel.openDoorDenied() },
    )
    val locationRequester = rememberPermissionRequester(
        permissions = LOCATION_PERMISSIONS,
        onGranted = { viewModel.sign() },
        onDenied = { viewModel.signLocationDenied() },
    )

    // 「自动开门」+「开门后自动退出」同时开启：三重危险确认（原项目行为，随开关迁至首页）。
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
            val r1 = confirmDialog.awaitConfirm(dangerTitle1, dangerMsg1, confirm = dangerConfirm, dismiss = dangerGiveUp)
            if (r1 != ConfirmResult.Confirmed) return@launch
            val r2 = confirmDialog.awaitConfirm(dangerTitle2, dangerMsg2, confirm = dangerConfirm, dismiss = dangerGiveUp)
            if (r2 != ConfirmResult.Confirmed) return@launch
            val r3 = confirmDialog.awaitConfirm(dangerTitle3, dangerMsg3, confirm = dangerConfirm, dismiss = dangerGiveUp)
            if (r3 != ConfirmResult.Confirmed) return@launch
            apply()
        }
    }

    fun onSetAutoConnect(value: Boolean) {
        if (value && uiState.settings.autoExit) {
            requestDangerToggle { viewModel.setAutoConnect(value) }
        } else {
            viewModel.setAutoConnect(value)
        }
    }

    fun onSetAutoExit(value: Boolean) {
        if (value && uiState.settings.autoConnect) {
            requestDangerToggle { viewModel.setAutoExit(value) }
        } else {
            viewModel.setAutoExit(value)
        }
    }

    val actions = HomeActions(
        onOpenDetail = { lock -> navigator.push(Route.LockDetail(lock.label)) },
        onOpenLocks = { mainPagerState.animateToPage(MainPagerConfig.PAGE_LOCKS) },
        onOpenDoor = {
            if (hasAllPermissions(BLE_PERMISSIONS)) {
                viewModel.openDoor()
            } else {
                bleRequester()
            }
        },
        onSetAutoConnect = ::onSetAutoConnect,
        onSetAutoExit = ::onSetAutoExit,
        onSetAutoCode = viewModel::setAutoCode,
        onGetCode = viewModel::getCode,
        onSign = {
            // 使用上次位置不需要定位权限；其他模式需要。
            if (SignLocationMode.fromValue(uiState.settings.signLocationMode) == SignLocationMode.LAST) {
                viewModel.sign()
            } else if (hasAllPermissions(LOCATION_PERMISSIONS)) {
                viewModel.sign()
            } else {
                locationRequester()
            }
        },
        onResolveSignAsk = viewModel::resolveSignAsk,
        onDismissSignAsk = viewModel::dismissSignAsk,
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> HomePagerMiuix(
            state = uiState,
            actions = actions,
            bottomInnerPadding = bottomInnerPadding,
        )

        UiMode.Material -> HomePagerMaterial(
            state = uiState,
            actions = actions,
            bottomInnerPadding = bottomInnerPadding,
        )
    }
}

/**
 * 打卡卡片的默认副标题：把内部定位模式映射成中文文案（取值、顺序、文案统一见 [SignLocationMode]）。
 * 此前直接把模式原始值拼在标题后面，界面上会出现「打卡位置询问 · lst」这类无意义文本。
 */
@Composable
internal fun signLocationLabel(mode: String): String =
    stringResource(SignLocationMode.fromValue(mode).labelRes)
