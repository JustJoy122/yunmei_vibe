package com.yunmei.vibe.data.model

import androidx.compose.runtime.Immutable

/**
 * 学校打卡状态（官方 `signrecord/getsignstatusbyschool`）。
 *
 * 官方返回的是一个数组，每项描述「一个打卡时段」：
 * ```
 * { "set": { "startTime": "22:00", "endTime": "23:00" },
 *   "signRecord": { "signTime": "2026-11-09 22:13:00" } | null,
 *   "askLeave": false }
 * ```
 * 学校未开启打卡时数组为空（或整个响应为 null），仓库层会转成 null，界面据此隐藏状态行。
 *
 * @property startTime 打卡时段开始时间（服务端原样字符串，通常是 `HH:mm`）
 * @property endTime 打卡时段结束时间
 * @property signTime 已打卡时间；只保留 `HH:mm:ss`，未打卡时为空串
 * @property askLeave 是否已请假（请假期间不要求打卡）
 */
@Immutable
data class SignStatus(
    val startTime: String = "",
    val endTime: String = "",
    val signTime: String = "",
    val askLeave: Boolean = false,
) {
    /** 是否已完成打卡。 */
    val signed: Boolean get() = signTime.isNotBlank()

    /** 是否存在可展示的打卡时段。 */
    val hasWindow: Boolean get() = startTime.isNotBlank() || endTime.isNotBlank()
}
