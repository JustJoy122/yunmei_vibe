package com.yunmei.vibe.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 模块内共享的序列化配置。放在文件级而非 private 成员，便于 internal inline 函数引用。 */
internal val yunmeiJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * 账号与门锁敏感数据存储（Tink AEAD，密钥来自 Android Keystore）。
 *
 * 存储布局（文件 `yunmei_secure_v2`）：
 *  - 键名保持明文（只是 `users` / `locks` 这类结构名，不含隐私）；
 *  - 值 = `Base64(AEAD.encrypt(明文, associatedData = 键名))`；
 *  - 键名同时作为 AEAD 的 associatedData，密文被挪到别的键下会解密失败（防 confused deputy）。
 *
 * 密钥管理：Tink `AndroidKeysetManager`（keyset 存在 `yunmei_secure_keyset`，主密钥在
 * Android Keystore 的 `yunmei_secure_master_key` 别名下）。主密钥不随备份迁移，因此
 * `yunmei_secure*` 系列文件在备份规则里被显式排除。
 *
 * 安全约定：
 *  - **不回退明文**：加密不可用时句柄为 null，读写一律降级为"无数据"，绝不写明文；
 *  - **失败不改动用户数据**：读取/解密失败只记录错误、返回默认值，**不删除、不重建、不覆盖**
 *    任何已存在的文件；
 *  - 失败状态通过 [isAvailable] / [lastError] 暴露，界面据此提示"安全存储暂时不可用"，
 *    而不是表现为"账号/门锁凭空消失"；
 *  - 进程内单例（[get]），全项目只有一个实例。
 *
 * 历史说明：0.4.9 及更早版本用 `EncryptedSharedPreferences` 写 `yunmei_secure`。该实现已随
 * `androidx.security:security-crypto` 一起移除；仍在旧格式上的数据不会被自动迁移（旧文件保持
 * 原样，见 [warnIfLegacyFilePresent]），需要迁移的话用上一个版本启动一次即可。
 */
class SecureStore(context: Context) {

    private val appContext: Context = context.applicationContext

    /** 容器（值已加密）。 */
    @Volatile
    private var prefs: SharedPreferences? = null

    /** Tink AEAD 原语；为空表示加密不可用。 */
    @Volatile
    private var aead: Aead? = null

    @Volatile
    private var error: Throwable? = null

    init {
        open()
    }

    /** 安全存储当前是否可用；不可用时调用方只会读到空数据（旧数据仍在磁盘上未被改动）。 */
    val isAvailable: Boolean get() = prefs != null && aead != null

    /**
     * 若此前初始化失败，这里再尝试一次（Keystore 短暂不可用后恢复的场景），
     * 使界面上的"安全存储暂时不可用"提示能够自动消失。失败不改动任何数据。
     */
    fun retryIfUnavailable() {
        if (!isAvailable) {
            open()
        }
    }

    /** 最近一次失败原因，供界面/日志提示使用；成功时为 null。 */
    val lastError: Throwable? get() = error

    private fun open() {
        runCatching {
            AeadConfig.register()
            val keysetHandle = AndroidKeysetManager.Builder()
                .withSharedPref(appContext, KEYSET_NAME, KEYSET_PREFS_NAME)
                .withKeyTemplate(KeyTemplates.get(AEAD_KEY_TEMPLATE))
                .withMasterKeyUri(MASTER_KEY_URI)
                .build()
                .keysetHandle
            // 1.24 起 `getPrimitive(Class)` 被标记 deprecated，改用显式 Configuration 的过载。
            val cipher = keysetHandle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
            val store = appContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            aead = cipher
            prefs = store
            dropObsoleteKeys(store)
            warnIfLegacyFilePresent()
        }.onFailure { cause ->
            error = cause
            Log.w(TAG, "安全存储不可用（不回退明文、不改动已有数据）", cause)
        }
    }

    /** 0.4.9 时代的分键档案索引：现在 `keys()` 直接枚举真实键，这份索引不再需要。 */
    private fun dropObsoleteKeys(store: SharedPreferences) {
        if (store.contains(KEY_OBSOLETE_PROFILE_INDEX)) {
            runCatching { store.edit { remove(KEY_OBSOLETE_PROFILE_INDEX) } }
        }
    }

    /** 旧格式文件还在时提示一句：数据不会被自动迁移（文件本身不做任何改动）。 */
    private fun warnIfLegacyFilePresent() {
        val exists = runCatching {
            java.io.File(appContext.dataDir, "shared_prefs/$FILE_NAME_LEGACY.xml").exists()
        }.getOrDefault(false)
        if (exists) {
            Log.w(
                TAG,
                "检测到旧版加密库 $FILE_NAME_LEGACY.xml：当前版本不再读取该格式，" +
                    "其中的账号/门锁不会自动迁移（旧文件保持原样）",
            )
        }
    }

    fun getString(key: String, def: String = ""): String {
        val store = prefs ?: return def
        val cipher = aead ?: return def
        val stored = runCatching { store.getString(key, null) }.getOrElse { cause ->
            error = cause
            Log.w(TAG, "安全存储读取失败（保留原有数据，不做任何清理）", cause)
            return def
        } ?: return def
        return runCatching { decrypt(cipher, key, stored) }.getOrElse { cause ->
            // 保守策略：只报告，不清理、不重建，旧密文保留，等密钥恢复后仍可读回。
            error = cause
            Log.w(TAG, "安全存储解密失败（保留原有数据，不做任何清理）", cause)
            def
        }
    }

    fun putString(key: String, value: String) {
        val store = prefs ?: return
        val cipher = aead ?: return
        runCatching {
            val encrypted = encrypt(cipher, key, value)
            store.edit { putString(key, encrypted) }
        }.onFailure { cause ->
            error = cause
            Log.w(TAG, "安全存储写入失败（不回退明文）", cause)
        }
    }

    /** 删除单个键。 */
    fun remove(key: String) {
        val store = prefs ?: return
        runCatching { store.edit { remove(key) } }
    }

    /**
     * 列出当前存储中的业务键。
     *
     * [SessionStore] 用它枚举分键档案，不必再维护 `account_profile_index` 这类手工索引。
     */
    fun keys(): Set<String> {
        val store = prefs ?: return emptySet()
        return runCatching {
            store.all.keys.toSet()
        }.getOrDefault(emptySet())
    }

    /**
     * 读取 JSON 存储。函数为 internal（本模块内使用），因此可以引用文件级 [yunmeiJson]。
     */
    internal inline fun <reified T> getJson(key: String, default: T): T {
        val raw = getString(key)
        if (raw.isBlank()) return default
        return runCatching { yunmeiJson.decodeFromString<T>(raw) }.getOrDefault(default)
    }

    internal inline fun <reified T> putJson(key: String, value: T) {
        putString(key, yunmeiJson.encodeToString(value))
    }

    private fun encrypt(cipher: Aead, key: String, value: String): String {
        val cipherText = cipher.encrypt(
            value.toByteArray(Charsets.UTF_8),
            key.toByteArray(Charsets.UTF_8),
        )
        return Base64.encodeToString(cipherText, Base64.NO_WRAP)
    }

    private fun decrypt(cipher: Aead, key: String, encoded: String): String {
        val cipherText = Base64.decode(encoded, Base64.NO_WRAP)
        val plain = cipher.decrypt(cipherText, key.toByteArray(Charsets.UTF_8))
        return String(plain, Charsets.UTF_8)
    }

    companion object {
        /** 值用 Tink AEAD 加密后的容器。 */
        private const val FILE_NAME = "yunmei_secure_v2"

        /** Tink keyset 与它的容器。 */
        private const val KEYSET_NAME = "yunmei_secure_keyset"
        private const val KEYSET_PREFS_NAME = "yunmei_secure_keyset"

        /** 主密钥放在 Android Keystore 里，别名固定，不随备份迁移。 */
        private const val MASTER_KEY_URI = "android-keystore://yunmei_secure_master_key"

        private const val AEAD_KEY_TEMPLATE = "AES256_GCM"

        /** 0.4.9 时代的旧格式容器名，仅用于检测并提示，不再读取。 */
        private const val FILE_NAME_LEGACY = "yunmei_secure"

        /** 已被 `keys()` 取代的旧索引键，启动时顺手删掉。 */
        private const val KEY_OBSOLETE_PROFILE_INDEX = "account_profile_index"

        private const val TAG = "SecureStore"

        @Volatile
        private var instance: SecureStore? = null

        /** 进程内单例：全项目只有一个实例，避免重复创建加密存储。 */
        fun get(context: Context): SecureStore =
            instance ?: synchronized(this) {
                instance ?: SecureStore(context.applicationContext).also { instance = it }
            }.also { it.retryIfUnavailable() }
    }
}
