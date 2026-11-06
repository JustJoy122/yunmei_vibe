package com.yunmei.vibe.data.model

/**
 * 一次开门的结果快照，用于上报 `lockrecord/savelockrecord`。
 *
 * 字段取自官方客户端上报时组装的参数：本应用没有的楼栋/房间号不参与，
 * 由 [com.yunmei.vibe.data.repository.YunMeiRepository.saveUnlockRecord] 传空串。
 */
data class UnlockRecord(
    val schoolNo: String,
    val lockNo: String,
    /** 开门账号的用户名（服务端按账号登记记录）；账号已删除时为空串。 */
    val userName: String,
    val success: Boolean,
    /** 门锁回传的结果摘要（官方是 AA/AB 解析后的 JSON），这里同样给 JSON 字符串。 */
    val resultJson: String,
    /** 从发起连接到收到结果的总秒数。 */
    val durationSeconds: Int,
    /** 门锁电量百分比；本轮没收到 AA/AB 时为 0（官方同样传 0）。 */
    val battery: Int,
    /** 开门时刻，格式 `yyyy-MM-dd HH:mm:ss`。 */
    val lockTime: String,
    /** 本次下发给门锁的临时密码；本应用不生成临时密码，恒为空串。 */
    val newPwd: String,
)
