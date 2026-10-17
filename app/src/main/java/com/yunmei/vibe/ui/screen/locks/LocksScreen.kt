package com.yunmei.vibe.ui.screen.locks

import android.content.Context
import android.content.ClipboardManager
import com.yunmei.vibe.ui.navigation.PendingLockImport
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
import com.yunmei.vibe.ui.navigation.Navigator
import com.yunmei.vibe.data.local.StoredUser
import com.yunmei.vibe.ui.navigation.Route
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
    val removeAccountTitle = stringResource(R.string.settings_remove_account)
    val confirmText = stringResource(R.string.confirm)
    val cancelText = stringResource(R.string.cancel)
    // 深链导入：观察到 PendingLockImport 里的原文就交给 VM 解析（解析后由下方确认框等待用户决定）。
    val pendingRaw by PendingLockImport.raw.collectAsStateWithLifecycle()
    LaunchedEffect(pendingRaw) {
        val raw = pendingRaw ?: return@LaunchedEffect
        viewModel.importFrom(raw)
        PendingLockImport.consume()
    }

    // 导入确认框：pendingLock 出现时弹出（两主题共用同一 handle，内容用 Markdown 排版；密钥不回显原文）。
    val clipboardEmptyText = stringResource(R.string.scan_clipboard_empty)
    val importTitle = stringResource(R.string.lock_import_confirm_title)
    val importAddText = stringResource(R.string.lock_import_confirm_add)
    val importSecretPresent = stringResource(R.string.lock_import_secret_present)
    val importSecretAbsent = stringResource(R.string.lock_import_secret_absent)
    val importLabelTag = stringResource(R.string.lock_detail_label)
    val importLockNoTag = stringResource(R.string.lock_detail_lock_no)
    val importSchoolTag = stringResource(R.string.lock_detail_school)
    val importMacTag = stringResource(R.string.lock_detail_mac)
    LaunchedEffect(uiState.pendingLock) {
        val pending = uiState.pendingLock ?: return@LaunchedEffect
        val lines = buildList {
            add("**$importLabelTag**：${pending.label}")
            if (pending.lockNo.isNotBlank()) add("**$importLockNoTag**：${pending.lockNo}")
            if (pending.schoolNo.isNotBlank()) add("**$importSchoolTag**：${pending.schoolNo}")
            if (pending.mac.isNotBlank()) add("**$importMacTag**：${pending.mac}")
            add(if (pending.secret.isNotBlank()) importSecretPresent else importSecretAbsent)
        }
        val result = confirmDialog.awaitConfirm(
            title = importTitle,
            content = lines.joinToString("\n\n"),
            markdown = true,
            confirm = importAddText,
            dismiss = cancelText,
        )
        if (result == ConfirmResult.Confirmed) viewModel.confirmImport() else viewModel.cancelImport()
    }
    val actions = LocksActions(
        onAddScan = { showScanMenu = true },
        onPasteLink = {
            // 仅在用户点击时读剪贴板（Android 10+ 只允许前台/焦点应用读取），不做后台监听。
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val text = clipboard?.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(context)
                ?.toString()
            if (text.isNullOrBlank()) viewModel.showMessage(clipboardEmptyText) else viewModel.importFrom(text)
        },
        onLogin = { navigator.push(Route.Login) },
        onRemoveAccount = { user: StoredUser ->
            scope.launch {
                val result = confirmDialog.awaitConfirm(
                    title = removeAccountTitle,
                    content = user.username,
                    confirm = confirmText,
                    dismiss = cancelText,
                )
                if (result == ConfirmResult.Confirmed) {
                    viewModel.removeAccount(user)
                }
            }
        },
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
        onConfirmImport = viewModel::confirmImport,
        onCancelImport = viewModel::cancelImport,
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
    // 相机/相册取图后解码二维码，交给 importFrom 解析；随后由确认框等待用户决定是否写库。
    ScanAddLockMenu(
        show = showScanMenu,
        onDismissRequest = { showScanMenu = false },
        onDecoded = { raw -> viewModel.importFrom(raw) },
        onError = { message -> viewModel.showMessage(message) },
    )
}
