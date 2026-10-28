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
    val lockNo: String = "",
    /** 服务端门锁型号 id（getuserlock 返回的 lockTypeId）；null 表示服务端没有给出。 */
    val lockTypeId: Int? = null,
    /**
     * 该型号是否要求 AES 加密协议，由 locktype/all 的 aesSupport 按 lockTypeId 匹配得到。
     * true = 官方客户端会再套一层 AES，当前版本未实现，开锁前直接拦截；
     * false / null = 继续走普通 OPEN 帧（null 表示型号未知或型号能力表没取到）。
     */
    val aesSupport: Boolean? = null,
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
            if (parts.size < 5) return null

            // 旧版 4 段格式：label|mac|write|service|secret，无账号信息。
            val fields = if (parts.size == 4) {
                listOf("account", parts[0], parts[1], parts[2], parts[3])
            } else {
                parts
            }
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
