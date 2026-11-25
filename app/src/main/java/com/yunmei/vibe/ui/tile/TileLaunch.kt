package com.yunmei.vibe.ui.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService

/**
 * 控制中心磁贴的「唤起跳板」封装。
 *
 * 磁贴本身不直接启动前台服务：Android 12 起后台启动前台服务受限，而经过一个透明跳板 Activity
 * 再 `startForegroundService()` 是既有的、已验证可用的路径（桌面快捷方式走的就是这条路）。
 * 因此磁贴只负责把与快捷方式**完全相同**的 Intent 交给 [com.yunmei.vibe.ui.unlock.UnlockShortcutActivity]
 * 或 [com.yunmei.vibe.ui.sign.SignShortcutActivity]，业务逻辑零复制。
 *
 * 两个平台差异：
 *  - API 34 起 `startActivityAndCollapse(Intent)` 会抛 `UnsupportedOperationException`
 *    （见 SDK 源码 TileService.java 的 `START_ACTIVITY_NEEDS_PENDING_INTENT` 校验），必须改用
 *    `startActivityAndCollapse(PendingIntent)`。
 *  - 锁屏状态下直接 startActivity 会被挡在锁屏之下，先 `unlockAndRun` 让系统做解锁校验。
 */
internal object TileLaunch {

    fun launch(service: TileService, intent: Intent) {
        if (service.isLocked) {
            service.unlockAndRun { start(service, intent) }
        } else {
            start(service, intent)
        }
    }

    private fun start(service: TileService, intent: Intent) {
        val launchIntent = Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                service,
                REQUEST_CODE,
                launchIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            service.startActivityAndCollapse(pendingIntent)
        } else {
            startLegacy(service, launchIntent)
        }
    }

    /**
     * Android 13 及以下的兼容路径。
     *
     * `startActivityAndCollapse(Intent)` 自 API 34 起被废弃并在 targetSdk ≥ 34 时直接抛异常，
     * 但低版本没有 `PendingIntent` 重载可用 —— 只能继续调用旧方法。抑制只作用于这个兼容函数，
     * 高版本的 `PendingIntent` 路径不受影响，若那边将来出问题仍会被检出。
     */
    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun startLegacy(service: TileService, intent: Intent) {
        service.startActivityAndCollapse(intent)
    }

    private const val REQUEST_CODE = 0
}
