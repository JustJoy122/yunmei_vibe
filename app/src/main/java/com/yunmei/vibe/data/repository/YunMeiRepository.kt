package com.yunmei.vibe.data.repository

import android.content.Context
import com.yunmei.vibe.R
import com.yunmei.vibe.data.local.StoredUser
import com.yunmei.vibe.data.model.Lock
import com.yunmei.vibe.data.model.LoginUser
import com.yunmei.vibe.data.model.SchoolEntry
import com.yunmei.vibe.data.network.YunMeiApiClient
import com.yunmei.vibe.data.security.Md5
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** 业务异常基类。 */
open class YunMeiException(message: String) : Exception(message)

/**
 * 服务端明确拒绝登录（账号或密码错误等业务性鉴权失败）。
 *
 * 与网络异常、HTTP 500、JSON 解析失败区分开，登录页据此给出「账号或密码错误」的中文友好提示，
 * 其余异常则保留原始信息并加中文前缀。继承 [YunMeiException]，既有 `catch (YunMeiException)` 不受影响。
 */
class YunMeiAuthException(message: String) : YunMeiException(message)

/**
 * 云莓智能业务仓库。
 * 网络调用与 token 切换用 [Mutex] 串行化，避免并发时 header 串号。
 */
class YunMeiRepository(context: Context, private val client: YunMeiApiClient) {

    /** 应用 Context，仅用于读取界面文案资源（数据层不再硬编码中文）。 */
    private val appContext = context.applicationContext

    private val mutex = Mutex()

    private var username: String? = null
    private var usernameMd5: String? = null

    suspend fun login(username: String, passwordMd5: String): LoginUser = mutex.withLock {
        val response = client.api(YunMeiApiClient.BASE_URL).login(username, passwordMd5)
        val user = response.o
        // 服务端 success=false：账号/密码不被接受，属业务性鉴权失败。
        if (!response.success) {
            throw YunMeiAuthException(response.msg ?: appContext.getString(R.string.login_error_generic))
        }
        // 以下属异常响应体（服务端 bug 或接口变更），归为预期外错误。
        val safeUser = user ?: throw YunMeiException(appContext.getString(R.string.login_error_response_no_user))
        if (safeUser.token.isNullOrBlank() || safeUser.userId.isNullOrBlank()) {
            throw YunMeiException(appContext.getString(R.string.login_error_response_no_token))
        }
        this.username = username
        this.usernameMd5 = Md5.hex(username)
        client.token = safeUser.token
        client.userId = safeUser.userId
        android.util.Log.d("YunMei", "login token=${safeUser.token} userId=${safeUser.userId}")
        safeUser
    }

    suspend fun fetchSchools(): List<SchoolEntry> = mutex.withLock {
        val userId = requireUserId()
        val schools = client.api(YunMeiApiClient.BASE_URL).getSchools(userId)
        android.util.Log.d(
            "YunMei",
            "schools=" + schools.joinToString { s -> "${s.schoolNo}/${s.school?.schoolName}/${s.school?.serverUrl}/${s.token}" }
        )
        schools
    }

    suspend fun fetchLocks(school: SchoolEntry): List<Lock> = mutex.withLock {
        val userId = requireUserId()
        val serverUrl = school.school?.serverUrl?.takeIf { it.isNotBlank() }
            ?: throw YunMeiException(appContext.getString(R.string.school_server_missing))
        applySchoolAuth(school, userId)
        android.util.Log.d(
            "YunMei",
            "getLocks url=$serverUrl schoolNo=${school.schoolNo} userId=$userId token=${client.token?.take(220)}"
        )
        val element = client.api(serverUrl).getLocks(school.schoolNo.orEmpty(), userId)
        android.util.Log.d("YunMei", "getLocks raw=$element")
        extractLocksArray(element).mapNotNull { it.toLock(school) }
    }

    /** 兼容旧/新格式：裸数组，或包装在 data/list/result 下的数组。 */
    private fun extractLocksArray(element: JsonElement): List<JsonObject> {
        if (element is JsonArray) {
            return element.mapNotNull { it as? JsonObject }
        }
        if (element is JsonObject) {
            val keys = listOf("data", "list", "result")
            for (key in keys) {
                val child = element[key]
                if (child is JsonArray) {
                    return child.mapNotNull { it as? JsonObject }
                }
            }
        }
        return emptyList()
    }

    private fun JsonObject.toLock(school: SchoolEntry): Lock? {
        fun str(vararg keys: String): String? =
            keys.firstNotNullOfOrNull { key -> this[key]?.jsonPrimitive?.contentOrNull }

        val lockNo = str("lockNo") ?: return null
        val writeUuid = str("lockCharacterUuid", "characteristicUuid") ?: return null
        val serviceUuid = str("lockServiceUuid", "serviceUuid") ?: return null
        return Lock(
            label = (str("buildName") ?: appContext.getString(R.string.lock_label_unknown)) +
                "-" + (str("dormNo") ?: appContext.getString(R.string.lock_label_unknown)),
            // 原项目在未知真实 MAC 时用 lockNo 顶替，快速连接失败后回退扫描。
            mac = lockNo,
            writeUuid = writeUuid,
            serviceUuid = serviceUuid,
            secret = str("lockSecret", "secret").orEmpty(),
            usernameMd5 = usernameMd5.orEmpty(),
            schoolNo = school.schoolNo.orEmpty(),
            lockNo = lockNo,
        )
    }

    suspend fun sign(school: SchoolEntry, lock: Lock, location: String): String = mutex.withLock {
        val userId = requireUserId()
        val serverUrl = requireServerUrl(school)
        applySchoolAuth(school, userId)
        val response = client.api(serverUrl)
            .sign(school.schoolNo.orEmpty(), lock.lockNo, location)
        if (!response.success) throw YunMeiException(response.msg ?: appContext.getString(R.string.unlock_sign_failed))
        response.msg ?: appContext.getString(R.string.unlock_sign_success)
    }

    suspend fun getLockPassword(school: SchoolEntry, lock: Lock): String = mutex.withLock {
        val userId = requireUserId()
        val serverUrl = requireServerUrl(school)
        applySchoolAuth(school, userId)
        val response = client.api(serverUrl).getLockPassword(lock.lockNo)
        response.lockPwd ?: throw YunMeiException(response.msg ?: appContext.getString(R.string.unlock_code_failed))
    }

    /** 用已保存账号重新登录后获取开锁密码（供主界面长期使用）。 */
    suspend fun getLockPassword(user: StoredUser, lock: Lock): String {
        login(user.username, user.passwordMd5)
        val school = findSchool(lock.schoolNo)
        return getLockPassword(school, lock)
    }

    /** 用已保存账号重新登录后打卡。 */
    suspend fun sign(user: StoredUser, lock: Lock, location: String): String {
        login(user.username, user.passwordMd5)
        val school = findSchool(lock.schoolNo)
        // 打卡接口只依赖 schoolNo + lockNo + location，不依赖门锁列表。
        // 服务器 getuserlock 返回空时，门锁是扫码/手动导入的，不能因为
        // 列表查不到而拒绝打卡（与原版 SignService 行为一致）。
        return sign(school, lock, location)
    }

    private suspend fun findSchool(schoolNo: String): SchoolEntry {
        return fetchSchools().firstOrNull { it.schoolNo == schoolNo }
            ?: throw YunMeiException(appContext.getString(R.string.school_info_missing))
    }

    private fun requireUserId(): String =
        client.userId ?: throw YunMeiException(appContext.getString(R.string.login_error_not_logged_in))

    private fun requireServerUrl(school: SchoolEntry): String =
        school.school?.serverUrl?.takeIf { it.isNotBlank() }
            ?: throw YunMeiException(appContext.getString(R.string.school_server_missing))

    private fun applySchoolAuth(school: SchoolEntry, userId: String) {
        client.token = school.token
        client.userId = userId
    }
}
