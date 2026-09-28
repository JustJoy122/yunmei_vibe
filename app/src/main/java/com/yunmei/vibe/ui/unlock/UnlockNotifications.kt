package com.yunmei.vibe.ui.unlock

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.yunmei.vibe.R
import com.yunmei.vibe.ui.MainActivity

/**
 * 快捷方式开门的通知（进行中 / 结果）。
 *
 * 样式全部交给系统通知排版，颜色不写死：渠道负责重要性与静音，正文取自字符串资源。
 * Android 16+ 的进度用 [Notification.ProgressStyle]，以下版本用传统进度条。
 */
object UnlockNotifications {

    const val CHANNEL_ID = "unlock"
    const val NOTIFICATION_ID = 1001

    /** 进度条最大值（百分比 0..100）。 */
    private const val PROGRESS_MAX = 100

    /** 创建通知渠道（幂等）。 */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.unlock_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.unlock_channel_desc)
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * 开门进行中（前台服务通知）。
     *
     * [percent] 为 null 表示不确定进度，否则为 0..100 的确定进度。
     */
    @SuppressLint("NewApi")
    fun progress(context: Context, text: String, percent: Int?): Notification {
        if (Build.VERSION.SDK_INT >= 36) {
            val style = Notification.ProgressStyle().apply {
                if (percent == null) {
                    setProgressIndeterminate(true)
                } else {
                    setProgressIndeterminate(false)
                    setProgress(percent)
                }
            }
            val builder = Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_unlock)
                .setContentTitle(context.getString(R.string.unlock_open))
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openAppIntent(context))
                // 关键：同时写入经典进度字段（max / progress / indeterminate）。
                // 系统若没有把 ProgressStyle 渲染成进度条（例如未获实况通知提升），
                // 通知栏也会显示真实进度条，而不是只有阶段文字。
                .setProgress(PROGRESS_MAX, percent ?: 0, percent == null)
                .setStyle(style)
            return builder.build()
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_unlock)
            .setContentTitle(context.getString(R.string.unlock_open))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openAppIntent(context))
        if (percent == null) {
            builder.setProgress(0, 0, true)
        } else {
            builder.setProgress(PROGRESS_MAX, percent, false)
        }
        return builder.build()
    }

    /** 开门结果：成功或带具体失败原因。 */
    fun result(context: Context, success: Boolean, text: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_unlock)
            .setContentTitle(
                context.getString(if (success) R.string.unlock_success else R.string.unlock_failed)
            )
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openAppIntent(context))
            .build()

    /** 发送通知；通知被系统关闭时静默跳过。 */
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
