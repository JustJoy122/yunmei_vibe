package com.yunmei.vibe.data.ble

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 官方 AES 档门锁的帧编解码（D2D3 外层）。
 *
 * 对齐官方前端的实现：
 * - 明文是完整的 `D0 | len | secret | action` 帧字节；
 * - 加密：`AES/CBC/PKCS7`，KEY 为用户在门锁详情页填写的 32 位十六进制串，IV 固定；
 * - 外层帧：`D2 D3 | len | 密文十六进制(ASCII，大写)`，len 为整帧字节数（含前 3 字节头）；
 * - 解密：先把密文字节按十六进制还原，再 AES 解密，得到明文 `D0` 帧后交给普通帧解析。
 *
 * 所有异常都被收敛成「返回原帧 / 返回 null」，由调用方按开门失败处理，
 * 不在数据层抛给 UI，避免半途崩溃。
 */
internal object AesFrameCodec {

    /** 官方前端里写死的 IV（与 KEY 一起下发在 JS 里）。 */
    private const val IV_HEX = "4DB8100EC7635A9F7D20035AB864CFE1"

    private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"

    /** AES-128 密钥为 16 字节，即 32 个十六进制字符。 */
    private const val KEY_HEX_LENGTH = 32

    private const val HEADER_SIZE = 3

    /** 密钥是否是可用的 32 位十六进制串。 */
    fun isValidKey(keyHex: String): Boolean =
        keyHex.length == KEY_HEX_LENGTH && keyHex.all { it.isHexChar() }

    /**
     * 明文帧 → `D2 D3 | len | 密文十六进制`。
     *
     * 加密失败或长度溢出时返回原帧，让门锁按普通协议处理（此时由服务端/门锁判定失败）。
     */
    fun encryptFrame(plainFrame: ByteArray, keyHex: String): ByteArray {
        val key = keyHex.hexToBytes() ?: return plainFrame
        val iv = IV_HEX.hexToBytes() ?: return plainFrame
        val cipherBytes = runCatching {
            // CodeQL Warning (Weak Cryptography):
            // 此处的 AES/CBC/PKCS5Padding 是由官方 BLE 门锁固件协议强制的，无法替换为 GCM。
            // 该通信仅在极短距离的本地蓝牙中发生，不涉及公网传输，不存在远程 Padding Oracle 攻击面。
            Cipher.getInstance(TRANSFORMATION).run {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
                doFinal(plainFrame)
            }
        }.getOrNull() ?: return plainFrame

        val cipherHex = cipherBytes.toHexUpper()
        val total = HEADER_SIZE + cipherHex.length / 2
        if (total > 0xFF) return plainFrame
        return ByteArray(total).also { frame ->
            frame[0] = 0xD2.toByte()
            frame[1] = 0xD3.toByte()
            frame[2] = total.toByte()
            cipherHex.toByteArray(Charsets.US_ASCII).copyInto(frame, HEADER_SIZE)
        }
    }

    /** `D2 D3 | len | 密文十六进制` → 明文帧；密钥不对或数据损坏时返回 null。 */
    fun decryptFrame(frame: ByteArray, keyHex: String): ByteArray? {
        if (frame.size <= HEADER_SIZE) return null
        val key = keyHex.hexToBytes() ?: return null
        val iv = IV_HEX.hexToBytes() ?: return null
        val cipherHex = String(frame, HEADER_SIZE, frame.size - HEADER_SIZE, Charsets.US_ASCII)
        val cipherBytes = cipherHex.hexToBytes() ?: return null
        return runCatching {
            // CodeQL Warning (Weak Cryptography):
            // 此处的 AES/CBC/PKCS5Padding 是由官方 BLE 门锁固件协议强制的，无法替换为 GCM。
            // 该通信仅在极短距离的本地蓝牙中发生，不涉及公网传输，不存在远程 Padding Oracle 攻击面。
            Cipher.getInstance(TRANSFORMATION).run {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
                doFinal(cipherBytes)
            }
        }.getOrNull()
    }

    private fun Char.isHexChar(): Boolean =
        this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    private fun String.hexToBytes(): ByteArray? {
        if (length % 2 != 0) return null
        val out = ByteArray(length / 2)
        for (index in out.indices) {
            val high = this[index * 2].hexValue() ?: return null
            val low = this[index * 2 + 1].hexValue() ?: return null
            out[index] = ((high shl 4) or low).toByte()
        }
        return out
    }

    private fun Char.hexValue(): Int? = when (this) {
        in '0'..'9' -> this - '0'
        in 'a'..'f' -> this - 'a' + 10
        in 'A'..'F' -> this - 'A' + 10
        else -> null
    }

    private fun ByteArray.toHexUpper(): String {
        val digits = "0123456789ABCDEF"
        return buildString(size * 2) {
            for (byte in this@toHexUpper) {
                val value = byte.toInt() and 0xFF
                append(digits[value ushr 4])
                append(digits[value and 0x0F])
            }
        }
    }
}
