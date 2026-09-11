package com.yunmei.vibe.ui.component.miuix

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.yunmei.vibe.BuildConfig
import com.yunmei.vibe.R
import com.yunmei.vibe.ui.component.ActionMenuItem
import com.yunmei.vibe.ui.component.dialog.LoadingDialogHandle
import com.yunmei.vibe.ui.util.bugreportFileName
import com.yunmei.vibe.ui.util.getBugreportFile

/**
 * 「发送日志」弹窗菜单（Miuix）。
 * 使用与「扫码添加门锁」共用的 [ActionMenuDialog] 组件。
 */
@Composable
fun SendLogDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    loadingDialog: LoadingDialogHandle,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val logSavedText = stringResource(R.string.log_saved)
    val sendLogText = stringResource(R.string.send_log)
    val saveLogText = stringResource(R.string.save_log)
    val title = stringResource(R.string.send_log)

    val exportBugreportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gzip")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            loadingDialog.show()
            context.contentResolver.openOutputStream(uri)?.use { output ->
                getBugreportFile(context).inputStream().use {
                    it.copyTo(output)
                }
            }
            loadingDialog.hide()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, logSavedText, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val items = listOf(
        ActionMenuItem(Icons.Rounded.Save, saveLogText) {
            exportBugreportLauncher.launch(bugreportFileName())
            onDismissRequest()
        },
        ActionMenuItem(Icons.Rounded.Share, sendLogText) {
            scope.launch {
                onDismissRequest()
                val bugreport = loadingDialog.withLoading {
                    withContext(Dispatchers.IO) {
                        getBugreportFile(context)
                    }
                }

                val uri: Uri =
                    FileProvider.getUriForFile(
                        context,
                        "${BuildConfig.APPLICATION_ID}.fileprovider",
                        bugreport
                    )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_STREAM, uri)
                    setDataAndType(uri, "application/gzip")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                context.startActivity(
                    Intent.createChooser(
                        shareIntent,
                        sendLogText
                    )
                )
            }
        },
    )

    ActionMenuDialog(
        show = show,
        title = title,
        items = items,
        onDismissRequest = onDismissRequest,
    )
}
