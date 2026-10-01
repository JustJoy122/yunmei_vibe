package com.yunmei.vibe.ui.unlock

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.yunmei.vibe.R
import com.yunmei.vibe.ui.util.BLE_PERMISSIONS
import com.yunmei.vibe.ui.util.PermissionRequester

/**
 * 「开门」快捷方式 / 「开启蓝牙」通知动作的入口 Activity：完全无界面。
 *
 * 权限处理遵循 Android 官方做法：**在承载流程的 Activity 里用 registerForActivityResult
 * 直接发起系统请求**，授权后继续开门，拒绝就发失败通知并结束，不会引导用户去打开 App 主界面。
 *
 * 顺序：令牌校验 → 通知权限（Android 13+）→ 蓝牙运行时权限（Android 12+ 的 BLUETOOTH_CONNECT）
 * → 蓝牙总开关（ACTION_REQUEST_ENABLE）→ 启动 UnlockService 执行开门。
 * 系统对话框若被后台启动限制拦下，退化为高优先级通知，由用户点击后再请求；
 * 通知动作/点击产生的 PendingIntent.getActivity 属于系统认可的用户主动启动路径，
 * 不受 Android 10+ 后台启动 Activity 限制约束。
 */
class UnlockShortcutActivity : ComponentActivity() {

    private val permissionRequester = PermissionRequester(this)

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startUnlockAndFinish()
        } else {
            startServiceWithBluetoothDenied()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val launchedByShortcut = intent?.action == UnlockShortcut.ACTION &&
            intent?.getStringExtra(UnlockShortcut.EXTRA_TOKEN) == UnlockShortcut.token(this)
        if (!launchedByShortcut) {
            finish()
            return
        }
        proceed()
    }

    /** 依次补齐通知权限与蓝牙权限，缺哪个就请求哪个；全部就绪后检查蓝牙总开关。 */
    private fun proceed() {
        if (needNotificationPermission()) {
            permissionRequester.request(
                permissions = arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                onGranted = { proceed() },
                // 通知权限被拒绝不影响开门（只是没有进度反馈），继续走蓝牙分支。
                onDenied = { checkBluetoothPermission() },
            )
            return
        }
        checkBluetoothPermission()
    }

    private fun checkBluetoothPermission() {
        if (!hasBlePermissions()) {
            permissionRequester.request(
                permissions = BLE_PERMISSIONS,
                onGranted = { checkBluetoothEnabled() },
                onDenied = {
                    // 用户拒绝授权：发失败通知并结束流程，不停在中间态。
                    UnlockNotifications.post(
                        this,
                        UnlockNotifications.result(
                            this,
                            false,
                            getString(R.string.unlock_shortcut_no_permission),
                        ),
                    )
                    finish()
                },
            )
            return
        }
        checkBluetoothEnabled()
    }

    private fun checkBluetoothEnabled() {
        if (!isBluetoothEnabled()) {
            requestBluetoothEnable()
            return
        }
        startUnlockAndFinish()
    }

    /** 用系统对话框请求开启蓝牙；同意后继续开门，拒绝则发失败通知。 */
    private fun requestBluetoothEnable() {
        val launched = runCatching {
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }.isSuccess
        if (launched) return

        // 受后台启动 Activity 限制时的兜底：高优先级通知 + PendingIntent.getActivity 引导用户点击。
        UnlockNotifications.post(
            this,
            UnlockNotifications.bluetoothFailure(
                this,
                getString(R.string.unlock_bluetooth_disabled),
                listOf(
                    UnlockNotifications.enableBluetoothAction(this),
                    UnlockNotifications.finishAction(this),
                ),
            ),
        )
        finish()
    }

    /** 用户拒绝开启蓝牙：交给服务发出失败通知（保留实况与进度条，动作重试/完成）。 */
    private fun startServiceWithBluetoothDenied() {
        runCatching {
            ContextCompat.startForegroundService(
                this,
                Intent(this, UnlockService::class.java)
                    .putExtra(UnlockService.EXTRA_BT_DENIED, true),
            )
        }
        finish()
    }

    private fun startUnlockAndFinish() {
        runCatching {
            ContextCompat.startForegroundService(this, Intent(this, UnlockService::class.java))
        }
        finish()
    }

    private fun needNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

    private fun hasBlePermissions(): Boolean = BLE_PERMISSIONS.all { permission ->
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun isBluetoothEnabled(): Boolean =
        runCatching { BluetoothAdapter.getDefaultAdapter()?.isEnabled == true }.getOrDefault(false)
}