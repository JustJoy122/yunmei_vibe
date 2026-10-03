package com.yunmei.vibe.ui.util

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.yunmei.vibe.R
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
/**
 * 权限/系统开关类提示（系统 Toast，不受 Material / Miuix 主题影响，故不做主题分派）。
 *
 * 去重策略：同一 [key] 在 [MIN_INTERVAL_MS] 内只提示一次，
 * 用于抑制用户快速重复点击时连续弹出的相同提示；不同流程的 key 不同，互不影响。
 */
object PermissionNotice {

    private const val MIN_INTERVAL_MS = 1500L

    private var lastKey: String? = null
    private var lastShownAt: Long = 0L

    /** 展示一次提示；同 key 在冷却时间内重复调用会被忽略。 */
    fun show(context: Context, messageRes: Int, key: String) {
        val now = System.currentTimeMillis()
        if (key == lastKey && now - lastShownAt < MIN_INTERVAL_MS) return
        lastKey = key
        lastShownAt = now
        Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).show()
    }
}
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
        // 先告知用户即将出现的系统权限弹窗在做什么（同流程只提示一次）
        PermissionNotice.show(activity, R.string.permission_request_notice, "runtime_permission")
        this.onGranted = onGranted
        this.onDenied = onDenied
        requestPermissionsLauncher.launch(permissions)
    }

    /** 跳系统设置页（如定位总开关），返回后回调 [onReturn] 由调用方复查状态。 */
    fun launchSettings(intent: Intent, onReturn: () -> Unit) {
        // 即将跳到系统设置页（如定位总开关），先说明原因
        PermissionNotice.show(activity, R.string.permission_settings_notice, "system_settings")
        onSettingsReturn = onReturn
        val launched = runCatching { settingsLauncher.launch(intent) }.isSuccess
        if (!launched) {
            onSettingsReturn = null
            onReturn()
        }
    }
}