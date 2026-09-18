package com.yunmei.vibe.ui.component.material

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Save
import androidx.compose.material.icons.twotone.Share
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.yunmei.vibe.BuildConfig
import com.yunmei.vibe.R
import com.yunmei.vibe.ui.component.ActionMenuItem
import com.yunmei.vibe.ui.component.dialog.rememberLoadingDialog
import com.yunmei.vibe.ui.util.bugreportFileName
import com.yunmei.vibe.ui.util.getBugreportFile

private tailrec fun Context.findComponentActivity(): ComponentActivity? {
    return when (this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.findComponentActivity()
        else -> null
    }
}

/**
 * 「发送日志」弹窗菜单（Material）。
 * 使用与「扫码添加门锁」共用的 [ActionMenuBottomSheet] 组件。
 */
@Composable
fun SendLogBottomSheet(
    onDismiss: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current
    val activity = context.findComponentActivity()
    val logSaved = stringResource(R.string.log_saved)
    val sendLog = stringResource(R.string.send_log)
    val saveLog = stringResource(R.string.save_log)
    val loadingDialog = rememberLoadingDialog()
    val scope = rememberCoroutineScope()

    val exportBugreportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gzip")
    ) { uri: Uri? ->
        if (uri == null) {
            onDismiss()
            return@rememberLauncherForActivityResult
        }
        val lifecycleScope = activity?.lifecycleScope ?: scope
        lifecycleScope.launch {
            loadingDialog.show()
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        getBugreportFile(context).inputStream().use {
                            it.copyTo(output)
                        }
                    }
                }
            } finally {
                loadingDialog.hide()
            }
            onDismiss()
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(logSaved)
        }
    }

    val items = listOf(
        ActionMenuItem(Icons.TwoTone.Save, saveLog) {
            exportBugreportLauncher.launch(bugreportFileName())
        },
        ActionMenuItem(Icons.TwoTone.Share, sendLog) {
            scope.launch {
                val bugreport = loadingDialog.withLoading {
                    withContext(Dispatchers.IO) {
                        getBugreportFile(context)
                    }
                }

                val uri: Uri = FileProvider.getUriForFile(
                    context, "${BuildConfig.APPLICATION_ID}.fileprovider", bugreport
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_STREAM, uri)
                    setDataAndType(uri, "application/gzip")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                context.startActivity(
                    Intent.createChooser(
                        shareIntent, sendLog
                    )
                )
                onDismiss()
            }
        },
    )

    ActionMenuBottomSheet(
        items = items,
        onDismiss = onDismiss,
    )
}
