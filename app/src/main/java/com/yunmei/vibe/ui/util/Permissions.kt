package com.yunmei.vibe.ui.util

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * BLE 开门所需权限（Android 12+ 用蓝牙权限，旧版本用定位权限）。
 *
 * 真实定义在数据层 `com.yunmei.vibe.data.ble.BLE_PERMISSIONS`（服务层与 UnlockFlow 共用），
 * 这里保留同名引用，UI 侧的既有调用点无需改动。
 */
val BLE_PERMISSIONS: Array<String> = com.yunmei.vibe.data.ble.BLE_PERMISSIONS

val LOCATION_PERMISSIONS: Array<String> =
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

/**
 * 统一运行时权限申请入口：返回一个 launcher 函数，授予后回调 [onGranted]。
 * 页面在调用开门/打卡等能力前先调用一次。
 */
@Composable
fun rememberPermissionRequester(
    permissions: Array<String>,
    onGranted: () -> Unit,
    onDenied: (() -> Unit)? = null,
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            onGranted()
        } else {
            onDenied?.invoke()
        }
    }
    return remember(permissions, launcher) {
        { launcher.launch(permissions) }
    }
}
