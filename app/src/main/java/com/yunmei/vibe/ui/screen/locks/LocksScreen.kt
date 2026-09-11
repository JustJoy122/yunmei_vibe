package com.yunmei.vibe.ui.screen.locks

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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.widget.Toast
import com.yunmei.vibe.R
import com.yunmei.vibe.ui.LocalUiMode
import com.yunmei.vibe.ui.UiMode
import com.yunmei.vibe.ui.component.dialog.ConfirmResult
import com.yunmei.vibe.ui.component.dialog.rememberConfirmDialog
import com.yunmei.vibe.ui.navigation3.Navigator
import com.yunmei.vibe.ui.navigation3.Route
import androidx.compose.runtime.saveable.rememberSaveable
import com.yunmei.vibe.ui.component.scan.ScanAddLockMenu
import kotlinx.coroutines.launch

@Composable
fun LocksPager(
    navigator: Navigator,
    bottomInnerPadding: Dp,
    isCurrentPage: Boolean = true,
) {
    val viewModel = viewModel<LocksViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val confirmDialog = rememberConfirmDialog()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var hasActivated by remember { mutableStateOf(false) }
    // 「扫码添加门锁」弹窗菜单（相机扫描 / 相册选择）。
    var showScanMenu by rememberSaveable { mutableStateOf(false) }
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

    // Miuix 侧没有 Snackbar 组件，用 Toast 展示操作结果（Material 侧由模板 SnackBarHost 消费并清空）。
    val uiMode = LocalUiMode.current
    LaunchedEffect(uiState.message, uiMode) {
        val message = uiState.message ?: return@LaunchedEffect
        if (uiMode == UiMode.Miuix) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.onMessageShown()
        }
    }

    val deleteTitle = stringResource(R.string.locks_delete)
    val labelPrefix = stringResource(R.string.lock_detail_label)
    val actions = LocksActions(
        onAddScan = { showScanMenu = true },
        onAddLogin = { navigator.push(Route.Login) },
        onSetDefault = viewModel::setDefault,
        onDelete = { lock ->
            scope.launch {
                val content = labelPrefix + "：" + lock.label
                val result = confirmDialog.awaitConfirm(
                    title = deleteTitle,
                    content = content,
                )
                if (result == ConfirmResult.Confirmed) {
                    viewModel.delete(lock)
                }
            }
        },
        onOpenDetail = { lock -> navigator.push(Route.LockDetail(lock.label)) },
        onMessageShown = viewModel::onMessageShown,
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> LocksPagerMiuix(
            state = uiState,
            actions = actions,
            bottomInnerPadding = bottomInnerPadding,
        )

        UiMode.Material -> LocksPagerMaterial(
            state = uiState,
            actions = actions,
            bottomInnerPadding = bottomInnerPadding,
        )
    }

    // 扫码添加门锁：复用「设置 → 发送日志」同款弹窗菜单组件；
    // 相机/相册取图后解码二维码，交给既有的 addLockFromShare 完成解析与添加。
    ScanAddLockMenu(
        show = showScanMenu,
        onDismissRequest = { showScanMenu = false },
        onDecoded = { raw -> viewModel.addLockFromShare(raw) },
        onError = { message -> viewModel.showMessage(message) },
    )
}
