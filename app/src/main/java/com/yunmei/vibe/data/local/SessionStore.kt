package com.yunmei.vibe.data.local

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

    /** 退出登录 / 注销账号：账号级与学校级会话一并作废。 */
    fun clearAll() {
        clearSession()
        clearSchools()
    }

    private companion object {
        const val KEY_SESSION = "account_session"
        const val KEY_SCHOOLS = "school_sessions"
        val EMPTY_SESSION = StoredSession()
    }
}
