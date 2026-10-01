package com.yunmei.vibe.ui.unlock

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.yunmei.vibe.R
import com.yunmei.vibe.YunMeiApp
import com.yunmei.vibe.data.ble.UnlockManager
import com.yunmei.vibe.ui.util.BLE_PERMISSIONS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 快捷方式开门的前台服务（无界面）。
 *
 * 复用 [UnlockManager] 的进度回调：开始时不确定进度、过程中确定进度，成功 / 失败给出结果通知。
 * App 内开门按钮仍走原有 Snackbar 逻辑，与本服务无关。
 */
class UnlockService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val finished = AtomicBoolean(false)

    /** 最近一次进度，用于失败时判断哪个分段变红。 */
    private var lastPercent = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        UnlockNotifications.ensureChannel(this)

        if (intent?.action == ACTION_FINISH) {
            dismissAndStop()
            return START_NOT_STICKY
        }
        if (intent?.getBooleanExtra(EXTRA_BT_DENIED, false) == true) {
            // 用户在系统对话框里拒绝了开启蓝牙：保留实况通知与进度条，动作是重试/完成。
            postBluetoothFailure()
            return START_NOT_STICKY
        }
        // 前台服务必须在 5 秒内 startForeground；同时兜住权限在启动瞬间被撤销等极端情况，
        // 失败时退回普通通知并结束自己，避免整个进程因未调用 startForeground 而崩溃。
        val foregroundStarted = runCatching {
            startForeground(
                UnlockNotifications.NOTIFICATION_ID,
                UnlockNotifications.progress(
                    this,
                    getString(R.string.unlock_notification_preparing),
                    null,
                ),
            )
        }.isSuccess
        if (!foregroundStarted) {
            UnlockNotifications.post(
                this,
                UnlockNotifications.result(
                    this,
                    false,
                    getString(R.string.unlock_shortcut_no_permission),
                ),
            )
            stopSelf()
            return START_NOT_STICKY
        }
        scope.launch { runUnlock() }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun runUnlock() {
        val container = YunMeiApp.app.container
        // 蓝牙未开启（含「重试」时仍关着的情况）：不要闷头失败，给出「开启蓝牙」入口。
        if (!isBluetoothEnabled()) {
            postBluetoothFailure()
            return
        }
        val lock = withContext(Dispatchers.IO) { container.lockStore.getDefault() }
        if (lock == null) {
            finishWithFailure(getString(R.string.unlock_shortcut_no_default))
            return
        }
        if (!hasBlePermissions()) {
            finishWithFailure(getString(R.string.unlock_shortcut_no_permission))
            return
        }
        if (!lock.isUsable) {
            finishWithFailure(getString(R.string.unlock_lock_unusable))
            return
        }
        val quickConnect = withContext(Dispatchers.IO) {
            container.appPreferences.settings.first().quickConnect
        }

        // 兜底超时：底层回调偶发不返回时也要给出结果并退出前台服务。
        val timeoutJob = scope.launch {
            delay(UNLOCK_TIMEOUT_MS)
            finishWithFailure(getString(R.string.unlock_timeout))
        }

        container.unlockManager.openDoor(lock, quickConnect, object : UnlockManager.Listener {
            override fun onProgress(percent: Int, message: String) {
                updateProgress(message, percent)
            }

            override fun onBattery(percent: Int) = Unit

            override fun onSuccess() {
                timeoutJob.cancel()
                finishWithSuccess()
            }

            override fun onFailure(message: String) {
                timeoutJob.cancel()
                finishWithFailure(message)
            }
        })
    }

    private fun updateProgress(text: String, percent: Int) {
        if (finished.get()) return
        Log.d(TAG, "unlock progress=$percent text=$text")
        // 固定 ID 原地更新同一个通知：进度条由真实的进度字段驱动，不靠改标题/正文代替。
        lastPercent = percent
        UnlockNotifications.post(this, UnlockNotifications.progress(this, text, percent))
    }

    private fun finishWithSuccess() {
        if (!finished.compareAndSet(false, true)) return
        UnlockNotifications.post(
            this,
            UnlockNotifications.result(this, true, getString(R.string.unlock_progress_done)),
        )
        stopSelfSafely()
    }

    private fun finishWithFailure(message: String) {
        if (!finished.compareAndSet(false, true)) return
        // 严格对齐 InstallerX：失败不退回传统通知，保留实况与进度条，失败分段变红、进度停在失败处，
        // 通知常驻（ongoing，不 autoCancel），由用户通过「重试 / 完成」结束。
        UnlockNotifications.post(
            this,
            UnlockNotifications.failure(
                this,
                message,
                UnlockNotifications.stageOf(lastPercent),
                listOf(
                    UnlockNotifications.retryAction(this),
                    UnlockNotifications.finishAction(this),
                ),
            ),
        )
        stopSelfSafely()
    }

    /** 蓝牙相关的失败通知：与其它开门失败一致，保留实况与进度条（扫描分段变红），动作是重试/完成。 */
    private fun postBluetoothFailure() {
        if (!finished.compareAndSet(false, true)) return
        val actions = listOf(
            UnlockNotifications.retryAction(this),
            UnlockNotifications.finishAction(this),
        )
        val notification = UnlockNotifications.failure(
            this,
            getString(R.string.unlock_bluetooth_disabled),
            0,
            actions,
        )
        // 该分支由 startForegroundService 拉活，必须先成为前台服务（5 秒规则）；
        // 直接以失败通知充当前台通知，随后 detach，保证通知留在通知栏。
        runCatching { startForeground(UnlockNotifications.NOTIFICATION_ID, notification) }
            .onFailure { UnlockNotifications.post(this, notification) }
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    /** 「完成」：用占位通知顶替同 ID 的通知后立即移除，并按规则先完成 startForeground。 */
    private fun dismissAndStop() {
        val placeholder = android.app.Notification.Builder(this, UnlockNotifications.PROGRESS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_unlock)
            .setContentTitle(getString(R.string.unlock_preparing))
            .build()
        runCatching { startForeground(UnlockNotifications.NOTIFICATION_ID, placeholder) }
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    private fun isBluetoothEnabled(): Boolean =
        runCatching { android.bluetooth.BluetoothAdapter.getDefaultAdapter()?.isEnabled == true }
            .getOrDefault(false)

    private fun stopSelfSafely() {
        // 结果通知保留在通知栏，只撤掉前台服务身份。
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun hasBlePermissions(): Boolean = BLE_PERMISSIONS.all { permission ->
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val TAG = "UnlockService"
        private const val UNLOCK_TIMEOUT_MS = 45_000L

        /** 失败通知的「重试」：重跑一次开门。 */
        const val ACTION_RETRY = "com.yunmei.vibe.action.UNLOCK_RETRY"

        /** 失败通知的「完成」：收起通知并结束服务。 */
        const val ACTION_FINISH = "com.yunmei.vibe.action.UNLOCK_FINISH"

        /** 用户拒绝开启蓝牙：只发失败通知，不执行开门。 */
        const val EXTRA_BT_DENIED = "unlock_bt_denied"
    }
}
