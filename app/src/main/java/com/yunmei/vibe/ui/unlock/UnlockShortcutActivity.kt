package com.yunmei.vibe.ui.unlock

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.yunmei.vibe.R
import com.yunmei.vibe.ui.util.BLE_PERMISSIONS

/**
 * 「开门」快捷方式的入口 Activity：完全无界面。
 *
 * 校验令牌后拉起 [UnlockService] 执行开门，随即结束自己，不会打开主界面。
 * 直接由第三方启动（令牌不匹配）时静默忽略。
 */
class UnlockShortcutActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val launchedByShortcut = intent?.action == UnlockShortcut.ACTION &&
            intent?.getStringExtra(UnlockShortcut.EXTRA_TOKEN) == UnlockShortcut.token(this)
        if (!launchedByShortcut) {
            finish()
            return
        }

        // Android 13+ 通知需要运行时权限：快捷方式开门的反馈全靠通知，因此在这里请求一次。
        val needNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

        if (needNotificationPermission) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATIONS,
            )
        } else {
            startUnlockAndFinish()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATIONS) {
            // 无论是否授权都继续开门：未授权时通知不可见，但开门本身照常执行。
            startUnlockAndFinish()
        }
    }

    private fun startUnlockAndFinish() {
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
        ContextCompat.startForegroundService(this, Intent(this, UnlockService::class.java))
        finish()
    }

    private fun hasBlePermissions(): Boolean = BLE_PERMISSIONS.all { permission ->
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        const val REQUEST_NOTIFICATIONS = 1001
    }
}
