package com.yunmei.vibe.ui.sign

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.lifecycleScope
import com.yunmei.vibe.YunMeiApp
import com.yunmei.vibe.ui.SignLocationMode
import com.yunmei.vibe.ui.util.LOCATION_PERMISSIONS
import com.yunmei.vibe.ui.util.PermissionRequester
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 「打卡」快捷方式 / 「授予权限」通知动作的入口 Activity：完全无界面。
 *
 * 与开门跳板同构，权限处理一律在流程内完成（Android 官方 Activity Result API）：
 *  1. 令牌校验；
 *  2. 通知权限（Android 13+）——打卡反馈全靠通知；
 *  3. 按设置里的定位方式判断是否需要定位：需要且未授权 → **直接在流程内请求定位权限**；
 *  4. 定位总开关未开启 → 跳系统定位设置页，返回后继续（SettingsClient 需要 GMS 依赖，本项目不引入，
 *     因此用系统设置页等价实现）；
 *  5. 拒绝授权/仍不可用 → 交给 SignService 发失败通知并结束，不打开 App 主界面。
 *
 * 注：本类及 PermissionRequester 均为自行实现。InstallerX 没有快捷方式与蓝牙/定位权限逻辑，
 * 本项目仅在**通知构建**上复用它的实现（渠道、进度样式、失败分段变红、动作按钮）。
 */
class SignShortcutActivity : ComponentActivity() {

    private val permissionRequester = PermissionRequester(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val launchedByShortcut = intent?.action == SignShortcut.ACTION &&
            intent?.getStringExtra(SignShortcut.EXTRA_TOKEN) == SignShortcut.token(this)
        if (!launchedByShortcut) {
            finish()
            return
        }

        if (needNotificationPermission()) {
            permissionRequester.request(
                permissions = arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                onGranted = { resolveModeAndProceed() },
                onDenied = { resolveModeAndProceed() },
            )
        } else {
            resolveModeAndProceed()
        }
    }

    /** 读设置里的定位方式；"使用上次位置"不需要定位权限，其余两种需要。 */
    private fun resolveModeAndProceed() {
        lifecycleScope.launch {
            val mode = runCatching {
                YunMeiApp.app.container.appPreferences.settings.first().signLocationMode
            }.getOrDefault(SignLocationMode.DEFAULT.value)
            val needsLocation = SignLocationMode.fromValue(mode) != SignLocationMode.LAST
            if (needsLocation && !hasLocationPermission()) {
                permissionRequester.request(
                    permissions = LOCATION_PERMISSIONS,
                    onGranted = { checkLocationEnabled() },
                    onDenied = { startServiceWithDenied() },
                )
            } else {
                checkLocationEnabled()
            }
        }
    }

    private fun checkLocationEnabled() {
        if (isLocationEnabled()) {
            startSignService()
            finish()
            return
        }
        // 定位总开关未开启：直接在流程内跳系统设置页，返回后继续（不再要求先打开 App）。
        permissionRequester.launchSettings(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) {
            startSignService()
            finish()
        }
    }

    private fun startSignService() {
        runCatching {
            ContextCompat.startForegroundService(
                this,
                Intent(this, SignService::class.java).setAction(SignService.ACTION_START),
            )
        }
    }

    /** 权限被拒绝：不做无声失败，发一条失败通知说明原因后结束。 */
    private fun startServiceWithDenied() {
        runCatching {
            ContextCompat.startForegroundService(
                this,
                Intent(this, SignService::class.java)
                    .setAction(SignService.ACTION_PERMISSION_DENIED),
            )
        }
        finish()
    }

    private fun needNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

    private fun hasLocationPermission(): Boolean = LOCATION_PERMISSIONS.all { permission ->
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun isLocationEnabled(): Boolean {
        val lm = getSystemService(LOCATION_SERVICE) as? LocationManager ?: return false
        return runCatching { LocationManagerCompat.isLocationEnabled(lm) }.getOrDefault(false)
    }
}