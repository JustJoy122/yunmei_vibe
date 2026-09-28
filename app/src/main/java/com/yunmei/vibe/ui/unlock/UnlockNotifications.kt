package com.yunmei.vibe.ui.unlock

import android.annotation.SuppressLint
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
 * 渠道划分与构建方式整体移植自 InstallerX Revived 的通知实现，按设备能力分三条路径：
 *
 *  - 小米超级岛路径（对应 MiIslandNotificationBuilder）：小米机型且系统焦点通知协议为 3 时，
 *    标准通知 + `addExtras(miui.focus.param)`，固定挂在实况渠道（InstallerX 同样要求岛通知
 *    不切渠道，否则 MIUI 侧的岛会重建、出现闪烁或黑隙）。
 *  - 实况路径（Android 16+，对应 ModernNotificationBuilder）：IMPORTANCE_HIGH 实况渠道
 *    + 平台 `Notification.ProgressStyle`，并写入 `android.requestPromotedOngoing` 请求实况提升
 *    + `setShortCriticalText(...)`，并已在清单声明 `POST_PROMOTED_NOTIFICATIONS`，
 *    这是 ColorOS 流体云等国产 ROM 读取该通知的前提。
 *  - 旧式路径（Android 16 以下，对应 LegacyNotificationBuilder）：进行中用 IMPORTANCE_LOW 渠道
 *    + `setProgress(100, percent, false)`；结束（成功 / 失败）切到 IMPORTANCE_HIGH 渠道并取消 ongoing。
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
                // 与 InstallerX 的 setSilent(true) 一致：高优先级但不响铃不振动。
                .setSound(null, null)
                .setVibrationEnabled(false)
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
        when {
            MiIslandNotification.isSupported(context) -> miIslandProgress(context, text, percent)
            isModernEligible() -> modernProgress(context, text, percent)
            else -> legacyProgress(context, text, percent)
        }

    /** 小米超级岛：标准通知 + 岛参数，固定实况渠道，进度用经典进度字段。 */
    private fun miIslandProgress(context: Context, text: String, percent: Int?): Notification {
        val title = context.getString(R.string.unlock_open)
        return NotificationCompat.Builder(context, LIVE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_unlock)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openAppIntent(context))
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(PROGRESS_MAX, percent ?: 0, percent == null)
            .addExtras(MiIslandNotification.extras(context, title, text, percent, inProgress = true))
            .build()
    }

    /**
     * Android 16+：复刻 InstallerX ModernNotificationBuilder（进度样式 + 请求实况提升）。
     *
     * 进度样式改用平台 [Notification.ProgressStyle]：androidx 的 NotificationCompat.ProgressStyle
     * 带 @RequiresApi(36)，在本项目工具链上可以解析类但解析不到其方法。
     * 提升请求则完全按 androidx 的实现方式，直接写入公开 extra 键（android.requestPromotedOngoing），
     * 因此 Android 16 与 Android 17 上都会生效。
     */
    @SuppressLint("NewApi")
    private fun modernProgress(context: Context, text: String, percent: Int?): Notification {
        val progressStyle = Notification.ProgressStyle().apply {
            setStyledByProgress(true)
            if (percent == null) {
                setProgressIndeterminate(true)
            } else {
                setProgressIndeterminate(false)
                setProgress(percent)
            }
        }
        val notification = Notification.Builder(context, LIVE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_unlock)
            .setContentTitle(context.getString(R.string.unlock_open))
            .setContentText(text)
            .setContentIntent(openAppIntent(context))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setShortCriticalText(percent?.let { "$it%" } ?: text)
            .setProgress(PROGRESS_MAX, percent ?: 0, percent == null)
            .setStyle(progressStyle)
            .build()
        // 与 InstallerX 的 setRequestPromotedOngoing(true) 等价（androidx 就是这么实现的）。
        notification.extras.putBoolean(NotificationCompat.EXTRA_REQUEST_PROMOTED_ONGOING, true)
        return notification
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
     * 与 InstallerX 一致：结束时取消 ongoing 并切到高优先级渠道，进度条随之为结果状态让位；
     * 小米设备附带超级岛的结果态参数（岛通知保持在同一渠道）。
     */
    fun result(context: Context, success: Boolean, text: String): Notification {
        val title = context.getString(if (success) R.string.unlock_success else R.string.unlock_failed)
        val islandSupported = MiIslandNotification.isSupported(context)
        val channelId = if (islandSupported || isModernEligible()) LIVE_CHANNEL_ID else RESULT_CHANNEL_ID
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_unlock)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .setOngoing(false)
            .setOnlyAlertOnce(false)
            .setSilent(false)
        if (islandSupported) {
            builder.addExtras(
                MiIslandNotification.extras(context, title, text, percent = null, inProgress = false)
            )
        }
        return builder.build()
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
