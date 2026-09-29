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
import com.yunmei.vibe.ui.theme.ThemeColors

/**
 * 快捷方式开门的通知（进行中 / 结果）。
 *
 * 渠道划分与构建方式整体移植自 InstallerX Revived 的通知实现，按设备能力分三条路径：
 *
 *  - 小米超级岛路径（对应 MiIslandNotificationBuilder）：小米机型且系统焦点通知协议为 3 时，
 *    标准通知 + `addExtras(miui.focus.param)`，固定挂在实况渠道。
 *  - 实况路径（Android 16+，对应 ModernNotificationBuilder）：IMPORTANCE_HIGH 实况渠道
 *    + `Notification.ProgressStyle`（分段彩色 + setStyledByProgress，与 InstallerX 同款）
 *    + 请求实况提升的 extra + `setShortCriticalText(...)`，并已在清单声明 `POST_PROMOTED_NOTIFICATIONS`。
 *  - 旧式路径（Android 16 以下，对应 LegacyNotificationBuilder）：进行中用 IMPORTANCE_LOW 渠道
 *    + `setProgress(100, percent, false)`，并以主题色着色；结束时切到 IMPORTANCE_HIGH 渠道。
 *
 * 进度条颜色取自 [ThemeColors]，与 Material / Miuix 界面主题同源（同一套设置与同一个推导函数）。
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

    /**
     * 分段长度与开门阶段对齐：扫描/快速连接 0-30、连接与订阅 30-50、发送数据 50-100。
     * ProgressStyle 的进度上限等于各分段长度之和（=100），因此可以直接传百分比。
     */
    private const val SEGMENT_SCAN = 30
    private const val SEGMENT_CONNECT = 20
    private const val SEGMENT_SEND = 50

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

    /** 小米超级岛：标准通知 + 岛参数，固定实况渠道，进度用经典进度字段并以主题色着色。 */
    private fun miIslandProgress(context: Context, text: String, percent: Int?): Notification {
        val title = context.getString(R.string.unlock_open)
        val accent = ThemeColors.accent(context)
        return NotificationCompat.Builder(context, LIVE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_unlock)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openAppIntent(context))
            .setColor(accent.primary)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(PROGRESS_MAX, percent ?: 0, percent == null)
            .addExtras(MiIslandNotification.extras(context, title, text, percent, inProgress = true))
            .build()
    }

    /**
     * Android 16+：复刻 InstallerX ModernNotificationBuilder。
     *
     * 与 InstallerX 一样使用 `ProgressStyle().setProgressSegments(segments).setStyledByProgress(true)`：
     * 分段即进度条的彩色底层（按阶段着色），进度覆盖在其上，因此呈现「深色底 + 分段彩色」的观感，
     * 而不是一条纯白实心条。同时保留经典进度字段作为不渲染该样式时的兜底。
     */
    private fun modernProgress(context: Context, text: String, percent: Int?): Notification {
        val accent = ThemeColors.accent(context)
        val segments = listOf(
            Notification.ProgressStyle.Segment(SEGMENT_SCAN).setColor(accent.tertiary),
            Notification.ProgressStyle.Segment(SEGMENT_CONNECT).setColor(accent.primary),
            Notification.ProgressStyle.Segment(SEGMENT_SEND).setColor(accent.primary),
        )
        val progressStyle = Notification.ProgressStyle()
            .setProgressSegments(segments)
            .setStyledByProgress(true)
            .apply {
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
            .setColor(accent.primary)
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

    /** Android 16 以下：复刻 InstallerX LegacyNotificationBuilder（经典进度条 + 主题色）。 */
    private fun legacyProgress(context: Context, text: String, percent: Int?): Notification {
        val accent = ThemeColors.accent(context)
        return NotificationCompat.Builder(context, PROGRESS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_unlock)
            .setContentTitle(context.getString(R.string.unlock_open))
            .setContentText(text)
            .setContentIntent(openAppIntent(context))
            .setColor(accent.primary)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(PROGRESS_MAX, percent ?: 0, percent == null)
            .build()
    }

    /**
     * 开门结果（成功 / 带具体原因的失败）。
     *
     * 与 InstallerX 一致：结束时取消 ongoing 并切到高优先级渠道，进度条随之为结果状态让位；
     * 小米设备附带超级岛的结果态参数（岛通知保持在同一渠道）。失败用错误色，成功用主色。
     */
    fun result(context: Context, success: Boolean, text: String): Notification {
        val title = context.getString(if (success) R.string.unlock_success else R.string.unlock_failed)
        val accent = ThemeColors.accent(context)
        val islandSupported = MiIslandNotification.isSupported(context)
        val channelId = if (islandSupported || isModernEligible()) LIVE_CHANNEL_ID else RESULT_CHANNEL_ID
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_unlock)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openAppIntent(context))
            .setColor(if (success) accent.primary else accent.error)
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
