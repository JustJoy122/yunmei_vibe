package com.yunmei.vibe.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 模块内共享的序列化配置。放在文件级而非 private 成员，便于 internal inline 函数引用。 */
internal val yunmeiJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * 账号与门锁敏感数据存储（AES256-SIV/GCM，密钥来自 Android Keystore）。
 *
 * 安全约定（本次安全审计确认）：
 *  - **不回退明文**：加密存储创建失败时句柄为 null，读写一律降级为"无数据"，绝不写明文；
 *  - **失败不改动用户数据**：读取失败（换机恢复、密钥失效、临时异常等）时只记录错误、
 *    返回默认值，**不删除、不重建、不覆盖**任何已存在的文件——旧数据保留在原地，
 *    以便安全存储恢复可用后仍能读回；
 *  - 失败状态通过 [isAvailable] / [lastError] 暴露，界面可据此提示
 *    "安全存储暂时不可用"，而不是表现为"账号/门锁凭空消失"；
 *  - 进程内单例（[get]），全项目只有一个实例。
 */
class SecureStore(context: Context) {

    private val appContext: Context = context.applicationContext

    @Volatile
    private var prefs: SharedPreferences? = createEncrypted(appContext)

    @Volatile
    private var error: Throwable? = null

    /** 安全存储当前是否可用；不可用时调用方只会读到空数据（旧数据仍在磁盘上未被改动）。 */
    val isAvailable: Boolean get() = prefs != null

    /**
     * 若此前创建失败，这里再尝试一次（Keystore 短暂不可用后恢复的场景），
     * 使界面上的"安全存储暂时不可用"提示能够自动消失。失败不改动任何数据。
     */
    fun retryIfUnavailable() {
        if (prefs == null) {
            prefs = createEncrypted(appContext)
        }
    }

    /** 最近一次失败原因，供界面/日志提示使用；成功时为 null。 */
    val lastError: Throwable? get() = error

    private fun createEncrypted(ctx: Context): SharedPreferences? = runCatching {
        val masterKey = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            ctx,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse { cause ->
        error = cause
        Log.w(TAG, "安全存储不可用（不回退明文、不改动已有数据）", cause)
        null
    }

    fun getString(key: String, def: String = ""): String {
        val store = prefs ?: return def
        return runCatching { store.getString(key, def) ?: def }.getOrElse { cause ->
            // 保守策略：只报告，不清理、不重建，旧密文保留，等安全存储恢复后仍可读回。
            error = cause
            Log.w(TAG, "安全存储读取失败（保留原有数据，不做任何清理）", cause)
            def
        }
    }

    fun putString(key: String, value: String) {
        val store = prefs ?: return
        runCatching { store.edit().putString(key, value).apply() }.onFailure { cause ->
            error = cause
            Log.w(TAG, "安全存储写入失败（不回退明文）", cause)
        }
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

    companion object {
        private const val FILE_NAME = "yunmei_secure"
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