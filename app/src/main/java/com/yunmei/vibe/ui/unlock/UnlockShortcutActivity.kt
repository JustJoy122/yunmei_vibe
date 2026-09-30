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

/**
 * 「开门」快捷方式（以及「开启蓝牙」通知动作）的入口 Activity：完全无界面。
 *
 * 流程与打卡的 SignShortcutActivity 同构：
 *  1. 校验令牌（快捷方式 Intent 与本应用签发的 PendingIntent 都带令牌），第三方直接启动时静默忽略；
 *  2. Android 13+ 先确保通知权限（开门反馈全靠通知）；
 *  3. 缺蓝牙权限（Android 12+ 的 BLUETOOTH_CONNECT）时不启动前台服务，改用普通通知说明原因；
 *  4. 蓝牙未开启时先用系统对话框请求开启：同意后继续开门；拒绝则交给 UnlockService
 *     发出「蓝牙未开启，无法开门」的失败通知（保留实况与进度条，动作是重试/完成）；
 *  5. 系统对话框若因后台启动限制拉不起来，退化为高优先级通知，由用户点击
 *     （PendingIntent.getActivity 是可靠路径）再发起请求。
 */
class UnlockShortcutActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // 无论是否授权都继续开门：未授权时通知不可见，但开门本身照常执行。
        proceed()
    }

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

        val needNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needNotificationPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            proceed()
        }
    }

    private fun proceed() {
        // Android 14+ 起，缺少蓝牙权限时启动 connectedDevice 类型的前台服务会直接抛异常，
        // 因此先用普通通知说明原因，由用户点通知回到应用内授权。
        if (!hasBlePermissions()) {
            UnlockNotifications.post(
                this,
                UnlockNotifications.result(
                    this,
                    false,
                    getString(R.string.unlock_shortcut_no_permission),
                ),
            )
            finish()
            return
        }
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

    private fun hasBlePermissions(): Boolean = BLE_PERMISSIONS.all { permission ->
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun isBluetoothEnabled(): Boolean =
        runCatching { BluetoothAdapter.getDefaultAdapter()?.isEnabled == true }.getOrDefault(false)
}