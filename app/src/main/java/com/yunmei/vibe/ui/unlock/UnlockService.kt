package com.yunmei.vibe.ui.unlock

import com.yunmei.vibe.ui.util.ForegroundPlaceholder
import android.app.Service
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.yunmei.vibe.R
import com.yunmei.vibe.YunMeiApp
import com.yunmei.vibe.data.ble.UnlockManager
import com.yunmei.vibe.data.local.StoredUser
import com.yunmei.vibe.data.model.Lock
import com.yunmei.vibe.ui.util.BLE_PERMISSIONS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        UnlockNotifications.ensureChannel(this)
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
        // 打开门之前先刷新一次 secret：本地保存的可能是首次添加时的旧快照，服务端会轮换。
        // 未登录时用已保存账号重新登录；刷新超时 / 离线直接沿用旧 secret，不阻塞开锁。
        val settings = withContext(Dispatchers.IO) { container.appPreferences.settings.first() }
        val quickConnect = settings.quickConnect
        val account = withContext(Dispatchers.IO) {
            container.accountStore.getByUsernameMd5(lock.usernameMd5)
                ?: if (settings.alwaysCode) container.accountStore.getAll().firstOrNull() else null
        }
        val effectiveLock = refreshLockInfo(lock, account)

        // 兜底超时：底层回调偶发不返回时也要给出结果并退出前台服务。
        val timeoutJob = scope.launch {
            delay(UNLOCK_TIMEOUT_MS)
            finishWithFailure(getString(R.string.unlock_timeout))
        }

        container.unlockManager.openDoor(effectiveLock, quickConnect, object : UnlockManager.Listener {
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

    /**
     * 开门前刷新门锁信息（secret 与型号能力）；拿不到（未登录 / 无网络 / 超时）时原样返回本地门锁，
     * 保证离线也能开门。AES 型号的拦截在 UnlockManager.openDoor 里按刷新后的 aesSupport 判断。
     */
    private suspend fun refreshLockInfo(lock: Lock, account: StoredUser?): Lock {
        val container = YunMeiApp.app.container
        val refreshed = withTimeoutOrNull(SECRET_REFRESH_TIMEOUT_MS) {
            container.repository.refreshLock(lock, account)
        } ?: return lock
        withContext(Dispatchers.IO) { container.lockStore.updateFromServer(refreshed) }
        return refreshed
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
        val notification = UnlockNotifications.failure(
            this,
            message,
            UnlockNotifications.stageOf(lastPercent),
            listOf(
                UnlockNotifications.retryAction(this),
                UnlockNotifications.finishAction(this),
            ),
        )
        // 失败时继续以这个通知作为前台服务通知（不 detach、不 stopSelf）：实况/流体云的"提升"依赖
        // 应用仍处于前台服务状态，一旦立刻停服，系统会把通知降级成普通通知。
        runCatching { startForeground(UnlockNotifications.UNLOCK_NOTIFICATION_ID, notification) }
            .onFailure { UnlockNotifications.post(this, notification) }
        armFailureTimeout()
    }

    /** 失败通知的兜底清理：用户长时间不理会时释放前台服务，避免常驻。 */
    private fun armFailureTimeout() {
        failureTimeoutJob?.cancel()
        failureTimeoutJob = scope.launch {
            delay(FAILURE_KEEP_MS)
            runCatching {
                NotificationManagerCompat.from(this@UnlockService)
                    .cancel(UnlockNotifications.UNLOCK_NOTIFICATION_ID)
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
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
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    /** 「完成」：用占位通知顶替同 ID 的通知后立即移除，并按规则先完成 startForeground。 */
    private fun dismissAndStop() {
        ForegroundPlaceholder.startThenRemove(
            service = this,
            id = UnlockNotifications.UNLOCK_NOTIFICATION_ID,
            channelId = UnlockNotifications.LIVE_CHANNEL_ID,
            smallIcon = R.drawable.ic_notification_unlock,
            title = getString(R.string.unlock_preparing),
        )
        stopSelf()
    }

    private fun isBluetoothEnabled(): Boolean = runCatching {
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        manager?.adapter?.isEnabled == true
    }.getOrDefault(false)

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

        /** 开门前刷新 secret 的等待上限：超时直接按旧 secret 开门。 */
        private const val SECRET_REFRESH_TIMEOUT_MS = 8_000L

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
