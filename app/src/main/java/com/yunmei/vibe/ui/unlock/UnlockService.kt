package com.yunmei.vibe.ui.unlock

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        UnlockNotifications.ensureChannel(this)
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
            finishWithFailure(getString(R.string.unlock_shortcut_timeout))
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
        startForeground(
            UnlockNotifications.NOTIFICATION_ID,
            UnlockNotifications.progress(this, text, percent),
        )
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
        UnlockNotifications.post(
            this,
            UnlockNotifications.result(this, false, message),
        )
        stopSelfSafely()
    }

    private fun stopSelfSafely() {
        // 结果通知保留在通知栏，只撤掉前台服务身份。
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun hasBlePermissions(): Boolean = BLE_PERMISSIONS.all { permission ->
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        const val UNLOCK_TIMEOUT_MS = 45_000L
    }
}
