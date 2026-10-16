package com.yunmei.vibe.ui.util

import android.app.Notification
import android.app.Service
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/**
 * 前台服务相关的通知工具。
 *
 * 1) [ForegroundPlaceholder.startThenRemove]：SignService.finishAndDismiss() 与
 *    UnlockService.dismissAndStop() 原本各自手写"用占位通知完成 startForeground（5 秒规则）
 *    → 立即移除 → stopSelf"三步，这里抽成同一实现；stopSelf 仍由调用方执行，时序不变。
 * 2) [requestPromotedOngoing]：androidx 未提供 setRequestPromotedOngoing，项目统一用写 extra 的等价写法。
 */
object ForegroundPlaceholder {

    /**
     * 用占位通知顶替同 ID 的通知并完成一次 startForeground，随即移除前台通知。
     *
     * @param foregroundType 前台服务类型位；传 null 时走 Service.startForeground(id, notification)
     *        （类型由清单声明决定，UnlockService 用这条），非 null 时走 ServiceCompat 带类型版本
     *        （SignService 需按是否已授权定位动态传入）。
     */
    fun startThenRemove(
        service: Service,
        id: Int,
        channelId: String,
        smallIcon: Int,
        title: String,
        foregroundType: Int? = null,
    ) {
        val placeholder = Notification.Builder(service, channelId)
            .setSmallIcon(smallIcon)
            .setContentTitle(title)
            .build()
        runCatching {
            if (foregroundType == null) {
                service.startForeground(id, placeholder)
            } else {
                ServiceCompat.startForeground(service, id, placeholder, foregroundType)
            }
        }
        runCatching { ServiceCompat.stopForeground(service, ServiceCompat.STOP_FOREGROUND_REMOVE) }
    }
}

/**
 * 请求把实况/流体云通知"提升"为 ongoing（与 androidx 的 setRequestPromotedOngoing(true) 等价）。
 *
 * 渠道与实况形态本身是行为敏感区，因此这里只抽这一条 extra 的写法，不触碰任何渠道/样式判断。
 */
fun Notification.requestPromotedOngoing(): Notification = apply {
    extras.putBoolean(NotificationCompat.EXTRA_REQUEST_PROMOTED_ONGOING, true)
}