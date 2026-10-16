package com.yunmei.vibe.ui.util

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat

/**
 * 跳板 Activity 的公共基类。
 *
 * 5 个无界面跳板（打卡/开门 × 快捷方式/通知动作，外加定位设置转发页）此前各自手写同一批逻辑，
 * 这里收敛为 5 个受保护方法；各跳板只保留自己的差异：转发的 action 常量、目标服务、
 * 是否需要令牌校验、以及各自的权限补齐流程。
 *
 * 注意：跳板的启动路径可能来自系统点击通知动作，属于受 Android 10+ 后台启动限制约束的场景，
 * 因此"先进入前台 Activity 再启动前台服务"的时序不能被改变——基类只做等价改写，不改变顺序。
 */
abstract class TrampolineActivity : ComponentActivity() {

    /** 令牌比较：只有携带与 expected 完全一致的令牌才放行。 */
    protected fun hasValidToken(provided: String?, expected: String): Boolean =
        provided != null && provided == expected

    /** Android 13+ 且尚未授予通知权限。 */
    protected fun needNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

    /** 是否已授予全部蓝牙运行时权限（见 [BLE_PERMISSIONS]）。 */
    protected fun hasBlePermissions(): Boolean = BLE_PERMISSIONS.all { permission ->
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    /** 在当前（前台）上下文启动前台服务并立刻结束本页；失败不抛，保持跳板静默。 */
    protected fun startForegroundServiceAndFinish(intent: Intent) {
        runCatching { ContextCompat.startForegroundService(this, intent) }
        finish()
    }

    /** 在当前上下文启动另一个界面（如系统设置页）并立刻结束本页。 */
    protected fun launchAndFinish(intent: Intent) {
        runCatching { startActivity(intent) }
        finish()
    }
}