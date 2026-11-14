package com.yunmei.vibe.ui.util

import android.app.Notification
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 结束态通知的延时降级与自动清除。
 *
 * 任务结束时先以「常驻」形态更新同一条通知（保持实况/流体云提升），延时后再把同一 ID、
 * 同一渠道的通知换成可滑动的普通形态。全程只做 notify，不做 remove + rebuild，
 * 因此系统里始终是同一条通知，实况退场时不会出现「删掉再重建」的闪断。
 *
 * 自动清除（固定 10 秒）：降级完成后再等待暂存时长后 cancel 同一条通知。
 * 只在成功结果上生效，失败一律保留，等用户处理。
 *
 * 取消点（同一份 Job，取消即两份计时一起停）：
 *  - 用户从通知点进应用（见 [ACTION_FROM_NOTIFICATION]）：正在看通知时不该再改它；
 *  - 用户点了「重试/完成」或重新发起任务：旧的结果不该覆盖新的进度；
 *  - 用户把通知划掉：activeNotifications 里已不存在，两个阶段都会自然退出。
 */
object NotificationDowngrade {

    /** 停驻时长：与 WorkIsland 的结束态降级节奏一致。 */
    private const val DELAY_MS = 8_000L

    /** 成功结果在降级为普通通知后的自动清除延时。 */
    private const val AUTO_CLEAR_DELAY_MS = 10_000L

    private const val TAG = "NotificationDowngrade"

    /** 点击通知正文时的标记：主界面据此取消本次降级。 */
    const val ACTION_FROM_NOTIFICATION = "com.yunmei.vibe.action.FROM_NOTIFICATION"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    /**
     * 安排一次降级：[normal] 应与刚发出的常驻通知内容相同，只是 `setOngoing(false)` + `setAutoCancel(true)`。
     *
     * @param autoClearOnSuccess 仅成功结果传 true：降级后再等待 10 秒自动清除。
     */
    fun schedule(
        context: Context,
        id: Int,
        normal: Notification,
        autoClearOnSuccess: Boolean = false,
    ) {
        val appContext = context.applicationContext
        cancel()
        job = scope.launch {
            delay(DELAY_MS)
            val manager = NotificationManagerCompat.from(appContext)
            if (!manager.areNotificationsEnabled()) return@launch
            // 通知已被用户划走或被「完成」收起时不再复活。
            val alive = runCatching {
                manager.activeNotifications.any { it.id == id }
            }.getOrDefault(false)
            if (!alive) return@launch
            runCatching { manager.notify(id, normal) }
                .onFailure { Log.w(TAG, "downgrade notify failed", it) }

            if (!autoClearOnSuccess) return@launch
            delay(AUTO_CLEAR_DELAY_MS)
            if (!manager.areNotificationsEnabled()) return@launch
            // 用户在这段时间里可能已经划掉通知或点进应用。
            val stillAlive = runCatching {
                manager.activeNotifications.any { it.id == id }
            }.getOrDefault(false)
            if (!stillAlive) return@launch
            runCatching { manager.cancel(id) }
                .onFailure { Log.w(TAG, "auto clear failed", it) }
        }
    }

    /** 取消待执行的降级与自动清除（幂等）。 */
    fun cancel() {
        job?.cancel()
        job = null
    }
}
