package com.yunmei.client.data.local

import kotlinx.serialization.Serializable

@Serializable
data class StoredUser(
    val username: String,
    val usernameMd5: String,
    val passwordMd5: String,
)

/** 保存账号（密码仅存 MD5，与原项目一致），存储由 [SecureStore] 加密。 */
class AccountStore(private val secureStore: SecureStore) {

    fun getAll(): List<StoredUser> = secureStore.getJson<List<StoredUser>>(KEY_USERS, emptyList())

    fun getByUsernameMd5(md5: String): StoredUser? =
        getAll().firstOrNull { it.usernameMd5 == md5 }

    fun add(user: StoredUser) {
        val users = getAll().filterNot { it.usernameMd5 == user.usernameMd5 }.toMutableList()
        users.add(0, user)
        secureStore.putJson(KEY_USERS, users)
    }

    fun removeByUsernameMd5(md5: String) {
        secureStore.putJson(KEY_USERS, getAll().filterNot { it.usernameMd5 == md5 })
    }

    private companion object {
        const val KEY_USERS = "users"
    }
}
