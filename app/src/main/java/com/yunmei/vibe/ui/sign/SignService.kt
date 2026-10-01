package com.yunmei.vibe.ui.sign

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.yunmei.vibe.R
import com.yunmei.vibe.YunMeiApp
import com.yunmei.vibe.data.model.Lock
import com.yunmei.vibe.ui.SignLocationMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 打卡的前台服务：承载进度/结果通知，并复用应用内打卡同一套业务逻辑
 * （AccountStore 取账号 → LocationProvider 取定位 → YunMeiRepository.sign）。
 *
 * 三种定位模式（与设置项 [SignLocationMode] 一致）：
 *  - 使用上次位置（lst）：后台静默完成，完成后发普通结果通知；
 *  - 重新定位并保存（rel）：取定位（定位未开启时由承载 Activity 拉起系统定位设置）→ 保存 → 打卡 → 结果通知；
 *  - 每次询问（ask）：先进度通知置 50% 并给出 2 个动作（使用上次位置 / 选择定位方式）；
 *    点「选择定位方式」走 PendingIntent.getActivity 打开 [SignLocationActivity] 轻量弹窗，
 *    选中后回到本服务执行；成功进度到 100%，失败保留实况与进度条、停在 50% 且失败分段变红。
 */
class SignService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val busy = AtomicBoolean(false)
    private var waitJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        SignNotifications.ensureChannel(this)
        val action = intent?.action ?: ACTION_START
        Log.d(TAG, "onStartCommand action=$action")

        when (action) {
            // 通知按钮：使用上次位置，直接后台执行
            ACTION_USE_LAST -> {
                startAsForegroundIfNeeded(progressText = getString(R.string.sign_signaling), percent = 75)
                runFlow(SignSubMode.USE_LAST)
            }
            // 弹窗回调：带上用户选择
            ACTION_WITH_MODE -> {
                val mode = SignSubMode.fromValue(intent?.getStringExtra(EXTRA_SUB_MODE))
                startAsForegroundIfNeeded(progressText = getString(R.string.sign_signaling), percent = 75)
                runFlow(mode)
            }
            // 入口：按设置里的定位方式决定
            // 失败通知的「重试」：带了定位方式就沿用，否则按设置里的模式重来（每次询问会重新回到选择状态）。
            ACTION_RETRY -> {
                val sub = intent?.getStringExtra(EXTRA_SUB_MODE)
                if (sub.isNullOrBlank()) {
                    startBySettingsMode()
                } else {
                    startAsForegroundIfNeeded(getString(R.string.sign_signaling), 75)
                    runFlow(SignSubMode.fromValue(sub))
                }
            }
            // 失败通知的「完成」：收起通知并结束服务。
            // 跳板 Activity 在流程内请求权限被拒：直接发失败通知并结束，不停在中间态。
            ACTION_PERMISSION_DENIED -> {
                SignNotifications.post(
                    this,
                    SignNotifications.result(
                        this,
                        false,
                        getString(R.string.sign_permission_needed),
                    ),
                )
                stopSelf()
            }
            ACTION_FINISH -> finishAndDismiss()
            else -> startBySettingsMode()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        waitJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    /** 首次进入时以前台服务身份发通知；已在运行则原地更新。 */
    private fun startAsForegroundIfNeeded(
        progressText: String,
        percent: Int,
        promptChooser: Boolean = false,
    ) {
        val notification = SignNotifications.progress(this, progressText, percent, promptChooser = promptChooser)
        val type = foregroundServiceType()
        runCatching {
            ServiceCompat.startForeground(this, SignNotifications.SIGN_NOTIFICATION_ID, notification, type)
        }.onFailure { error ->
            Log.w(TAG, "startForeground failed", error)
            SignNotifications.post(this, SignNotifications.result(this, false, getString(R.string.sign_permission_needed)))
            stopSelf()
        }
    }

    /** 前台服务类型：需要取新定位时用 location，否则只用 dataSync（避免无谓的权限前置条件）。 */
    private fun foregroundServiceType(): Int {
        val hasLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasLocation) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
    }

    /** 每次询问模式下等待用户选择，避免前台服务无限驻留。 */
    private fun armWaitTimeout() {
        waitJob?.cancel()
        waitJob = scope.launch {
            delay(WAIT_TIMEOUT_MS)
            if (busy.get()) return@launch
            SignNotifications.post(this@SignService, SignNotifications.result(this@SignService, false, getString(R.string.sign_timeout)))
            stopForegroundCompat()
            stopSelf()
        }
    }

    /** 按设置里的定位方式启动；每次询问模式停在 50% 等用户选择。 */
    private fun startBySettingsMode() {
        scope.launch {
            when (SignLocationMode.fromValue(currentMode())) {
                SignLocationMode.LAST -> {
                    startAsForegroundIfNeeded(getString(R.string.sign_signaling), 75)
                    runFlow(SignSubMode.USE_LAST)
                }
                SignLocationMode.RELOCATE -> {
                    startAsForegroundIfNeeded(getString(R.string.sign_locating), 25)
                    runFlow(SignSubMode.LOCATE_SAVE)
                }
                SignLocationMode.ASK -> {
                    startAsForegroundIfNeeded(getString(R.string.sign_choose_location), 50, promptChooser = true)
                    armWaitTimeout()
                }
            }
        }
    }

    /**
     * 用户点「完成」：收起失败通知并结束服务。
     *
     * 用前台服务动作实现是为了绕开后台服务启动限制；按 5 秒规则必须先 startForeground，
     * 因此这里用一个占位通知顶替同 ID 的失败通知，随后立即移除。
     */
    private fun finishAndDismiss() {
        val placeholder = android.app.Notification.Builder(this, SignNotifications.PROGRESS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shortcut_check_in)
            .setContentTitle(getString(R.string.unlock_sign))
            .build()
        runCatching {
            ServiceCompat.startForeground(
                this,
                SignNotifications.SIGN_NOTIFICATION_ID,
                placeholder,
                foregroundServiceType(),
            )
        }
        runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    private fun runFlow(subMode: SignSubMode) {
        if (!busy.compareAndSet(false, true)) return
        waitJob?.cancel()
        scope.launch {
            val container = YunMeiApp.app.container
            try {
                val lock = withContext(Dispatchers.IO) { container.lockStore.getDefault() }
                if (lock == null) {
                    fail(getString(R.string.unlock_shortcut_no_default), SignNotifications.STAGE_LOCATE)
                    return@launch
                }
                if (!lock.isUsable) {
                    fail(getString(R.string.unlock_lock_unusable_need_login), SignNotifications.STAGE_LOCATE)
                    return@launch
                }
                val user = withContext(Dispatchers.IO) { findUser(lock) }
                if (user == null) {
                    fail(getString(R.string.unlock_no_account), SignNotifications.STAGE_LOCATE)
                    return@launch
                }

                val location = resolveLocation(subMode) ?: return@launch

                // 已确定位置，进入打卡请求阶段
                updateProgress(getString(R.string.sign_signaling), 75)
                val message = withContext(Dispatchers.IO) {
                    runCatching { container.repository.sign(user, lock, location) }
                }
                message.onSuccess { text ->
                    // 成功：实况/进度条直接到 100%，通知可被划走，前台服务退出但保留通知。
                    SignNotifications.post(
                        this@SignService,
                        SignNotifications.progress(
                            this@SignService,
                            text.ifBlank { getString(R.string.unlock_sign_success) },
                            100,
                            completed = true,
                        ),
                    )
                    complete()
                }.onFailure { error ->
                    fail(
                        error.message ?: getString(R.string.unlock_sign_failed),
                        SignNotifications.STAGE_SIGN,
                        retrySubMode = subMode,
                    )
                }
            } finally {
                busy.set(false)
            }
        }
    }

    /** 解析本次打卡使用的位置；失败时已负责发出失败通知。 */
    private suspend fun resolveLocation(subMode: SignSubMode): String? {
        val container = YunMeiApp.app.container
        return when (subMode) {
            SignSubMode.USE_LAST -> {
                val last = withContext(Dispatchers.IO) { container.appPreferences.getLastLocation() }
                if (last.isBlank()) {
                    fail(getString(R.string.unlock_sign_last_missing), SignNotifications.STAGE_LOCATE)
                    null
                } else {
                    last
                }
            }
            SignSubMode.LOCATE_ONLY, SignSubMode.LOCATE_SAVE -> {
                if (!isLocationEnabled()) {
                    fail(
                        getString(R.string.unlock_sign_location_unavailable),
                        SignNotifications.STAGE_LOCATE,
                        locationUnavailable = true,
                        retrySubMode = subMode,
                    )
                    return null
                }
                updateProgress(getString(R.string.sign_locating), 25)
                val located = withContext(Dispatchers.IO) {
                    runCatching { container.locationProvider.getLocation() }
                }
                located.onFailure { error ->
                    fail(
                        error.message ?: getString(R.string.unlock_sign_location_failed),
                        SignNotifications.STAGE_LOCATE,
                        retrySubMode = subMode,
                    )
                }.getOrNull()?.also { value ->
                    if (subMode == SignSubMode.LOCATE_SAVE) {
                        withContext(Dispatchers.IO) { container.appPreferences.setLastLocation(value) }
                    }
                }
            }
        }
    }

    private suspend fun findUser(lock: Lock) = withContext(Dispatchers.IO) {
        val container = YunMeiApp.app.container
        val alwaysCode = container.appPreferences.settings.first().alwaysCode
        container.accountStore.getByUsernameMd5(lock.usernameMd5)
            ?: if (alwaysCode) container.accountStore.getAll().firstOrNull() else null
    }

    private suspend fun currentMode(): String = runCatching {
        YunMeiApp.app.container.appPreferences.settings.first().signLocationMode
    }.getOrDefault(SignLocationMode.DEFAULT.value)

    private fun isLocationEnabled(): Boolean {
        val lm = getSystemService(LOCATION_SERVICE) as? LocationManager ?: return false
        return runCatching { LocationManagerCompat.isLocationEnabled(lm) }.getOrDefault(false)
    }

    private fun updateProgress(text: String, percent: Int) {
        SignNotifications.post(this, SignNotifications.progress(this, text, percent))
    }

    /** 失败：保留实况与进度条、进度停在失败处，并把失败分段变红；通知不消失，提醒一次。 */
    private fun fail(
        message: String,
        stage: Int,
        locationUnavailable: Boolean = false,
        retrySubMode: SignSubMode? = null,
    ) {
        val percent = if (stage == SignNotifications.STAGE_SIGN) 75 else 50
        SignNotifications.post(
            this,
            SignNotifications.progress(
                this,
                message,
                percent,
                failedStage = stage,
                locationUnavailable = locationUnavailable,
                retrySubMode = retrySubMode?.value,
            ),
        )
        stopForegroundCompat()
        stopSelf()
    }

    private fun complete() {
        stopForegroundCompat()
        stopSelf()
    }

    private fun stopForegroundCompat() {
        runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH) }
    }

    /** 弹窗回传的定位方式（与设置项解耦，仅表达"这一次打卡怎么取位置"）。 */
    enum class SignSubMode(val value: String) {
        USE_LAST("use_last"),
        LOCATE_ONLY("locate_only"),
        LOCATE_SAVE("locate_save"),
        ;

        companion object {
            fun fromValue(value: String?): SignSubMode =
                entries.firstOrNull { it.value == value } ?: USE_LAST
        }
    }

    companion object {
        const val ACTION_START = "com.yunmei.vibe.action.SIGN_START"
        const val ACTION_USE_LAST = "com.yunmei.vibe.action.SIGN_USE_LAST"
        const val ACTION_WITH_MODE = "com.yunmei.vibe.action.SIGN_WITH_MODE"
        const val ACTION_RETRY = "com.yunmei.vibe.action.SIGN_RETRY"
        const val ACTION_FINISH = "com.yunmei.vibe.action.SIGN_FINISH"

        /** 权限被拒（由跳板 Activity 在流程内请求后回报）。 */
        const val ACTION_PERMISSION_DENIED = "com.yunmei.vibe.action.SIGN_PERMISSION_DENIED"
        const val EXTRA_SUB_MODE = "sign_sub_mode"

        private const val TAG = "SignService"
        private const val WAIT_TIMEOUT_MS = 120_000L
    }
}
