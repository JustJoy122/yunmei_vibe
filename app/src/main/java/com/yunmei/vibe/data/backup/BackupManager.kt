package com.yunmei.vibe.data.backup

import android.content.Context
import com.yunmei.vibe.YunMeiApp
import com.yunmei.vibe.data.preferences.SettingsPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

/** 备份/还原过程中的可读错误（界面直接展示 message）。 */
class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 极简备份与还原：全量导出 + 全量还原，单文件、口令加密。
 *
 * 文件格式（定长头，便于快速识别与解析）：
 *   [magic 8B "YMBACKUP"][fileVersion Int 4B][salt 16B][iv 12B][AES-GCM 密文]
 * 密钥派生：PBKDF2WithHmacSHA256，salt 随机 16 字节，迭代 [ITERATIONS] 次，输出 256 位密钥。
 * 加密：AES/GCM/NoPadding（128 位认证标签），IV 随机 12 字节。
 *
 * 还原策略：**先完整读取-解密-解析-校验，全部通过后才写入**，任何一步失败都不触碰现有数据。
 */
object BackupManager {

    private const val MAGIC = "YMBACKUP"
    private const val FILE_VERSION = 1
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256

    /** PBKDF2 迭代次数（兼顾手机端耗时与抗暴力破解）。 */
    const val ITERATIONS = 200_000

    /** 备份文件中不参与导出的键（设备绑定/安全存储相关）。 */
    private val EXCLUDED_PREF_KEYS = setOf(SettingsPrefs.SHORTCUT_TOKEN)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    /** 导出：读取当前账号/门锁/设置 → JSON → 口令加密 → 写入 [out]。 */
    suspend fun export(context: Context, password: CharArray, out: OutputStream): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val envelope = snapshot(context)
                val plain = json.encodeToString(BackupEnvelope.serializer(), envelope).toByteArray(Charsets.UTF_8)
                val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
                val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(
                    Cipher.ENCRYPT_MODE,
                    deriveKey(password, salt),
                    GCMParameterSpec(TAG_BITS, iv),
                )
                val cipherText = cipher.doFinal(plain)
                out.use { stream ->
                    stream.write(MAGIC.toByteArray(Charsets.US_ASCII))
                    stream.write(intToBytes(FILE_VERSION))
                    stream.write(salt)
                    stream.write(iv)
                    stream.write(cipherText)
                    stream.flush()
                }
            }.recoverCatching { throw BackupException("备份写入失败：${it.message}", it) }
        }

    /** 还原：读取-解密-解析-校验全部通过后才写入；失败时现有数据保持原样。 */
    suspend fun restore(context: Context, password: CharArray, input: InputStream): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val bytes = input.use { it.readBytes() }
                val headerSize = MAGIC.length + 4 + SALT_SIZE + IV_SIZE
                if (bytes.size <= headerSize) throw BackupException("备份文件不完整")
                if (String(bytes, 0, MAGIC.length, Charsets.US_ASCII) != MAGIC) {
                    throw BackupException("不是云莓氛围的备份文件")
                }
                val fileVersion = bytesToInt(bytes, MAGIC.length)
                if (fileVersion > FILE_VERSION) throw BackupException("备份文件版本过高，请先升级 App")
                val salt = bytes.copyOfRange(MAGIC.length + 4, MAGIC.length + 4 + SALT_SIZE)
                val iv = bytes.copyOfRange(MAGIC.length + 4 + SALT_SIZE, headerSize)
                val cipherText = bytes.copyOfRange(headerSize, bytes.size)

                val plain = try {
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(
                        Cipher.DECRYPT_MODE,
                        deriveKey(password, salt),
                        GCMParameterSpec(TAG_BITS, iv),
                    )
                    cipher.doFinal(cipherText)
                } catch (e: AEADBadTagException) {
                    throw BackupException("口令错误，或备份文件已损坏", e)
                } catch (e: Exception) {
                    throw BackupException("备份解密失败：${e.message}", e)
                }

                val envelope = try {
                    json.decodeFromString(BackupEnvelope.serializer(), String(plain, Charsets.UTF_8))
                } catch (e: Exception) {
                    throw BackupException("备份内容无法解析（可能不是有效的备份文件）", e)
                }

                if (envelope.schemaVersion > BackupEnvelope.CURRENT_SCHEMA_VERSION) {
                    throw BackupException("备份来自更高版本（schemaVersion ${envelope.schemaVersion}），请先升级 App")
                }
                // 校验通过后才写入；缺字段由 data class 默认值补齐。
                apply(context, envelope)
            }.recoverCatching { throw it }
        }

    /** 读取当前数据生成备份内容。 */
    suspend fun snapshot(context: Context): BackupEnvelope = withContext(Dispatchers.IO) {
        val container = YunMeiApp.app.container
        val settings = container.appPreferences.settings.first()
        val prefs = SettingsPrefs.of(context).all
            .filterKeys { it !in EXCLUDED_PREF_KEYS }
            .mapValues { (_, v) -> v?.toString() ?: "" }
        BackupEnvelope(
            schemaVersion = BackupEnvelope.CURRENT_SCHEMA_VERSION,
            exportedAt = System.currentTimeMillis(),
            appVersion = runCatching { getAppVersionForBackup(context) }.getOrDefault(""),
            account = AccountSection(users = container.accountStore.getAll()),
            locks = LockSection(
                locks = container.lockStore.getAll(),
                default = container.lockStore.getDefault(),
            ),
            settings = SettingsSection(
                prefs = prefs,
                dataStore = DataStoreSettings(
                    themeMode = container.appPreferences.themeMode.first().name,
                    quickConnect = settings.quickConnect,
                    autoConnect = settings.autoConnect,
                    autoExit = settings.autoExit,
                    autoCode = settings.autoCode,
                    alwaysCode = settings.alwaysCode,
                    hideSign = settings.hideSign,
                    hideCode = settings.hideCode,
                    attemptUpload = settings.attemptUpload,
                    recordObject = settings.recordObject,
                    signLocationMode = settings.signLocationMode,
                ),
            ),
        )
    }

    /** 写入备份内容（仅在全部校验通过后调用）。 */
    private suspend fun apply(context: Context, envelope: BackupEnvelope) = withContext(Dispatchers.IO) {
        val container = YunMeiApp.app.container
        if (envelope.account.users.isNotEmpty()) {
            container.accountStore.replaceAll(envelope.account.users)
        }
        if (envelope.locks.locks.isNotEmpty() || envelope.locks.default != null) {
            container.lockStore.replaceAll(envelope.locks.locks, envelope.locks.default)
        }
        if (envelope.settings.prefs.isNotEmpty()) {
            val editor = SettingsPrefs.of(context).edit()
            for ((key, value) in envelope.settings.prefs) {
                if (key in EXCLUDED_PREF_KEYS) continue
                editor.putString(key, value)
            }
            editor.apply()
        }
        container.appPreferences.restoreFrom(envelope.settings.dataStore)
    }

    

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun intToBytes(value: Int) = byteArrayOf(
        (value ushr 24).toByte(),
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte(),
    )

    private fun bytesToInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    /** 备份文件名：yunmei-backup-YYYYMMDD.bak（识别一律以文件头 magic 为准，与后缀无关） */
    fun fileName(): String {
        val date = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
        return "yunmei-backup-$date.bak"
    }
}

/** 版本名（避免与 LogEvent 里的同名私有函数冲突）。 */
private fun getAppVersionForBackup(context: Context): String = context.packageManager
    .getPackageInfo(context.packageName, 0)
    .versionName ?: ""