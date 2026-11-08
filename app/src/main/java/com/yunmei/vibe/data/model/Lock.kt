package com.yunmei.vibe.data.model

import android.util.Base64
import kotlinx.serialization.Serializable

/**
 * 门锁领域模型。
 *
 * 字段命名尽量直白，同时保留与旧客户端/PWA 的 `|` 分隔 + Base64 分享格式兼容，
 * 以便旧二维码、旧链接仍然能被解析。
 */
@Serializable
data class Lock(
    val label: String = "",
    /** 已知 MAC；未知时原项目用 lockNo 顶替，用于快速连接失败后回退扫描。 */
    val mac: String = "",
    /** 写特征 UUID（原 D_CHAR / lockCharacterUuid）。 */
    val writeUuid: String = "",
    /** 服务 UUID（原 D_SERV / lockServiceUuid）。 */
    val serviceUuid: String = "",
    /** 锁密钥（原 D_SEC / lockSecret）。 */
    val secret: String = "",
    /** 用户名 MD5（用于找回保存的账号）。 */
    val usernameMd5: String = "",
    val schoolNo: String = "",
    /** 学校名称（getuserlock 的 schoolName），门锁列表卡片上作为副标题展示。 */
    val schoolName: String = "",
    val lockNo: String = "",
    /** 服务端门锁型号 id（getuserlock 返回的 lockTypeId）；null 表示服务端没有给出。 */
    val lockTypeId: Int? = null,
    /**
     * 该型号是否要求 AES 加密协议，由 locktype/all 的 aesSupport 按 lockTypeId 匹配得到。
     * true = 官方客户端会再套一层 D2D3 加密帧，需要 [customAesKey] 才能开门；
     * false / null = 继续走普通 OPEN 帧（null 表示型号未知或型号能力表没取到）。
     */
    val aesSupport: Boolean? = null,
    /**
     * 该型号是否支持密码指令（locktype/all 的 pwdSupport）。
     *
     * true = 开门帧要按官方闭环先写临时密码（A5）再开门（A7）；
     * false = 型号明确不支持密码，只发 A7；
     * null = 型号未知（没拉过能力表的老锁/离线导入的锁），保守地只发 A7，避免老固件不认组合帧。
     */
    val pwdSupport: Boolean? = null,
    /** 临时密码位数（locktype/all 的 pwdLength），缺省按 6 位处理。 */
    val pwdLength: Int? = null,
    /**
     * AES 型号的自定义密钥（32 位十六进制，对应官方前端 AesFrameCodec 里的固定 KEY）。
     * 仅当 [aesSupport] 为 true 时使用；为空表示用户还没设置，开门前会被拦截并提示去设置。
     */
    val customAesKey: String? = null,
    /** 服务端记录的门锁电量百分比（getuserlock 的 lockElectricity）；null 表示服务端没给。 */
    val lockElectricity: Int? = null,
    /** 服务端数据最后一次更新时间（getuserlock 的 updateTime），用于界面展示。 */
    val updateTime: String? = null,
    /** 校区/楼栋/房间编号，开门记录上报时要原样带回学校服务。 */
    val areaNo: String = "",
    val buildNo: String = "",
    val dormNo: String = "",
) {

    val isUsable: Boolean
        get() = writeUuid.isNotBlank() && serviceUuid.isNotBlank()

    /** 生成旧客户端兼容的分享链接。 */
    fun toShareUrl(): String {
        val body = listOf(
            label, mac, writeUuid, serviceUuid, secret,
            usernameMd5, schoolNo, lockNo, DATA_VER,
        ).joinToString("|")
        val encoded = Base64.encodeToString(body.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return if (secret.isBlank()) {
            "https://yunmeiui.xypp.cc/#/lock_id/$encoded"
        } else {
            "https://yunmeiui.xypp.cc/#/lock_info/$encoded"
        }
    }

    /** 去掉敏感信息后的公开分享体（写 NFC 用）。 */
    fun withoutSecret(): Lock = copy(secret = "", usernameMd5 = "", lockNo = "", schoolNo = "")

    companion object {
        private const val DATA_VER = "1.2"

        /** 解析旧客户端/PWA 的分享链接或裸 Base64。 */
        fun from(raw: String?): Lock? {
            val text = raw?.takeIf { it.isNotBlank() } ?: return null
            var value = text
            listOf("addlock/", "lock_id/", "lock_info/", "lockInfo/").forEach { prefix ->
                val idx = value.indexOf(prefix)
                if (idx >= 0) {
                    value = value.substring(idx + prefix.length)
                }
            }
            val decoded = runCatching {
                String(Base64.decode(value, Base64.NO_WRAP), Charsets.UTF_8)
            }.getOrNull() ?: return null

            val parts = decoded.split("|")

            // 旧版 4 段格式（沿用原项目 Lock.java）：mac|writeUuid|serviceUuid|secret，
            // 没有门锁名和账号信息：补一个固定名称 "account" 后按 5 段处理。
            val fields = if (parts.size == 4) {
                listOf("account", parts[0], parts[1], parts[2], parts[3])
            } else {
                parts
            }
            if (fields.size < 5) return null
            return Lock(
                label = fields[0],
                mac = fields.getOrElse(1) { "" },
                writeUuid = fields.getOrElse(2) { "" },
                serviceUuid = fields.getOrElse(3) { "" },
                secret = fields.getOrElse(4) { "" },
                usernameMd5 = fields.getOrElse(5) { "" },
                schoolNo = fields.getOrElse(6) { "" },
                lockNo = fields.getOrElse(7) { "" },
            )
        }
    }
}
