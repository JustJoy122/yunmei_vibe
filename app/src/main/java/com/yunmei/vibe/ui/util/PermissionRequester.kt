package com.yunmei.vibe.ui.util

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * 运行时权限请求助手。
 *
 * 实现方式完全基于 Android 官方的 Activity Result API（`registerForActivityResult`）：
 * 由承载流程的 Activity 注册 launcher，检查/请求权限，授权后回调 [request] 的 onGranted
 * 继续原流程，拒绝则回调 onDenied 结束流程——**不会把用户赶去打开 App 主界面**。
 * 需要跳系统设置页（例如定位总开关）的场景走 [launchSettings]，返回后再由调用方复查状态。
 * 本类为项目自行实现（InstallerX 没有快捷方式，也没有蓝牙/定位权限逻辑，无从复用）。
 *
 * 必须在 Activity 创建阶段构造（launcher 只能在此之前注册）。
 */
class PermissionRequester(private val activity: ComponentActivity) {

    private val requestPermissionsLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            onGranted?.invoke()
        } else {
            onDenied?.invoke()
        }
    }

    private val settingsLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { onSettingsReturn?.invoke() }

    private var onGranted: (() -> Unit)? = null
    private var onDenied: (() -> Unit)? = null
    private var onSettingsReturn: (() -> Unit)? = null

    /** 请求一组运行时权限；全部授予后继续，否则结束。 */
    fun request(permissions: Array<String>, onGranted: () -> Unit, onDenied: () -> Unit) {
        this.onGranted = onGranted
        this.onDenied = onDenied
        requestPermissionsLauncher.launch(permissions)
    }

    /** 跳系统设置页（如定位总开关），返回后回调 [onReturn] 由调用方复查状态。 */
    fun launchSettings(intent: Intent, onReturn: () -> Unit) {
        onSettingsReturn = onReturn
        val launched = runCatching { settingsLauncher.launch(intent) }.isSuccess
        if (!launched) {
            onSettingsReturn = null
            onReturn()
        }
    }
}