package com.yunmei.vibe.data.backup

import com.yunmei.vibe.data.local.StoredUser
import com.yunmei.vibe.data.model.Lock
import kotlinx.serialization.Serializable

/**
 * 备份文件顶层结构。
 *
 * 版本约定（本次安全/兼容性设计）：
 *  - [schemaVersion] 为顶层版本；各子模块另带自己的 [AccountSection.version] 等；
 *  - 解析一律使用 ignoreUnknownKeys = true，因此**新增字段必须给默认值**；
 *  - 已发布字段名不再修改，语义变化用新增字段表达。
 */
@Serializable
data class BackupEnvelope(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val exportedAt: Long = 0L,
    val appVersion: String = "",
    val account: AccountSection = AccountSection(),
    val locks: LockSection = LockSection(),
    val settings: SettingsSection = SettingsSection(),
) {
    companion object {
        /** 当前 App 支持的备份结构版本。 */
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

@Serializable
data class AccountSection(
    val version: Int = 1,
    val users: List<StoredUser> = emptyList(),
)

@Serializable
data class LockSection(
    val version: Int = 1,
    val locks: List<Lock> = emptyList(),
    val default: Lock? = null,
)

@Serializable
data class SettingsSection(
    val version: Int = 1,
    /** 原始 `settings` SharedPreferences 的键值（已剔除快捷方式令牌等设备绑定项）。 */
    val prefs: Map<String, String> = emptyMap(),
    /** DataStore 中的应用设置（主题、开关等）。 */
    val dataStore: DataStoreSettings = DataStoreSettings(),
)

@Serializable
data class DataStoreSettings(
    val themeMode: String = "MATERIAL3",
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