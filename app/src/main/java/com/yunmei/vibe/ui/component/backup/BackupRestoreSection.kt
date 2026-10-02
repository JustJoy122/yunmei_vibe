package com.yunmei.vibe.ui.component.backup

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yunmei.vibe.R
import com.yunmei.vibe.YunMeiApp
import com.yunmei.vibe.data.backup.BackupManager
import com.yunmei.vibe.ui.LocalUiMode
import com.yunmei.vibe.ui.UiMode
import com.yunmei.vibe.ui.component.material.SegmentedListItem
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference

/** 备份/还原的两个动作。 */
private enum class BackupAction { BACKUP, RESTORE }

/**
 * 安全存储不可用的内联提示（方案 A：设置页顶部，不弹阻断式对话框）。
 *
 * [show] 由调用方按 `AppContainer.secureStoreAvailable` 计算，安全存储恢复后自然隐藏。
 */
@Composable
fun BackupSecureStoreWarning(show: Boolean) {
    if (!show) return
    Text(
        text = stringResource(R.string.secure_store_unavailable),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

/**
 * 口令输入弹窗（双主题）：
 *  - Miuix：项目自有的 `OverlayDialog` + `TextField` + 两个 `TextButton`，
 *    与 `ui/component/miuix/ScaleDialog.kt`（同样是"输入框 + 取消/确定"）保持同一套观感；
 *  - Material：`AlertDialog` + `OutlinedTextField`（项目内没有 Material 版的输入弹窗组件，
 *    见汇报中的核实结论）。
 */
@Composable
private fun PasswordDialog(
    isBackup: Boolean,
    password: String,
    onPasswordChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title = stringResource(
        if (isBackup) R.string.backup_password_title else R.string.restore_password_title
    )
    val hint = stringResource(R.string.backup_password_hint)
    if (LocalUiMode.current == UiMode.Miuix) {
        OverlayDialog(
            show = true,
            title = title,
            summary = hint,
            onDismissRequest = onDismiss,
            content = {
                TextField(
                    modifier = Modifier.padding(bottom = 16.dp),
                    value = password,
                    maxLines = 1,
                    onValueChange = onPasswordChange,
                )
                Row(horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(
                        text = stringResource(android.R.string.cancel),
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(20.dp))
                    TextButton(
                        text = stringResource(android.R.string.ok),
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            },
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = {
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    singleLine = true,
                    label = { Text(hint) },
                )
            },
            confirmButton = {
                TextButton(enabled = password.isNotBlank(), onClick = onConfirm) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

/** 覆盖确认弹窗（双主题，与相邻弹窗同一套组件）。 */
@Composable
private fun OverwriteConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val title = stringResource(R.string.restore_confirm_title)
    val message = stringResource(R.string.restore_confirm_message)
    if (LocalUiMode.current == UiMode.Miuix) {
        OverlayDialog(
            show = true,
            title = title,
            summary = message,
            onDismissRequest = onDismiss,
            content = {
                Row(horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(
                        text = stringResource(android.R.string.cancel),
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(20.dp))
                    TextButton(
                        text = stringResource(android.R.string.ok),
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            },
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = onConfirm) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

/**
 * 设置页的「备份与还原」入口 + 全部弹窗（极简版：全量导出、全量还原，无勾选项）。
 *
 * 入口复用两套主题的现有组件（Material SegmentedListItem / Miuix ArrowPreference）；
 * 文件选择走 SAF：备份 CreateDocument、还原 OpenDocument（与项目里"发送日志"的用法一致）；
 * 口令输入与覆盖确认按 LocalUiMode 分派到各主题自有的对话框组件。
 */
@Composable
fun BackupRestoreEntry() {
    val uiMode = LocalUiMode.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showChooser by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<BackupAction?>(null) }
    var askPassword by remember { mutableStateOf(false) }
    var askOverwrite by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }

    // 备份：选保存位置（SAF CreateDocument，文件名 yunmei-backup-YYYYMMDD.bak）
    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        pendingUri = uri
        pendingAction = BackupAction.BACKUP
        askPassword = true
    }
    // 还原：选备份文件（SAF OpenDocument；识别只看文件头 magic，与后缀无关）
    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        pendingUri = uri
        pendingAction = BackupAction.RESTORE
        askPassword = true
    }

    if (uiMode == UiMode.Miuix) {
        ArrowPreference(
            title = stringResource(R.string.backup_restore_title),
            startAction = {
                Icon(
                    Icons.Rounded.Save,
                    contentDescription = stringResource(R.string.backup_restore_title),
                    modifier = Modifier.padding(end = 6.dp),
                )
            },
            onClick = { showChooser = true },
        )
    } else {
        SegmentedListItem(
            onClick = { showChooser = true },
            headlineContent = { Text(stringResource(R.string.backup_restore_title)) },
            leadingContent = { Icon(Icons.Rounded.Save, null) },
        )
    }

    // 1. 选择动作
    if (showChooser) {
        AlertDialog(
            onDismissRequest = { showChooser = false },
            title = { Text(stringResource(R.string.backup_restore_title)) },
            text = {
                Text(
                    text = stringResource(R.string.backup_restore_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showChooser = false
                    password = ""
                    pendingAction = BackupAction.BACKUP
                    createLauncher.launch(BackupManager.fileName())
                }) { Text(stringResource(R.string.backup_action)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showChooser = false
                    password = ""
                    openLauncher.launch(arrayOf("*/*"))
                }) { Text(stringResource(R.string.restore_action)) }
            },
        )
    }

    // 2. 口令输入（按主题分派）
    if (askPassword) {
        PasswordDialog(
            isBackup = pendingAction == BackupAction.BACKUP,
            password = password,
            onPasswordChange = { password = it },
            onConfirm = {
                askPassword = false
                if (pendingAction == BackupAction.RESTORE) {
                    askOverwrite = true
                } else {
                    runBackup(context, scope, pendingUri, password) { pendingUri = null }
                }
            },
            onDismiss = { askPassword = false; pendingUri = null },
        )
    }

    // 3. 覆盖确认（按主题分派）
    if (askOverwrite) {
        OverwriteConfirmDialog(
            onConfirm = {
                askOverwrite = false
                runRestore(context, scope, pendingUri, password) { pendingUri = null }
            },
            onDismiss = { askOverwrite = false; pendingUri = null },
        )
    }
}

/** 全量导出：读取账号/门锁/设置 → 口令加密 → 写入用户选定的 Uri。 */
private fun runBackup(
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    uri: Uri?,
    password: String,
    done: () -> Unit,
) {
    if (uri == null) { done(); return }
    scope.launch {
        val result = runCatching {
            val out = context.contentResolver.openOutputStream(uri)
                ?: error("无法写入所选位置")
            BackupManager.export(context, password.toCharArray(), out).getOrThrow()
        }
        done()
        Toast.makeText(
            context,
            result.fold(
                onSuccess = { context.getString(R.string.backup_success) },
                onFailure = { context.getString(R.string.backup_failed, it.message ?: "") },
            ),
            Toast.LENGTH_LONG,
        ).show()
    }
}

/** 全量还原：解密 + 校验通过后覆盖写入（失败不触碰现有数据）。 */
private fun runRestore(
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    uri: Uri?,
    password: String,
    done: () -> Unit,
) {
    if (uri == null) { done(); return }
    scope.launch {
        val result = runCatching {
            val input = context.contentResolver.openInputStream(uri)
                ?: error("无法读取所选文件")
            BackupManager.restore(context, password.toCharArray(), input).getOrThrow()
        }
        done()
        Toast.makeText(
            context,
            result.fold(
                onSuccess = { context.getString(R.string.restore_success) },
                onFailure = { context.getString(R.string.restore_failed, it.message ?: "") },
            ),
            Toast.LENGTH_LONG,
        ).show()
    }
}