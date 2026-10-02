package com.yunmei.vibe.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.yunmei.vibe.data.local.SecureStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

enum class ThemeMode { MATERIAL3, MIUIX }

data class AppSettings(
    val quickConnect: Boolean = true,
    val autoConnect: Boolean = false,
    val autoExit: Boolean = false,
    val autoCode: Boolean = false,
    val alwaysCode: Boolean = false,
    val hideSign: Boolean = false,
    val hideCode: Boolean = false,
    val attemptUpload: Boolean = false,
    val recordObject: String = "",
    val signLocationMode: String = "ask",
)

class AppPreferences(private val context: Context, private val secureStore: SecureStore) {

    val themeMode: Flow<ThemeMode> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_THEME_MODE]
            ?.let { value -> runCatching { ThemeMode.valueOf(value) }.getOrNull() }
            ?: ThemeMode.MATERIAL3
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            quickConnect = prefs[KEY_QUICK_CONNECT] ?: true,
            autoConnect = prefs[KEY_AUTO_CONNECT] ?: false,
            autoExit = prefs[KEY_AUTO_EXIT] ?: false,
            autoCode = prefs[KEY_AUTO_CODE] ?: false,
            alwaysCode = prefs[KEY_ALWAYS_CODE] ?: false,
            hideSign = prefs[KEY_HIDE_SIGN] ?: false,
            hideCode = prefs[KEY_HIDE_CODE] ?: false,
            attemptUpload = prefs[KEY_ATTEMPT_UPLOAD] ?: false,
            recordObject = prefs[KEY_RECORD_OBJECT] ?: "",
            signLocationMode = prefs[KEY_SIGN_LOCATION_MODE] ?: "ask",
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    suspend fun setQuickConnect(value: Boolean) {
        context.settingsDataStore.edit { it[KEY_QUICK_CONNECT] = value }
    }

    suspend fun setAutoConnect(value: Boolean) {
        context.settingsDataStore.edit { it[KEY_AUTO_CONNECT] = value }
    }

    suspend fun setAutoExit(value: Boolean) {
        context.settingsDataStore.edit { it[KEY_AUTO_EXIT] = value }
    }

    suspend fun setAutoCode(value: Boolean) {
        context.settingsDataStore.edit { it[KEY_AUTO_CODE] = value }
    }

    suspend fun setAlwaysCode(value: Boolean) {
        context.settingsDataStore.edit { it[KEY_ALWAYS_CODE] = value }
    }

    suspend fun setHideSign(value: Boolean) {
        context.settingsDataStore.edit { it[KEY_HIDE_SIGN] = value }
    }

    suspend fun setHideCode(value: Boolean) {
        context.settingsDataStore.edit { it[KEY_HIDE_CODE] = value }
    }

    suspend fun setAttemptUpload(value: Boolean) {
        context.settingsDataStore.edit { it[KEY_ATTEMPT_UPLOAD] = value }
    }

    suspend fun setRecordObject(value: String) {
        context.settingsDataStore.edit { it[KEY_RECORD_OBJECT] = value }
    }

    suspend fun setSignLocationMode(value: String) {
        context.settingsDataStore.edit { it[KEY_SIGN_LOCATION_MODE] = value }
    }

    /** 上次打卡位置属个人数据，改存加密存储（旧版本明文值在首次读取时迁移并清除）。 */
    suspend fun setLastLocation(value: String) = withContext(Dispatchers.IO) {
        secureStore.putString(KEY_LAST_LOCATION_SECURE, value)
    }

    suspend fun getLastLocation(): String = withContext(Dispatchers.IO) {
        val encrypted = secureStore.getString(KEY_LAST_LOCATION_SECURE)
        if (encrypted.isNotBlank()) return@withContext encrypted
        // 兼容旧版本：DataStore 里的明文位置迁移一次，然后删除明文
        val legacy = context.settingsDataStore.data.first()[KEY_LAST_LOCATION] ?: ""
        if (legacy.isNotBlank()) {
            secureStore.putString(KEY_LAST_LOCATION_SECURE, legacy)
            context.settingsDataStore.edit { it.remove(KEY_LAST_LOCATION) }
        }
        legacy
    }

    /** 还原：把备份中的应用设置写回（缺字段用默认值补齐）。 */
    suspend fun restoreFrom(backup: com.yunmei.vibe.data.backup.DataStoreSettings) {
        setThemeMode(runCatching { ThemeMode.valueOf(backup.themeMode) }.getOrDefault(ThemeMode.MATERIAL3))
        setQuickConnect(backup.quickConnect)
        setAutoConnect(backup.autoConnect)
        setAutoExit(backup.autoExit)
        setAutoCode(backup.autoCode)
        setAlwaysCode(backup.alwaysCode)
        setHideSign(backup.hideSign)
        setHideCode(backup.hideCode)
        setAttemptUpload(backup.attemptUpload)
        setRecordObject(backup.recordObject)
        setSignLocationMode(backup.signLocationMode)
    }

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_QUICK_CONNECT = booleanPreferencesKey("quick_connect")
        val KEY_AUTO_CONNECT = booleanPreferencesKey("auto_connect")
        val KEY_AUTO_EXIT = booleanPreferencesKey("auto_exit")
        val KEY_AUTO_CODE = booleanPreferencesKey("auto_code")
        val KEY_ALWAYS_CODE = booleanPreferencesKey("always_code")
        val KEY_HIDE_SIGN = booleanPreferencesKey("hide_sign")
        val KEY_HIDE_CODE = booleanPreferencesKey("hide_code")
        val KEY_ATTEMPT_UPLOAD = booleanPreferencesKey("attempt_upload")
        val KEY_RECORD_OBJECT = stringPreferencesKey("record_object")
        val KEY_SIGN_LOCATION_MODE = stringPreferencesKey("sign_location_mode")
        val KEY_LAST_LOCATION = stringPreferencesKey("last_location")

        /** 加密存储中的上次位置键（与旧 DataStore 键同名，便于理解）。 */
        const val KEY_LAST_LOCATION_SECURE = "last_location"
    }
}
