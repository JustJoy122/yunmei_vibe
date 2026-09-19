package com.yunmei.vibe.ui.component.scan

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.twotone.PhotoCamera
import androidx.compose.material.icons.twotone.PhotoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import com.yunmei.vibe.BuildConfig
import com.yunmei.vibe.R
import com.yunmei.vibe.ui.LocalUiMode
import com.yunmei.vibe.ui.UiMode
import com.yunmei.vibe.ui.component.ActionMenuItem
import com.yunmei.vibe.ui.component.material.ActionMenuBottomSheet
import com.yunmei.vibe.ui.component.miuix.ActionMenuDialog
import com.yunmei.vibe.ui.util.decodeQrFromUri
import com.yunmei.vibe.ui.util.rememberPermissionRequester
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 「扫码添加门锁」弹窗菜单。
 *
 * - 菜单本身复用与「设置 → 发送日志」相同的 [ActionMenuBottomSheet] / [ActionMenuDialog]；
 * - 相册选择走 Photo Picker（`PickVisualMedia`，Android 13+ 零权限，低版本由 AndroidX 自动回退）；
 * - 相机走 `MediaStore.ACTION_IMAGE_CAPTURE` + FileProvider 临时文件（Android 7.0+ 必需）；
 * - 取到图片后统一交给 [decodeQrFromUri] 解码，再由 [onDecoded] 走既有添加门锁逻辑。
 *
 * 全程使用 Activity Result API，不使用已废弃的 startActivityForResult。
 */
@Composable
fun ScanAddLockMenu(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onDecoded: (String) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val title = stringResource(R.string.locks_add_scan)
    val cameraLabel = stringResource(R.string.scan_camera)
    val galleryLabel = stringResource(R.string.scan_gallery)
    val invalidQrText = stringResource(R.string.scan_invalid)
    val unreadableText = stringResource(R.string.scan_image_unreadable)
    val permissionDeniedText = stringResource(R.string.scan_permission_denied)

    // 相机拍照的临时输出 Uri（FileProvider）。
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val handleImage: (Uri) -> Unit = { uri ->
        scope.launch {
            val text = withContext(Dispatchers.IO) { decodeQrFromUri(context, uri) }
            if (text.isNullOrBlank()) onError(invalidQrText) else onDecoded(text)
        }
    }

    // 相册：Photo Picker（无需存储权限；API 26–29 自动回退到系统文件选择器）。
    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let(handleImage)
    }

    // 相机：Activity Result API + ACTION_IMAGE_CAPTURE + FileProvider。
    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            handleImage(uri)
        }
    }

    val launchCamera: () -> Unit = {
        runCatching {
            val uri = createCameraOutputUri(context)
            pendingCameraUri = uri
            takePicture.launch(
                Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    putExtra(MediaStore.EXTRA_OUTPUT, uri)
                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                }
            )
        }.onFailure {
            onError(unreadableText)
        }
    }

    // 若清单声明了 CAMERA 权限，启动系统相机前需动态申请；否则由相机应用自行处理。
    val requestCameraPermission = rememberPermissionRequester(
        permissions = arrayOf(Manifest.permission.CAMERA),
        onGranted = launchCamera,
        onDenied = { onError(permissionDeniedText) },
    )

    // 图标按主题分派（与项目其他双主题组件同一做法）：
    // Material 走 InstallerX 的 AppIcons 体系（TwoTone），Miuix 保持原有的 Rounded 图标。
    val useTwoToneIcons = LocalUiMode.current == UiMode.Material

    val items = listOf(
        ActionMenuItem(
            if (useTwoToneIcons) Icons.TwoTone.PhotoCamera else Icons.Rounded.PhotoCamera,
            cameraLabel,
        ) {
            onDismissRequest()
            requestCameraPermission()
        },
        ActionMenuItem(
            if (useTwoToneIcons) Icons.TwoTone.PhotoLibrary else Icons.Rounded.PhotoLibrary,
            galleryLabel,
        ) {
            onDismissRequest()
            pickMedia.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        },
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> ActionMenuDialog(
            show = show,
            title = title,
            items = items,
            onDismissRequest = onDismissRequest,
        )

        UiMode.Material -> if (show) {
            ActionMenuBottomSheet(
                items = items,
                onDismiss = onDismissRequest,
            )
        }
    }
}

/** 在应用私有缓存目录创建拍照临时文件，并返回 FileProvider 的 content:// Uri。 */
private fun createCameraOutputUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "scan_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(
        context,
        "${BuildConfig.APPLICATION_ID}.fileprovider",
        file,
    )
}
