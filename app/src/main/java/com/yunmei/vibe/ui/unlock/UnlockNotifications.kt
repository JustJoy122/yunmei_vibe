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
 *
 * 关于通知小图标与 ColorOS：
 *  - 三条路径（超级岛 / 实况 / 旧式）以及结果通知统一使用 R.drawable.ic_notification_unlock：
 *    24dp 矢量、透明背景、纯白单色剪影，仅 alpha 通道有效（颜色由系统决定），
 *    任何位置都不使用彩色 App 图标或带底色的图标。
 *  - 遵循 Android 规范的系统（原生 Android 与其他 ROM）会据此渲染单色剪影，并随深浅色自动反色。
 *  - ColorOS 15 及以上可能在通知初始化时把小图标强制替换为彩色 App 图标：这是系统级行为，
 *    App 层无法绕过（本项目也不会为此做系统级 Hook、不引入 LSPosed 等模块）。
 *    因此在 ColorOS 上看到彩色 App 图标属该系统行为，并不代表本 App 未设置单色小图标。
 */
object UnlockNotifications {

    /** 实况渠道（InstallerX: installer_live_channel，IMPORTANCE_HIGH）。 */
    const val LIVE_CHANNEL_ID = "unlock_live_channel"

    /** 进行中渠道（InstallerX: installer_progress_channel，IMPORTANCE_LOW）。 */
    const val PROGRESS_CHANNEL_ID = "unlock_progress_channel"

    /** 结束渠道（InstallerX: installer_channel，IMPORTANCE_HIGH）。 */
    const val RESULT_CHANNEL_ID = "unlock_channel"

    /** 固定通知 ID：进度与结果始终是同一条通知。 */
    /** 固定通知 ID：进度与结果始终是同一条通知（与打卡通知的 1002 区分）。 */
    const val UNLOCK_NOTIFICATION_ID = 1001

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
        // 旧版本把实况渠道建成了完全静音（setSound(null) + 关闭振动），而渠道创建后声音/振动
        // 无法直接更新，只能删除重建；否则失败通知永远无法"提醒一次"。
        val manager = NotificationManagerCompat.from(context)
        val existingLiveChannel = manager.getNotificationChannel(LIVE_CHANNEL_ID)
        if (existingLiveChannel != null &&
            existingLiveChannel.sound == null &&
            !existingLiveChannel.shouldVibrate()
        ) {
            manager.deleteNotificationChannel(LIVE_CHANNEL_ID)
        }
        val channels = listOf(
            NotificationChannelCompat.Builder(
                LIVE_CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_HIGH,
            )
                .setName(context.getString(R.string.unlock_live_channel_name))
                .setDescription(context.getString(R.string.unlock_live_channel_desc))
                // 与 InstallerX 一致：渠道保持系统默认提示能力，进行中靠 setSilent(true) 抑制，
                // 失败时才真正提醒一次——渠道一旦静音，失败就永远提醒不了。
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
        // 进行中：平台 Builder 没有 setSilent，静音改由渠道/onlyAlertOnce 控制：
        // 实况渠道保持系统默认提示能力（否则失败无法提醒），进行中靠 setOnlyAlertOnce(true) 抑制重复提醒。
        val notification = Notification.Builder(context, LIVE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_unlock)
            .setContentTitle(context.getString(R.string.unlock_open))
            .setContentText(text)
            .setContentIntent(openAppIntent(context))
            .setColor(accent.primary)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            // ColorOS 实况/胶囊形态只显示 shortCriticalText，必须包含阶段文字，
            // 否则「快速连接」等阶段只会看到一个百分比。
            .setShortCriticalText(if (percent == null) text else "$text $percent%")
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
    /**
     * 蓝牙未开启 / 用户拒绝开启时的失败通知。
     *
     * 与开门失败的处理方式一致：Android 16+ 保留实况样式与进度条（分段 30/20/50），
     * 把扫描分段标成 error 色（蓝牙没开，流程停在扫描阶段之前），
     * 低版本用经典进度条 + error 色；通知可划走，动作最多 2 个。
     */
    fun bluetoothFailure(
        context: Context,
        text: String,
        actions: List<Notification.Action> = emptyList(),
    ): Notification {
        val accent = ThemeColors.accent(context)
        val percent = SEGMENT_SCAN
        if (isModernEligible()) {
            val style = Notification.ProgressStyle()
                .setProgressSegments(
                    listOf(
                        Notification.ProgressStyle.Segment(SEGMENT_SCAN).setColor(accent.error),
                        Notification.ProgressStyle.Segment(SEGMENT_CONNECT).setColor(accent.tertiary),
                        Notification.ProgressStyle.Segment(SEGMENT_SEND).setColor(accent.primary),
                    )
                )
                .setStyledByProgress(true)
                .apply {
                    setProgressIndeterminate(false)
                    setProgress(percent)
                }
            val builder = Notification.Builder(context, LIVE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_unlock)
                .setContentTitle(context.getString(R.string.unlock_failed))
                .setContentText(text)
                .setContentIntent(openAppIntent(context))
                .setColor(accent.error)
                .setOngoing(false)
                .setAutoCancel(true)
                .setOnlyAlertOnce(false)
                .setStyle(style)
            for (action in actions) builder.addAction(action)
            return builder.build()
        }
        val builder = NotificationCompat.Builder(context, RESULT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_unlock)
            .setContentTitle(context.getString(R.string.unlock_failed))
            .setContentText(text)
            .setContentIntent(openAppIntent(context))
            .setColor(accent.error)
            .setOngoing(false)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setSilent(false)
            .setProgress(PROGRESS_MAX, percent, false)
        for (action in actions) builder.addAction(0, action.title, action.actionIntent)
        return builder.build()
    }

    /**
     * 开门失败通知（严格对齐 InstallerX）：
     *  - 保持实况：Android 16+ 继续用 ProgressStyle，不退回传统通知；进度条保留；
     *  - 失败的阶段对应的 Segment 变红，其余分段仍用主题色（分段变红，不是整条变红）；
     *  - 进度停在失败阶段末尾（InstallerX 做法），渠道固定实况渠道（实况路径不切渠道）；
     *  - setOnlyAlertOnce(false).setSilent(false)：确保失败提醒一次；
     *  - 保持常驻（ongoing，且不 autoCancel），由用户点「重试」或「完成」结束。
     */
    fun failure(
        context: Context,
        text: String,
        stage: Int,
        actions: List<Notification.Action> = emptyList(),
    ): Notification {
        val accent = ThemeColors.accent(context)
        val percent = when (stage) {
            0 -> SEGMENT_SCAN
            1 -> SEGMENT_SCAN + SEGMENT_CONNECT
            else -> PROGRESS_MAX
        }
        if (isModernEligible()) {
            val segments = listOf(
                Notification.ProgressStyle.Segment(SEGMENT_SCAN)
                    .setColor(if (stage == 0) accent.error else accent.tertiary),
                Notification.ProgressStyle.Segment(SEGMENT_CONNECT)
                    .setColor(if (stage == 1) accent.error else accent.primary),
                Notification.ProgressStyle.Segment(SEGMENT_SEND)
                    .setColor(if (stage >= 2) accent.error else accent.primary),
            )
            val style = Notification.ProgressStyle()
                .setProgressSegments(segments)
                .setStyledByProgress(true)
                .apply {
                    setProgressIndeterminate(false)
                    setProgress(percent)
                }
            // 失败：保持实况样式与进度条，不退回传统通知；渠道固定实况渠道（不切渠道）。
            // 平台 Builder 没有 setSilent，提醒能力由实况渠道提供，这里只关掉 onlyAlertOnce。
            val builder = Notification.Builder(context, LIVE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_unlock)
                .setContentTitle(context.getString(R.string.unlock_failed))
                .setContentText(text)
                .setContentIntent(openAppIntent(context))
                .setColor(accent.error)
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(false)
                .setProgress(PROGRESS_MAX, percent, false)
                .setStyle(style)
            for (action in actions) builder.addAction(action)
            val failed = builder.build()
            // 失败同样要保持"实况/流体云"提升：InstallerX 的基础构建器全程 setRequestPromotedOngoing(true)，
            // 失败分支复用同一个 builder；这份之前漏了这个 extra，导致失败即掉出实况。
            failed.extras.putBoolean(NotificationCompat.EXTRA_REQUEST_PROMOTED_ONGOING, true)
            return failed
        }
        // 低版本没有实况样式：保留进度条与错误色；失败切到高优先级结果渠道（InstallerX 旧式实现同样会切）。
        val builder = NotificationCompat.Builder(context, RESULT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_unlock)
            .setContentTitle(context.getString(R.string.unlock_failed))
            .setContentText(text)
            .setContentIntent(openAppIntent(context))
            .setColor(accent.error)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(false)
            .setSilent(false)
            .setProgress(PROGRESS_MAX, percent, false)
        for (action in actions) builder.addAction(0, action.title, action.actionIntent)
        return builder.build()
    }

    /** 按当前进度推断失败发生在哪个分段（0=扫描、1=连接、2=发送）。 */
    fun stageOf(percent: Int): Int = when {
        percent < SEGMENT_SCAN -> 0
        percent < SEGMENT_SCAN + SEGMENT_CONNECT -> 1
        else -> 2
    }

    /** 「重试」：通知按钮直接后台重跑开门（前台服务动作，不受后台启动限制）。 */
    fun retryAction(context: Context): Notification.Action = Notification.Action.Builder(
        null,
        context.getString(R.string.unlock_retry),
        PendingIntent.getForegroundService(
            context,
            10,
            Intent(context, UnlockService::class.java).setAction(UnlockService.ACTION_RETRY),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ),
    ).build()

    /** 「完成」：收起通知并结束服务。 */
    fun finishAction(context: Context): Notification.Action = Notification.Action.Builder(
        null,
        context.getString(R.string.unlock_finish),
        PendingIntent.getForegroundService(
            context,
            11,
            Intent(context, UnlockService::class.java).setAction(UnlockService.ACTION_FINISH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ),
    ).build()

    /** 「开启蓝牙」：用 PendingIntent.getActivity 打开跳板 Activity 发起系统请求（用户点击触发，可靠）。 */
    fun enableBluetoothAction(context: Context): Notification.Action = Notification.Action.Builder(
        null,
        context.getString(R.string.unlock_bluetooth_enable),
        PendingIntent.getActivity(
            context,
            12,
            Intent(context, UnlockShortcutActivity::class.java)
                .setAction(UnlockShortcut.ACTION)
                .putExtra(UnlockShortcut.EXTRA_TOKEN, UnlockShortcut.token(context))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ),
    ).build()

    fun post(context: Context, notification: Notification) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        runCatching { manager.notify(UNLOCK_NOTIFICATION_ID, notification) }
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
