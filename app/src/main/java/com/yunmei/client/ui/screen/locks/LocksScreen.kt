package com.yunmei.client.ui.screen.locks

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
import com.yunmei.client.R
import com.yunmei.client.ui.LocalMainPagerState
import com.yunmei.client.ui.LocalUiMode
import com.yunmei.client.ui.UiMode
import com.yunmei.client.ui.component.dialog.ConfirmResult
import com.yunmei.client.ui.component.dialog.rememberConfirmDialog
import com.yunmei.client.ui.navigation3.Navigator
import com.yunmei.client.ui.navigation3.Route
import com.yunmei.client.ui.screen.scan.SCAN_RESULT_KEY
import com.yunmei.client.ui.viewmodel.MainPagerConfig
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

    // 接收扫码页回传的分享内容并添加门锁；添加后切到本页展示结果。
    val mainPagerState = LocalMainPagerState.current
    LaunchedEffect(Unit) {
        navigator.observeResult<String>(SCAN_RESULT_KEY).collect { raw ->
            viewModel.addLockFromShare(raw)
            navigator.clearResult(SCAN_RESULT_KEY)
            mainPagerState.animateToPage(MainPagerConfig.PAGE_LOCKS)
        }
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
        onAddScan = { navigator.push(Route.Scan) },
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
}
