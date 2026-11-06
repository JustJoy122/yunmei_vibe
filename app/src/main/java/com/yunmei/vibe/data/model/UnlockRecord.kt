package com.yunmei.vibe.data.model

/**
 * 一次开门的结果快照，用于上报 `lockrecord/savelockrecord`。
 *
 * 字段与官方前端组装的上报参数一一对应（`common/openLock.js` / `pages/index/index.js`）：
 * 官方会带上 `userName`（用户 id）、`lockDevice`（本机设备号）、`lockLog`（阶段流水）、
 * `voltage`（AB 原始电压）、`location` 等字段，缺任何一项服务端都可能按无效记录丢弃。
 * 账号 id 与设备号由仓库层补齐，不放在这个模型里。
 */
data class UnlockRecord(
    val schoolNo: String,
    val lockNo: String,
    /** 校区 / 楼栋 / 房间编号，来自 getuserlock；扫码导入的门锁可能为空。 */
    val areaNo: String,
    val buildNo: String,
    val dormNo: String,
    val success: Boolean,
    /** 门锁回传的结果摘要（官方是 AA/AB 解析后的 JSON），这里保持同样的 JSON 形状。 */
    val resultJson: String,
    /** 从发起连接到收到结果的总秒数。 */
    val durationSeconds: Int,
    /** 门锁电量百分比；没收到 AA/AB 时为 0（官方同样传 0）。 */
    val battery: Int,
    /** AB 回传的原始电压值；没收到时为 0。 */
    val voltage: Int,
    /** 阶段流水（`HH:mm:ss.SSS 描述;` 逐行拼接），官方用于排查开门失败。 */
    val lockLog: String,
    /** 开门时刻，格式 `yyyy-MM-dd HH:mm:ss`。 */
    val lockTime: String,
    /** 本次生成的一次性临时密码（6 位数字），与官方 createPwd 行为一致。 */
    val newPwd: String,
    /** 开门时的定位（官方必传，本应用未采集蓝牙开门定位，统一传空串）。 */
    val location: String,
)
