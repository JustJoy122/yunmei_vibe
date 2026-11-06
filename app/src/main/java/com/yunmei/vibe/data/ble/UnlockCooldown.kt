package com.yunmei.vibe.data.ble

import android.os.SystemClock

/**
 * 开门冷却：门锁执行开门后需要一段时间复位（官方服务端下发的 `openDelay` 为 8 秒）。
 *
 * 云莓原先只在 UI 层用 `isOpening` 防连点，底层没有时间戳校验：
 * 通知栏快捷方式、蓝牙重新开启后的自动续开都可能在上一次尚未复位时再次连接。
 * 这里用单调时钟记录「上一次开门成功」的时刻，冷却期内直接拒绝发起蓝牙连接。
 *
 * 只记成功：失败（含超时、扫描不到）不占用冷却，用户可立即重试。
 */
object UnlockCooldown {

    /** 与官方 openDelay 一致：8 秒。 */
    private const val INTERVAL_MS = 8_000L

    @Volatile
    private var lastSuccessAt: Long = UNSET

    /** 开门成功后打点。由 [UnlockManager] 在收到门锁 A7 确认时调用，保证所有入口共用同一份时间戳。 */
    fun markSuccess() {
        lastSuccessAt = SystemClock.elapsedRealtime()
    }

    /** 剩余冷却毫秒数；0 表示可以立即开门。 */
    fun remainingMs(): Long {
        val last = lastSuccessAt
        if (last == UNSET) return 0L
        val elapsed = SystemClock.elapsedRealtime() - last
        return (INTERVAL_MS - elapsed).coerceAtLeast(0L)
    }

    /** 冷却剩余秒数（向上取整），用于提示文案。 */
    fun remainingSeconds(): Int = ((remainingMs() + 999L) / 1000L).toInt()

    private const val UNSET = Long.MIN_VALUE
}
