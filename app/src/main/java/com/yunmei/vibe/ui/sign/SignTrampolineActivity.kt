package com.yunmei.vibe.ui.sign

import com.yunmei.vibe.ui.util.TrampolineActivity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.yunmei.vibe.R
import com.yunmei.vibe.ui.util.LOCATION_PERMISSIONS
import com.yunmei.vibe.ui.util.PermissionRequester

/**
 * 打卡的**前台跳板** Activity：不显示任何 UI，唯一职责是在"用户主动操作"的前台上下文中
 * 启动 [SignService]，然后立刻 finish()。
 *
 * 为什么需要它：Android 12+ 禁止应用在后台启动前台服务，
 * 直接把这些动作做成 `PendingIntent.getForegroundService` 会被系统拒绝——服务根本不会启动，
 * 用户看到的就是"点了没反应"。改成 `PendingIntent.getActivity` 打开本页后，启动服务发生在前台，系统放行。
 *
 * 权限时机（本类负责）：只有在用户**明确选择了需要定位的方式**
 * （`LOCATE_ONLY` / `LOCATE_SAVE`）时才请求定位权限；
 * 选择「使用上次位置」时不会触发任何定位权限请求。被拒绝则发失败通知并结束。
 */
class SignTrampolineActivity : TrampolineActivity() {

    private val permissionRequester = PermissionRequester(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = intent?.getStringExtra(EXTRA_ACTION) ?: SignService.ACTION_START
        val subMode = intent?.getStringExtra(SignService.EXTRA_SUB_MODE)

        // 只有"确实需要定位"的两种模式才请求定位权限
        val needsLocation = action == SignService.ACTION_WITH_MODE &&
            (subMode == SignService.SignSubMode.LOCATE_ONLY.value ||
                subMode == SignService.SignSubMode.LOCATE_SAVE.value)

        if (needsLocation && !hasLocationPermission()) {
            permissionRequester.request(
                permissions = LOCATION_PERMISSIONS,
                onGranted = { startSignService(action, subMode) },
                onDenied = { postPermissionDenied() },
            )
        } else {
            startSignService(action, subMode)
        }
    }

    private fun startSignService(action: String, subMode: String?) {
        val serviceIntent = Intent(this, SignService::class.java).setAction(action)
        if (subMode != null) {
            serviceIntent.putExtra(SignService.EXTRA_SUB_MODE, subMode)
        }
        // 此时本 Activity 处于前台，startForegroundService 不会被后台启动限制拒绝
        startForegroundServiceAndFinish(serviceIntent)
    }

    /** 用户拒绝定位权限：发一条失败通知，然后结束（不启动服务、不打卡）。 */
    private fun postPermissionDenied() {
        runCatching {
            SignNotifications.ensureChannel(this)
            SignNotifications.post(
                this,
                SignNotifications.result(
                    this,
                    false,
                    getString(R.string.unlock_sign_location_denied),
                ),
            )
        }
        finish()
    }

    private fun hasLocationPermission(): Boolean = LOCATION_PERMISSIONS.all { permission ->
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        /** 要转发给 [SignService] 的 action（默认 [SignService.ACTION_START]）。 */
        const val EXTRA_ACTION = "sign_trampoline_action"
    }
}