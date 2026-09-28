package com.yunmei.vibe.ui.unlock

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.yunmei.vibe.R
import com.yunmei.vibe.ui.MainActivity

/**
 * 快捷方式开门的通知（进行中 / 结果）。
 *
 * 渠道划分与构建方式整体移植自 InstallerX Revived 的通知实现
 * （framework/notification/NotificationHelper + SessionNotifierImpl + Modern/LegacyNotificationBuilder）：
 *
 *  - 实况路径（Android 16+，对应 ModernNotificationBuilder）：
 *    IMPORTANCE_HIGH 的独立实况渠道 + `NotificationCompat.ProgressStyle`
 *    （`setStyledByProgress` + `setProgress`）+ `setRequestPromotedOngoing(true)`
 *    + `setShortCriticalText(...)`，并在清单声明 `POST_PROMOTED_NOTIFICATIONS`。
 *    这套组合是 ColorOS 流体云 / 小米灵动岛等国产 ROM 能读到该通知的前提。
 *  - 旧式路径（Android 16 以下，对应 LegacyNotificationBuilder）：
 *    进行中用 IMPORTANCE_LOW 渠道 + `setProgress(100, percent, false)`；
 *    结束（成功 / 失败）切到 IMPORTANCE_HIGH 渠道并取消 ongoing。
 *
 * 通知 ID 固定，进度持续更新同一条通知，不会反复新建。
 */
object UnlockNotifications {

    /** 实况渠道（InstallerX: installer_live_channel，IMPORTANCE_HIGH）。 */
    const val LIVE_CHANNEL_ID = "unlock_live_channel"

    /** 进行中渠道（InstallerX: installer_progress_channel，IMPORTANCE_LOW）。 */
    const val PROGRESS_CHANNEL_ID = "unlock_progress_channel"

    /** 结束渠道（InstallerX: installer_channel，IMPORTANCE_HIGH）。 */
    const val RESULT_CHANNEL_ID = "unlock_channel"

    /** 固定通知 ID：进度与结果始终是同一条通知。 */
    const val NOTIFICATION_ID = 1001

    /** 进度最大值，与 InstallerX 一致恒为 100。 */
    private const val PROGRESS_MAX = 100

    /** Android 16 起才有实况通知（对应 InstallerX 的 isModernEligible）。 */
    private fun isModernEligible(): Boolean = Build.VERSION.SDK_INT >= 36

    /** 创建渠道（幂等；与 InstallerX 一样按用途分成三个渠道）。 */
    fun ensureChannel(context: Context) {
        val channels = listOf(
            NotificationChannelCompat.Builder(
                LIVE_CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_HIGH,
            )
                .setName(context.getString(R.string.unlock_live_channel_name))
                .setDescription(context.getString(R.string.unlock_live_channel_desc))
                .build(),
            NotificationChannelCompat.Builder(
                PROGRESS_CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_LOW,
            )
                .setName(context.getString(R.string.unlock_progress_channel_name))
                .setDescription(context.getString(R.string.unlock_progress_channel_desc))
                .build(),
            NotificationChannelCompat.Builder(
                RESULT_CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_HIGH,
            )
                .setName(context.getString(R.string.unlock_channel_name))
                .setDescription(context.getString(R.string.unlock_channel_desc))
                .build(),
        )
        NotificationManagerCompat.from(context).createNotificationChannelsCompat(channels)
    }

    /**
     * 开门进行中的通知（前台服务使用）。
     *
     * [percent] 为 null 表示进度未知（不确定进度），否则为 0..100 的确定进度。
     */
    fun progress(context: Context, text: String, percent: Int?): Notification =
        if (isModernEligible()) {
            modernProgress(context, text, percent)
        } else {
            legacyProgress(context, text, percent)
        }

    /** Android 16+：复刻 InstallerX ModernNotificationBuilder（ProgressStyle + 请求实况提升）。 */
    private fun modernProgress(context: Context, text: String, percent: Int?): Notification {
        val progressStyle = NotificationCompat.ProgressStyle().apply {
            setStyledByProgress(true)
            if (percent == null) {
                setProgressIndeterminate(true)
            } else {
                setProgressIndeterminate(false)
                setProgress(percent)
            }
        }
        return NotificationCompat.Builder(context, LIVE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_unlock)
            .setContentTitle(context.getString(R.string.unlock_open))
            .setContentText(text)
            .setContentIntent(openAppIntent(context))
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setRequestPromotedOngoing(true)
            .setShortCriticalText(percent?.let { "$it%" } ?: text)
            .setStyle(progressStyle)
            .build()
    }

    /** Android 16 以下：复刻 InstallerX LegacyNotificationBuilder（经典进度条）。 */
    private fun legacyProgress(context: Context, text: String, percent: Int?): Notification =
        NotificationCompat.Builder(context, PROGRESS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_unlock)
            .setContentTitle(context.getString(R.string.unlock_open))
            .setContentText(text)
            .setContentIntent(openAppIntent(context))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(PROGRESS_MAX, percent ?: 0, percent == null)
            .build()

    /**
     * 开门结果（成功 / 带具体原因的失败）。
     *
     * 与 InstallerX 一致：结束时取消 ongoing 并切到高优先级渠道，进度条随之为结果状态让位。
     */
    fun result(context: Context, success: Boolean, text: String): Notification {
        val channelId = if (isModernEligible()) LIVE_CHANNEL_ID else RESULT_CHANNEL_ID
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_unlock)
            .setContentTitle(
                context.getString(if (success) R.string.unlock_success else R.string.unlock_failed)
            )
            .setContentText(text)
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .setOngoing(false)
            .setOnlyAlertOnce(false)
            .setSilent(false)
            .build()
    }

    /** 用固定 ID 更新同一条通知；通知被系统关闭时静默跳过。 */
    fun post(context: Context, notification: Notification) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
