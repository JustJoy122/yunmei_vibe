package com.yunmei.vibe.data.local

import android.content.Context
import android.content.SharedPreferences
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
 * 账号与门锁敏感数据存储。
 * 优先使用 EncryptedSharedPreferences；极少数设备创建失败时回退到普通 SharedPreferences，
 * 保证功能可用，但会打印日志提示降级。
 */
class SecureStore(context: Context) {

    private val prefs: SharedPreferences? = runCatching {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse { error ->
        android.util.Log.w("SecureStore", "EncryptedSharedPreferences 不可用，回退为明文存储", error)
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    }

    fun getString(key: String, def: String = ""): String = prefs?.getString(key, def) ?: def

    fun putString(key: String, value: String) {
        prefs?.edit()?.putString(key, value)?.apply()
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

    private companion object {
        const val FILE_NAME = "yunmei_secure"
    }
}
