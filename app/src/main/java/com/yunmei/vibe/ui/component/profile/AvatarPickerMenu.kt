package com.yunmei.vibe.ui.component.profile

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
import com.yunmei.vibe.ui.util.rememberPermissionRequester
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 换头像的来源菜单：拍照 / 从相册选择。
 *
 * 交互与底层控件完全复用「扫码添加门锁」那套（`ScanAddLockMenu`）：
 * - 菜单用 [ActionMenuBottomSheet]（Material）/ [ActionMenuDialog]（Miuix）；
 * - 相册走 Photo Picker（`PickVisualMedia.ImageOnly`，Android 13+ 零权限）；
 * - 相机走 `MediaStore.ACTION_IMAGE_CAPTURE` + FileProvider 临时文件；
 * - 拿到 Uri 后在 IO 线程交给 [AvatarImage.prepare] 裁方 + 压缩，再把字节交给 [onPicked]。
 *
 * 与扫码菜单的差别只在「拿到图片之后做什么」：那边解码二维码，这里压成头像。
 */
@Composable
fun AvatarPickerMenu(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onPicked: (ByteArray) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val title = stringResource(R.string.profile_avatar_change)
    val cameraLabel = stringResource(R.string.scan_camera)
    val galleryLabel = stringResource(R.string.scan_gallery)
    val unreadableText = stringResource(R.string.profile_avatar_unreadable)
    val permissionDeniedText = stringResource(R.string.scan_permission_denied)

    // 相机拍照的临时输出 Uri（FileProvider）。
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val handleImage: (Uri) -> Unit = { uri ->
        scope.launch {
            val bytes = withContext(Dispatchers.IO) { AvatarImage.prepare(context, uri) }
            if (bytes == null || bytes.isEmpty()) onError(unreadableText) else onPicked(bytes)
        }
    }

    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let(handleImage)
    }

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

    // 清单里声明了 CAMERA，启动系统相机前按项目既有做法动态申请。
    val requestCameraPermission = rememberPermissionRequester(
        permissions = arrayOf(Manifest.permission.CAMERA),
        onGranted = launchCamera,
        onDenied = { onError(permissionDeniedText) },
    )

    // 图标分派与项目其他双主题组件一致：Material 用 TwoTone，Miuix 用 Rounded。
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

/** 在私有缓存目录创建拍照临时文件并返回 FileProvider 的 content:// Uri。 */
private fun createCameraOutputUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "avatar_" + System.currentTimeMillis() + ".jpg")
    return FileProvider.getUriForFile(
        context,
        BuildConfig.APPLICATION_ID + ".fileprovider",
        file,
    )
}
