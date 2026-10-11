package com.yunmei.vibe.ui.unlock

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import com.yunmei.vibe.R
import com.yunmei.vibe.ui.util.BLE_PERMISSIONS
import com.yunmei.vibe.ui.util.PermissionRequester

/**
 * 开门的**前台跳板** Activity：不显示 UI，在用户主动操作的前台上下文中
 * 启动 [UnlockService] 并立即 finish()。
 *
 * 用途：开门失败通知上的「重试 / 完成」按钮。此前它们用
 * `PendingIntent.getForegroundService`，在 Android 12+ 会因"后台启动前台服务"被拒绝，
 * 服务不启动 → 用户点重试毫无反应。改为 getActivity 打开本页后即可正常启动。
 *
 * 权限补齐（本类负责）：「重试」时若缺少蓝牙运行时权限（Android 12+ 的
 * `BLUETOOTH_CONNECT` / 低版本的旧蓝牙权限，见 [BLE_PERMISSIONS]），先重新发起请求：
 *  - `shouldShowRequestPermissionRationale` 为 true（用户上次只是拒绝，未勾选"不再询问"）
 *    → 弹一次说明并再次请求；
 *  - 再次被拒且 rationale 仍为 false（已被永久拒绝）→ 引导用户到本应用的系统设置页开启；
 *  - 用户拒绝时，仍通过 [UnlockService.EXTRA_BT_DENIED] 让服务重新发一条带「重试」的失败通知，
 *    保证用户始终有再次尝试的入口。
 *
 * 安全：沿用 [UnlockShortcutActivity] 的 token 校验，只有携带正确令牌的 Intent 才会执行。
 */
class UnlockTrampolineActivity : ComponentActivity() {

    private val permissionRequester = PermissionRequester(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!isLaunchedByOurselves()) {
            finish()
            return
        }
        val action = intent?.getStringExtra(EXTRA_ACTION) ?: UnlockService.ACTION_RETRY
        if (hasBlePermissions()) {
            startUnlockService(action)
            return
        }
        // 缺少蓝牙权限：先请求，必要时解释，二次被拒则引导到系统设置
        val permanentlyDenied = !shouldShowRequestPermissionRationale(BLE_PERMISSIONS.first())
        if (!permanentlyDenied) {
            Toast.makeText(this, R.string.unlock_permission_rationale, Toast.LENGTH_LONG).show()
        }
        permissionRequester.request(
            permissions = BLE_PERMISSIONS,
            onGranted = { startUnlockService(action) },
            onDenied = { handleDenied(action) },
        )
    }

    /** 用户拒绝后：若已被永久拒绝则引导到系统设置，否则提示；两种情况都让服务补发失败通知。 */
    private fun handleDenied(action: String) {
        val permanentlyDenied = !shouldShowRequestPermissionRationale(BLE_PERMISSIONS.first())
        if (permanentlyDenied) {
            Toast.makeText(this, R.string.unlock_permission_go_settings, Toast.LENGTH_LONG).show()
            permissionRequester.launchSettings(appDetailsIntent()) {
                postBluetoothFailure(action)
            }
        } else {
            Toast.makeText(this, R.string.unlock_permission_denied, Toast.LENGTH_LONG).show()
            postBluetoothFailure(action)
        }
    }

    /** 让服务重新发一条"缺少蓝牙权限"的失败通知（带「重试」），保证仍有重试入口。 */
    private fun postBluetoothFailure(action: String) {
        val serviceIntent = Intent(this, UnlockService::class.java)
            .setAction(action)
            .putExtra(UnlockService.EXTRA_BT_DENIED, true)
        runCatching { ContextCompat.startForegroundService(this, serviceIntent) }
        finish()
    }

    private fun startUnlockService(action: String) {
        val serviceIntent = Intent(this, UnlockService::class.java).setAction(action)
        // 此时本 Activity 处于前台，startForegroundService 不会被后台启动限制拒绝
        runCatching { ContextCompat.startForegroundService(this, serviceIntent) }
        finish()
    }

    private fun appDetailsIntent(): Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun hasBlePermissions(): Boolean = BLE_PERMISSIONS.all { permission ->
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun isLaunchedByOurselves(): Boolean =
        intent?.getStringExtra(UnlockShortcut.EXTRA_TOKEN) == UnlockShortcut.token(this)

    companion object {
        /** 要转发给 [UnlockService] 的 action（默认 [UnlockService.ACTION_RETRY]）。 */
        const val EXTRA_ACTION = "unlock_trampoline_action"
    }
}