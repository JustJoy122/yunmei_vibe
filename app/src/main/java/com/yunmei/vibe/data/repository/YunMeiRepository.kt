package com.yunmei.vibe.data.repository

import android.content.Context
import com.yunmei.vibe.R
import com.yunmei.vibe.data.local.StoredUser
import com.yunmei.vibe.data.model.Lock
import com.yunmei.vibe.data.model.LoginUser
import com.yunmei.vibe.data.model.SchoolEntry
import com.yunmei.vibe.data.network.YunMeiApiClient
import com.yunmei.vibe.data.security.Md5
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
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

    /**
     * 登录拿到的基础服务 token。
     *
     * [applySchoolAuth] 会把 client.token 换成学校 token，而 locktype/all 走基础服务、
     * 必须用登录 token，所以这里单独留一份。
     */
    private var baseToken: String? = null

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
        baseToken = safeUser.token
        // 安全：不打印 token / userId（会被"发送日志"整包带出）
        safeUser
    }

    suspend fun fetchSchools(): List<SchoolEntry> = mutex.withLock {
        val userId = requireUserId()
        val schools = client.api(YunMeiApiClient.BASE_URL).getSchools(userId)
        // 安全：只打印学校数量与编号，不打印 serverUrl/token
        android.util.Log.d("YunMei", "schools=" + schools.joinToString { s -> s.schoolNo ?: "" })
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
        // 型号能力表在基础服务上；取不到只影响 aesSupport（保持 null → 按普通协议处理）。
        val capabilities = fetchLockTypeCapabilities()
        extractArray(element).mapNotNull { it.toLock(school, capabilities) }
    }

    /**
     * 拉取型号能力表（基础服务 POST locktype/all）。
     *
     * [applySchoolAuth] 已把 client.token 换成学校 token，而这个请求要用登录 token，
     * 所以临时切回 baseToken，结束后恢复，避免影响调用方后续的学校请求。
     * 返回 id → aesSupport；整体失败时返回空表，门锁 aesSupport 保持 null。
     */
    private suspend fun fetchLockTypeCapabilities(): Map<Int, Boolean?> {
        val loginToken = baseToken ?: return emptyMap()
        val schoolToken = client.token
        return try {
            client.token = loginToken
            val element = client.api(YunMeiApiClient.BASE_URL).getLockTypes()
            extractArray(element).mapNotNull { row ->
                val id = row["id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                if (id == null) null else id to row.aesSupportOf()
            }.toMap()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // 型号能力表属于增强信息：失败不阻断门锁列表，按普通协议继续。
            android.util.Log.d("YunMei", "locktype/all failed: " + (error.message ?: ""))
            emptyMap()
        } finally {
            client.token = schoolToken
        }
    }

    /**
     * 解析型号表里的 aesSupport。
     *
     * 官方 4.2.2 是用「值 == 1」判断是否 AES，所以字段显式为 JSON null 时等价于 false；
     * 字段缺失或值无法识别返回 null（未知），开锁时同样按普通协议处理。
     */
    private fun JsonObject.aesSupportOf(): Boolean? {
        if (!containsKey("aesSupport")) return null
        val primitive = this["aesSupport"]?.jsonPrimitive ?: return null
        if (primitive is JsonNull) return false
        primitive.booleanOrNull?.let { return it }
        return when (primitive.contentOrNull?.trim()?.lowercase()) {
            "1", "true" -> true
            "0", "false" -> false
            else -> null
        }
    }

    /** 兼容旧/新格式：裸数组，或包装在 data/list/result 下的数组。 */
    private fun extractArray(element: JsonElement): List<JsonObject> {
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

    private fun JsonObject.toLock(school: SchoolEntry, capabilities: Map<Int, Boolean?>): Lock? {
        fun str(vararg keys: String): String? =
            keys.firstNotNullOfOrNull { key -> this[key]?.jsonPrimitive?.contentOrNull }

        val lockNo = str("lockNo") ?: return null
        val writeUuid = str("lockCharacterUuid", "characteristicUuid") ?: return null
        val serviceUuid = str("lockServiceUuid", "serviceUuid") ?: return null
        // 型号 id 缺失时保持 null（型号未知）；能力表里没有这个 id 同样是 null。
        val typeId = str("lockTypeId")?.toIntOrNull()
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
            lockTypeId = typeId,
            aesSupport = typeId?.let { capabilities[it] },
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

    /** 用已保存账号重新登录后获取开门密码（供主界面长期使用）。 */
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

    /**
     * 开门前刷新单把门锁的信息：secret（服务端会轮换）与型号能力（lockTypeId / aesSupport）。
     *
     * 本地列表只在添加门锁时写入、同名门锁不会覆盖，所以存下来的 secret 与型号能力都可能还是
     * 首次添加时的旧快照；开门前重新拉一次 getuserlock，把服务端的最新值合并进传入的门锁，
     * 由调用方写回 [com.yunmei.vibe.data.local.LockStore] 并用于本次开门。
     *
     * @param account 当前没有登录会话时用于重新登录的已保存账号；null 表示只复用现有会话。
     * @return 合并后的门锁；未登录、无网络、接口失败或服务端返回的值与本地一致时返回 null，
     *         调用方沿用本地门锁，保证离线也能开门。
     */
    suspend fun refreshLock(lock: Lock, account: StoredUser? = null): Lock? = runCatching {
        if (client.token == null) {
            account ?: return@runCatching null
            login(account.username, account.passwordMd5)
        }
        val school = findSchool(lock.schoolNo)
        val latest = fetchLocks(school).firstOrNull { candidate ->
            (lock.lockNo.isNotBlank() && candidate.lockNo == lock.lockNo) || candidate.label == lock.label
        } ?: return@runCatching null
        val merged = lock.copy(
            secret = latest.secret.ifBlank { lock.secret },
            lockTypeId = latest.lockTypeId ?: lock.lockTypeId,
            aesSupport = latest.aesSupport ?: lock.aesSupport,
        )
        merged.takeIf { it != lock }
    }.getOrElse { error ->
        // 协程取消（含调用方 withTimeout 超时）必须继续向上抛，不能被兜底吞掉；
        // 其余异常按「刷新失败」处理，交给调用方沿用本地门锁。
        if (error is CancellationException) throw error
        null
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
