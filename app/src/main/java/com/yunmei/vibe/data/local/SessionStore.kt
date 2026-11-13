package com.yunmei.vibe.data.local

import com.yunmei.vibe.data.model.UserProfile
import kotlinx.serialization.Serializable

/**
 * 账号级会话快照。
 *
 * 官方把 token 存在本地（`USERS_KEY` 里的 `token`），进程被杀后仍能直接继续用；
 * 本项目此前把 token 放在内存里，冷启动必然回到「未登录」。这里只持久化服务端下发的
 * [token] 与 [userId]，**不保存密码**——密码 MD5 仍在 [AccountStore] 里，续期时从那里取。
 */
@Serializable
data class StoredSession(
    val usernameMd5: String = "",
    val token: String = "",
    val userId: String = "",
    val savedAt: Long = 0L,
    /** 登录时服务端返回的姓名与手机号：冷启动或断网时个人中心靠它先显示。 */
    val realName: String = "",
    val userTel: String = "",
)

/**
 * 学校级会话缓存。
 *
 * 学校域接口（取码、开门记录、群组成员）用的是学校 token + `adminSchoolNo`，
 * 它与登录 token 是两套凭据；缓存下来可以在不重新拉 `userschool/getbyuserid` 的情况下复用。
 */
@Serializable
data class SchoolSession(
    val schoolNo: String = "",
    val serverUrl: String = "",
    val schoolToken: String = "",
)

/**
 * 会话持久化（与账号、门锁共用 [SecureStore] 的加密存储）。
 *
 * 读写都不做明文回退：安全存储不可用时按「没有会话」处理，调用方走正常登录流程。
 */
class SessionStore(private val secureStore: SecureStore) {

    /** 读取账号会话；token 或 userId 缺失视为没有会话。 */
    fun loadSession(): StoredSession? =
        secureStore.getJson(KEY_SESSION, EMPTY_SESSION).takeIf {
            it.token.isNotBlank() && it.userId.isNotBlank()
        }

    fun saveSession(session: StoredSession) {
        secureStore.putJson(KEY_SESSION, session)
    }

    fun clearSession() {
        secureStore.putJson(KEY_SESSION, EMPTY_SESSION)
    }

    fun loadSchools(): List<SchoolSession> = secureStore.getJson(KEY_SCHOOLS, emptyList())

    /** 按 schoolNo 覆盖写入学校会话，避免同一学校堆积多条。 */
    fun saveSchool(session: SchoolSession) {
        if (session.schoolNo.isBlank() || session.schoolToken.isBlank()) return
        val schools = loadSchools().filterNot { it.schoolNo == session.schoolNo } + session
        secureStore.putJson(KEY_SCHOOLS, schools)
    }

    fun clearSchools() {
        secureStore.putJson(KEY_SCHOOLS, emptyList<SchoolSession>())
    }

    /**
     * 读取某个账号的档案快照。
     *
     * 按 [usernameMd5] 分键存储：多个账号共用同一台设备时，{@code account_profile}
     * 这种单键缓存会在切号后先显示上一个账号的姓名/学院/宿舍（缓存优先策略会把这个
     * 串号问题暴露出来），所以缓存必须带账号归属。[usernameMd5] 为空时视为没有归属，
     * 直接当缓存未命中。
     *
     * 旧版本只有一个全局键 [KEY_PROFILE_LEGACY]；这里做一次懒迁移：首次命中就把它
     * 写给当前账号，并立即清空旧键，避免被第二个账号再次读到。
     */
    fun loadProfile(usernameMd5: String): UserProfile? {
        if (usernameMd5.isBlank()) return null
        val key = profileKey(usernameMd5)
        secureStore.getJson(key, EMPTY_PROFILE).takeIf { it.isFilled() }?.let { return it }
        val legacy = secureStore.getJson(KEY_PROFILE_LEGACY, EMPTY_PROFILE).takeIf { it.isFilled() }
            ?: return null
        secureStore.putJson(key, legacy)
        secureStore.putJson(KEY_PROFILE_LEGACY, EMPTY_PROFILE)
        rememberProfileKey(usernameMd5)
        return legacy
    }

    fun saveProfile(usernameMd5: String, profile: UserProfile) {
        if (usernameMd5.isBlank()) return
        secureStore.putJson(profileKey(usernameMd5), profile)
        rememberProfileKey(usernameMd5)
    }

    fun clearProfile(usernameMd5: String) {
        if (usernameMd5.isBlank()) return
        secureStore.putJson(profileKey(usernameMd5), EMPTY_PROFILE)
        secureStore.putJson(KEY_PROFILE_INDEX, loadProfileKeys().filterNot { it == usernameMd5 })
    }

    /** 退出登录 / 注销账号：账号级、学校级会话与全部账号的档案快照一并作废。 */
    fun clearAll() {
        // SecureStore 没有「列键」能力，只能靠索引记住写过哪些账号。
        loadProfileKeys().forEach { md5 -> secureStore.putJson(profileKey(md5), EMPTY_PROFILE) }
        secureStore.putJson(KEY_PROFILE_INDEX, emptyList<String>())
        secureStore.putJson(KEY_PROFILE_LEGACY, EMPTY_PROFILE)
        clearSession()
        clearSchools()
    }

    /** 分键档案的键名，保证每个账号各占一份。 */
    private fun profileKey(usernameMd5: String): String = PROFILE_KEY_PREFIX + usernameMd5

    private fun loadProfileKeys(): List<String> = secureStore.getJson(KEY_PROFILE_INDEX, emptyList())

    private fun rememberProfileKey(usernameMd5: String) {
        val keys = loadProfileKeys()
        if (usernameMd5 in keys) return
        secureStore.putJson(KEY_PROFILE_INDEX, keys + usernameMd5)
    }

    private fun UserProfile.isFilled(): Boolean =
        userId.isNotBlank() || realName.isNotBlank() || stuNo.isNotBlank()

    private companion object {
        const val KEY_SESSION = "account_session"
        const val KEY_SCHOOLS = "school_sessions"

        /** 分键前缀：`profile_<usernameMd5>`。 */
        const val PROFILE_KEY_PREFIX = "profile_"

        /** 写过档案的账号 md5 列表，退出登录时据此逐个清空。 */
        const val KEY_PROFILE_INDEX = "account_profile_index"

        /** 旧版本的单一全局档案键，仅用于一次性迁移。 */
        const val KEY_PROFILE_LEGACY = "account_profile"
        val EMPTY_SESSION = StoredSession()
        val EMPTY_PROFILE = UserProfile()
    }
}
