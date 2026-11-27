package com.yunmei.vibe.ui.unlock

import android.app.Notification
import com.yunmei.vibe.ui.util.ForegroundPlaceholder
import com.yunmei.vibe.ui.util.NotificationDowngrade
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.yunmei.vibe.R
import com.yunmei.vibe.YunMeiApp
import com.yunmei.vibe.data.repository.UnlockBlocker
import com.yunmei.vibe.data.ble.UnlockManager
import com.yunmei.vibe.data.repository.UnlockPreparation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

    /** 失败通知保持实况的最长时间，超时后释放前台服务。 */
    private var failureTimeoutJob: Job? = null

    /** 最近一次进度，用于失败时判断哪个分段变红。 */
    private var lastPercent = 0

    /** 本次开门执行的 Job：与 Service 生命周期作用域分离，取消只影响本次执行。 */
    private var runJob: Job? = null

    /** 结束态的「可滑动」形态：8 秒降级与 10 分钟兜底都复用它原地更新通知。 */
    private var pendingDowngrade: Notification? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        UnlockNotifications.ensureChannel(this)
        // 新的一次启动（含重试）：之前排队的降级不能把新进度覆盖成旧结果。
        NotificationDowngrade.cancel()
        // 每次新命令都重置完成标记：失败后服务会保持存活以承载实况通知，
        // 此时用户点「重试」若不清零，进度回调与再次失败都会被 early-return 吞掉。
        if (intent?.action != ACTION_FINISH) {
            finished.set(false)
        }

        if (intent?.action == ACTION_FINISH) {
            dismissAndStop()
            return START_NOT_STICKY
        }
        // 「重试」：显式分支。取消上一次执行，并让通知立刻回到"不确定进度"，
        // 用户点下去马上有可见反馈（此前只有常量定义、没有分支）。
        if (intent?.action == ACTION_RETRY) {
            runJob?.cancel()
            runJob = null
            UnlockNotifications.post(
                this,
                UnlockNotifications.progress(
                    this,
                    getString(R.string.unlock_retry),
                    null,
                ),
            )
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
                UnlockNotifications.UNLOCK_NOTIFICATION_ID,
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
        runJob?.cancel()
        runJob = scope.launch { runUnlock() }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        failureTimeoutJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun runUnlock() {
        val container = YunMeiApp.app.container
        // 前置检查（蓝牙开关 / 默认门锁 / 权限 / 可用性 / 8 秒冷却 / secret 刷新）统一放在 UnlockFlow，
        // 与首页按钮共用同一份实现；这里只负责把阻塞原因翻译成通知文案。
        when (val preparation = container.unlockFlow.prepare(lock = null)) {
            is UnlockPreparation.Blocked -> when (val blocker = preparation.blocker) {
                // 蓝牙关着（含「重试」时仍关着）：不要闷头失败，给出「开启蓝牙」入口。
                UnlockBlocker.BluetoothOff -> postBluetoothFailure()
                UnlockBlocker.NoDefaultLock -> finishWithFailure(getString(R.string.unlock_shortcut_no_default))
                UnlockBlocker.MissingPermissions -> finishWithFailure(getString(R.string.unlock_shortcut_no_permission))
                UnlockBlocker.LockUnusable -> finishWithFailure(getString(R.string.unlock_lock_unusable))
                is UnlockBlocker.CoolingDown -> finishWithFailure(getString(R.string.unlock_cooldown_wait, blocker.seconds))
            }

            is UnlockPreparation.Ready -> {
                // 兜底超时：底层回调偶发不返回时也要给出结果并退出前台服务。
                val timeoutJob = scope.launch {
                    delay(UNLOCK_TIMEOUT_MS)
                    finishWithFailure(getString(R.string.unlock_timeout))
                }

                container.unlockManager.openDoor(
                    preparation.lock,
                    preparation.quickConnect,
                    object : UnlockManager.Listener {
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
                    },
                )
            }
        }
    }

    private fun updateProgress(text: String, percent: Int) {
        if (finished.get()) return
        // 固定 ID 原地更新同一个通知：进度条由真实的进度字段驱动，不靠改标题/正文代替。
        lastPercent = percent
        UnlockNotifications.post(this, UnlockNotifications.progress(this, text, percent))
    }

    private fun finishWithSuccess() {
        if (!finished.compareAndSet(false, true)) return
        val text = getString(R.string.unlock_progress_done)
        // 先以常驻形态更新「同一条」通知：解除前台服务身份后系统会把实况回落，
        // 但通知本身不删除，避免出现「删掉再重建」的闪断。
        UnlockNotifications.post(
            this,
            UnlockNotifications.result(this, true, text, ongoing = true),
        )
        // 8 秒后原地换成可滑动的普通通知（同 ID、同渠道，不触发重建），
        // 成功后 10 秒再自动清除；失败保留由用户处理。
        NotificationDowngrade.schedule(
            this,
            UnlockNotifications.UNLOCK_NOTIFICATION_ID,
            UnlockNotifications.result(this, true, text, ongoing = false),
            autoClearOnSuccess = true,
        )
        stopSelfSafely()
    }

    private fun finishWithFailure(message: String) {
        if (!finished.compareAndSet(false, true)) return
        // 严格对齐 InstallerX：失败不退回传统通知，保留实况与进度条，失败分段变红、进度停在失败处，
        // 通知常驻（ongoing，不 autoCancel），由用户通过「重试 / 完成」结束。
        val stage = UnlockNotifications.stageOf(lastPercent)
        val actions = listOf(
            UnlockNotifications.retryAction(this),
            UnlockNotifications.finishAction(this),
        )
        val notification = UnlockNotifications.failure(this, message, stage, actions, ongoing = true)
        // 失败时继续以这个通知作为前台服务通知（不 detach、不 stopSelf）：实况/流体云的"提升"依赖
        // 应用仍处于前台服务状态，一旦立刻停服，系统会把通知降级成普通通知。
        runCatching { startForeground(UnlockNotifications.UNLOCK_NOTIFICATION_ID, notification) }
            .onFailure { UnlockNotifications.post(this, notification) }
        // 结束态先保持常驻，8 秒后原地降级；10 分钟兜底复用同一份降级通知。
        val downgraded = UnlockNotifications.failure(this, message, stage, actions, ongoing = false)
        pendingDowngrade = downgraded
        NotificationDowngrade.schedule(this, UnlockNotifications.UNLOCK_NOTIFICATION_ID, downgraded)
        armFailureTimeout()
    }

    /**
     * 失败通知的兜底清理：用户长时间不理会时把通知换成可滑动的普通形态，并只解除前台服务身份。
     * 刻意不删除通知、也不用 STOP_FOREGROUND_REMOVE：删除会让实况/流体云走「删掉再重建」的路径。
     */
    private fun armFailureTimeout() {
        failureTimeoutJob?.cancel()
        failureTimeoutJob = scope.launch {
            delay(FAILURE_KEEP_MS)
            pendingDowngrade?.let { normal ->
                NotificationDowngrade.cancel()
                UnlockNotifications.post(this@UnlockService, normal)
            }
            ServiceCompat.stopForeground(this@UnlockService, ServiceCompat.STOP_FOREGROUND_DETACH)
            stopSelf()
        }
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
        runCatching { startForeground(UnlockNotifications.UNLOCK_NOTIFICATION_ID, notification) }
            .onFailure { UnlockNotifications.post(this, notification) }
        // 先安排降级再解除前台身份：通知留在通知栏，8 秒后变成可滑动的普通通知。
        val downgraded = UnlockNotifications.failure(
            this,
            getString(R.string.unlock_bluetooth_disabled),
            0,
            actions,
            ongoing = false,
        )
        pendingDowngrade = downgraded
        NotificationDowngrade.schedule(this, UnlockNotifications.UNLOCK_NOTIFICATION_ID, downgraded)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    /** 「完成」：用占位通知顶替同 ID 的通知后立即移除，并按规则先完成 startForeground。 */
    private fun dismissAndStop() {
        NotificationDowngrade.cancel()
        ForegroundPlaceholder.startThenRemove(
            service = this,
            id = UnlockNotifications.UNLOCK_NOTIFICATION_ID,
            channelId = UnlockNotifications.LIVE_CHANNEL_ID,
            smallIcon = R.drawable.ic_notification_unlock,
            title = getString(R.string.unlock_preparing),
        )
        stopSelf()
    }

    private fun stopSelfSafely() {
        // 结果通知保留在通知栏，只撤掉前台服务身份（detach 而非 remove：remove 会删掉通知）。
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    companion object {
        private const val TAG = "UnlockService"
        private const val UNLOCK_TIMEOUT_MS = 45_000L

        /** 失败通知保持实况的最长时间（10 分钟），超时后释放前台服务。 */
        private const val FAILURE_KEEP_MS = 10 * 60 * 1000L

        /** 失败通知的「重试」：重跑一次开门。 */
        const val ACTION_RETRY = "com.yunmei.vibe.action.UNLOCK_RETRY"

        /** 失败通知的「完成」：收起通知并结束服务。 */
        const val ACTION_FINISH = "com.yunmei.vibe.action.UNLOCK_FINISH"

        /** 用户拒绝开启蓝牙：只发失败通知，不执行开门。 */
        const val EXTRA_BT_DENIED = "unlock_bt_denied"
    }
}
