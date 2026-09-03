package com.yunmei.client.data.security

import java.security.MessageDigest

object Md5 {
    /** 与旧客户端一致：小写 32 位 MD5 十六进制字符串。 */
    fun hex(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                val value = byte.toInt() and 0xFF
                if (value < 0x10) append('0')
                append(value.toString(16))
            }
        }
    }
}
