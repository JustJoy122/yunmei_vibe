package com.yunmei.vibe.ui.sign

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
 * 打卡通知（进行中 / 结果）。
 *
 * 对齐 InstallerX 的通知规范：
 *  - 通知 ID 全程固定（[SIGN_NOTIFICATION_ID]），进度与结果都是原地更新，不新建；
 *  - 实况路径固定使用 [LIVE_CHANNEL_ID]，**全程不切渠道**（InstallerX 的 MiIsland 实现里明确写了
 *    切渠道会让小米岛/实况渲染重建、出现闪烁或黑隙；实况路径同理）；
 *  - Android 16+ 用 [Notification.ProgressStyle]，分段长度之和即进度上限（50 + 50 = 100）：
 *    定位阶段 0-50、打卡请求阶段 50-100。失败时**保留实况与进度条**、进度停在失败发生处
 *    （每次询问模式按要求停在 50%），并把失败阶段对应的 Segment 改成 error 色，通知不消失；
 *  - 通知上最多 2 个动作：使用上次位置（前台服务动作）、选择定位方式（打开轻量弹窗）。
 */
object SignNotifications {

    /** 实况渠道（不切渠道，全程使用）。 */
    const val LIVE_CHANNEL_ID = "sign_live_channel"

    /** 低版本进度渠道。 */
    const val PROGRESS_CHANNEL_ID = "sign_progress_channel"

    /** 结果渠道。 */
    const val RESULT_CHANNEL_ID = "sign_result_channel"

    /** 固定通知 ID（与开门通知的 1001 区分开）。 */
    const val SIGN_NOTIFICATION_ID = 1002

    private const val PROGRESS_MAX = 100

    /** 分段：定位 50 + 打卡 50，和即进度上限。 */
    private const val SEGMENT_LOCATE = 50
    private const val SEGMENT_SIGN = 50

    /** 阶段编号：用于失败时决定哪个分段变红。 */
    const val STAGE_LOCATE = 0
    const val STAGE_SIGN = 1

    /** Android 16 起才有实况样式。 */
    private fun isModernEligible(): Boolean = Build.VERSION.SDK_INT >= 36

    fun ensureChannel(context: Context) {
        val channels = listOf(
            NotificationChannelCompat.Builder(
                LIVE_CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_HIGH,
            )
                .setName(context.getString(R.string.sign_live_channel_name))
                .setDescription(context.getString(R.string.sign_live_channel_desc))
                // 与 InstallerX 的实况渠道一致：保持系统默认提示，进行中靠 setOnlyAlertOnce(true) 抑制，
                // 失败时才真正提醒一次——渠道一旦静音，失败就无法提醒。
                .build(),
            NotificationChannelCompat.Builder(
                PROGRESS_CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_LOW,
            )
                .setName(context.getString(R.string.sign_progress_channel_name))
                .setDescription(context.getString(R.string.sign_progress_channel_desc))
                .build(),
            NotificationChannelCompat.Builder(
                RESULT_CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_HIGH,
            )
                .setName(context.getString(R.string.sign_result_channel_name))
                .setDescription(context.getString(R.string.sign_result_channel_desc))
                .build(),
        )
        NotificationManagerCompat.from(context).createNotificationChannelsCompat(channels)
    }

    /**
     * 进行中通知（每次询问模式）。
     *
     * [percent] 为 0..100；[failedStage] 不为 null 时表示失败，对应的分段用 error 色，
     * 并强制提醒一次（setOnlyAlertOnce(false) + setSilent(false)），通知保持不消失。
     */
    fun progress(
        context: Context,
        text: String,
        percent: Int,
        failedStage: Int? = null,
        locationUnavailable: Boolean = false,
        completed: Boolean = false,
        retrySubMode: String? = null,
        promptChooser: Boolean = false,
    ): Notification {
        val accent = ThemeColors.accent(context)
        // 需要选择定位方式时，通知整体点击也直接进弹窗（与按钮同一个 PendingIntent），
        // 避免个别 ROM 折叠/吞掉通知动作时用户完全无法选择。
        val contentIntent = if (promptChooser) chooserIntent(context) else openAppIntent(context)
        val actions = if (failedStage != null) {
            failureActions(context, locationUnavailable, retrySubMode)
        } else {
            normalActions(context)
        }

        if (isModernEligible()) {
            val style = Notification.ProgressStyle()
                .setProgressSegments(
                    listOf(
                        Notification.ProgressStyle.Segment(SEGMENT_LOCATE)
                            .setColor(if (failedStage == STAGE_LOCATE) accent.error else accent.tertiary),
                        Notification.ProgressStyle.Segment(SEGMENT_SIGN)
                            .setColor(if (failedStage == STAGE_SIGN) accent.error else accent.primary),
                    )
                )
                .setStyledByProgress(true)
                .apply {
                    setProgressIndeterminate(false)
                    setProgress(percent)
                }
            val builder = Notification.Builder(context, LIVE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_sign)
                .setContentTitle(context.getString(R.string.unlock_sign))
                .setContentText(text)
                .setContentIntent(contentIntent)
                .setColor(accent.primary)
                .setOngoing(failedStage == null && !completed)
                .setAutoCancel(completed)
                .setProgress(PROGRESS_MAX, percent, false)
                .setStyle(style)
            if (failedStage != null) {
                // 失败：保留实况与进度条，并确保提醒一次。
                // 平台 Notification.Builder 没有 setSilent，提醒能力由渠道提供，这里只关掉 onlyAlertOnce。
                builder.setOnlyAlertOnce(false)
            } else {
                builder.setOnlyAlertOnce(true)
            }
            for (action in actions) builder.addAction(action)
            val notification = builder.build()
            notification.extras.putBoolean(NotificationCompat.EXTRA_REQUEST_PROMOTED_ONGOING, true)
            return notification
        }

        // 低版本：经典进度条 + 主题色（无分段能力，失败靠文案与颜色区分）。
        // 低版本没有实况样式，失败时切到结果渠道（与 InstallerX 旧式实现一致：进行中用低优先级进度渠道，
        // 进入需要关注/结果状态时切到高优先级渠道）；实况路径则全程不切渠道。
        val channelId = if (failedStage != null) RESULT_CHANNEL_ID else PROGRESS_CHANNEL_ID
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_sign)
            .setContentTitle(context.getString(R.string.unlock_sign))
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setColor(if (failedStage != null) accent.error else accent.primary)
            .setOngoing(failedStage == null && !completed)
            .setAutoCancel(completed)
            .setProgress(PROGRESS_MAX, percent, false)
        if (failedStage != null) {
            builder.setOnlyAlertOnce(false).setSilent(false)
        } else {
            builder.setOnlyAlertOnce(true)
        }
        // NotificationCompat.Builder 只接受 NotificationCompat.Action，
        // 这里用三参重载转换（本项目的动作都没有图标，icon 传 0 即可）。
        for (action in actions) {
            builder.addAction(0, action.title, action.actionIntent)
        }
        return builder.build()
    }

    /** 结果通知（使用上次位置 / 重新定位并保存两种静默模式，以及进行中模式的最终结果）。 */
    fun result(context: Context, success: Boolean, text: String): Notification {
        val accent = ThemeColors.accent(context)
        return NotificationCompat.Builder(context, RESULT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_sign)
            .setContentTitle(
                context.getString(if (success) R.string.unlock_sign_success else R.string.unlock_sign_failed)
            )
            .setContentText(text)
            .setContentIntent(openAppIntent(context))
            .setColor(if (success) accent.primary else accent.error)
            .setAutoCancel(true)
            .setOngoing(false)
            .setOnlyAlertOnce(false)
            .setSilent(false)
            .build()
    }

    /** 用固定 ID 原地更新同一条通知。 */
    fun post(context: Context, notification: Notification) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        runCatching { manager.notify(SIGN_NOTIFICATION_ID, notification) }
    }

    /**
     * 最多 2 个动作（与 InstallerX 一致，避免国产 ROM 折叠/裁剪）：
     *  - 使用上次位置：PendingIntent.getForegroundService，直接后台执行；
     *  - 选择定位方式：PendingIntent.getActivity 打开轻量弹窗（不打开主界面）。
     */
    /** 进行中（每次询问）：使用上次位置 + 选择定位方式，共 2 个动作。 */
    private fun normalActions(context: Context): List<Notification.Action> = listOf(
        Notification.Action.Builder(
            null,
            context.getString(R.string.unlock_sign_use_last),
            useLastIntent(context),
        ).build(),
        Notification.Action.Builder(
            null,
            context.getString(R.string.sign_choose_location),
            chooserIntent(context),
        ).build(),
    )

    /**
     * 失败：动作固定为 2 个——「重试」+（定位未开启时）「去开启定位」/（其他失败）「完成」。
     *
     * [retrySubMode] 为重试时沿用的定位方式；为空表示按设置里的模式重试（每次询问会重新回到选择状态）。
     */
    private fun failureActions(
        context: Context,
        locationUnavailable: Boolean,
        retrySubMode: String?,
    ): List<Notification.Action> {
        val second = if (locationUnavailable) {
            Notification.Action.Builder(
                null,
                context.getString(R.string.sign_open_location_settings),
                locationSettingsIntent(context),
            ).build()
        } else {
            Notification.Action.Builder(
                null,
                context.getString(R.string.sign_finish),
                finishIntent(context),
            ).build()
        }
        return listOf(
            Notification.Action.Builder(
                null,
                context.getString(R.string.sign_retry),
                retryIntent(context, retrySubMode),
            ).build(),
            second,
        )
    }

    private fun useLastIntent(context: Context): PendingIntent =
        PendingIntent.getForegroundService(
            context,
            0,
            Intent(context, SignService::class.java).setAction(SignService.ACTION_USE_LAST),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun retryIntent(context: Context, retrySubMode: String?): PendingIntent {
        val intent = Intent(context, SignService::class.java).setAction(SignService.ACTION_RETRY)
        if (retrySubMode != null) {
            intent.putExtra(SignService.EXTRA_SUB_MODE, retrySubMode)
        }
        return PendingIntent.getForegroundService(
            context,
            4,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun finishIntent(context: Context): PendingIntent =
        PendingIntent.getForegroundService(
            context,
            5,
            Intent(context, SignService::class.java).setAction(SignService.ACTION_FINISH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun locationSettingsIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            3,
            Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun chooserIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            1,
            Intent(context, SignLocationActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
