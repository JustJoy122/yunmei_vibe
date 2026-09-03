package com.yunmei.client.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
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

class AppPreferences(private val context: Context) {

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

    suspend fun setLastLocation(value: String) {
        context.settingsDataStore.edit { it[KEY_LAST_LOCATION] = value }
    }

    suspend fun getLastLocation(): String =
        context.settingsDataStore.data.first()[KEY_LAST_LOCATION] ?: ""

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
    }
}
