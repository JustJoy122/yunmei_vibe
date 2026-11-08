package com.yunmei.vibe.data.repository

import android.content.Context
import android.os.Build
import com.yunmei.vibe.BuildConfig
import com.yunmei.vibe.R
import com.yunmei.vibe.data.local.StoredUser
import com.yunmei.vibe.data.model.Lock
import com.yunmei.vibe.data.model.LoginUser
import com.yunmei.vibe.data.model.SchoolEntry
import com.yunmei.vibe.data.model.UnitCodes
import com.yunmei.vibe.data.model.UnlockRecord
import com.yunmei.vibe.data.model.UnlockRecordItem
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
        client.adminSchoolNo = null
        baseToken = safeUser.token
        // 安全：不打印 token / userId（会被"发送日志"整包带出）
        safeUser
    }

    /**
     * 拉取当前账号所属的学校（基础服务 `userschool/getbyuserid`）。
     *
     * 基础服务只认登录 token：调用方可能刚切到学校会话（例如开完门紧接着取学校信息），
     * 若直接用 `client.token` 请求，服务端会回 `{"error":true,"msg":"未登录或登录超时…"}`，
     * 解析数组时再抛异常——这正是开门记录上报长期失败的真正原因。
     * 因此这里固定切回 [baseToken] 再请求，结束后恢复调用方的会话。
     */
    suspend fun fetchSchools(): List<SchoolEntry> = mutex.withLock {
        val userId = requireUserId()
        val loginToken = baseToken
            ?: throw YunMeiException(appContext.getString(R.string.login_error_not_logged_in))
        val schoolToken = client.token
        val schoolNo = client.adminSchoolNo
        try {
            client.token = loginToken
            client.adminSchoolNo = null
            val schools = client.api(YunMeiApiClient.BASE_URL).getSchools(userId)
            // 安全：只打印学校数量与编号，不打印 serverUrl/token
            android.util.Log.i("YunMei", "schools=" + schools.joinToString { s -> s.schoolNo ?: "" })
            schools
        } finally {
            client.token = schoolToken
            client.adminSchoolNo = schoolNo
        }
    }

    suspend fun fetchLocks(school: SchoolEntry): List<Lock> = mutex.withLock {
        val userId = requireUserId()
        val serverUrl = school.school?.serverUrl?.takeIf { it.isNotBlank() }
            ?: throw YunMeiException(appContext.getString(R.string.school_server_missing))
        applySchoolAuth(school, userId)
        android.util.Log.i(
            "YunMei",
            "getLocks url=$serverUrl schoolNo=${school.schoolNo} userId=$userId token=${client.token?.take(24)}…"
        )
        val element = client.api(serverUrl).getLocks(school.schoolNo.orEmpty(), userId)
        android.util.Log.i("YunMei", "getLocks raw=$element")
        // 型号能力表在基础服务上；取不到只影响 aesSupport（保持 null → 按普通协议处理）。
        val capabilities = fetchLockTypeCapabilities()
        extractArray(element).mapNotNull { it.toLock(school, capabilities) }
    }

    /**
     * 拉取型号能力表（基础服务 POST locktype/all）。
     *
     * [applySchoolAuth] 已把 client.token 换成学校 token，而这个请求要用登录 token，
     * 所以临时切回 baseToken，结束后恢复，避免影响调用方后续的学校请求。
     * 返回 id → 型号能力（aesSupport / pwdSupport / pwdLength）；整体失败时返回空表，
     * 门锁的能力字段保持 null（按最保守的老协议处理）。
     */
    private suspend fun fetchLockTypeCapabilities(): Map<Int, LockCapability> {
        val loginToken = baseToken ?: return emptyMap()
        val schoolToken = client.token
        val schoolNo = client.adminSchoolNo
        return try {
            client.token = loginToken
            // 基础服务请求不带学校上下文（官方同样只在非基础服务上带 adminSchoolNo）。
            client.adminSchoolNo = null
            val element = client.api(YunMeiApiClient.BASE_URL).getLockTypes()
            extractArray(element).mapNotNull { row ->
                val id = row["id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                if (id == null) null else id to row.capabilityOf()
            }.toMap()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // 型号能力表属于增强信息：失败不阻断门锁列表，按普通协议继续。
            android.util.Log.i("YunMei", "locktype/all failed: " + (error.message ?: ""))
            emptyMap()
        } finally {
            client.token = schoolToken
            client.adminSchoolNo = schoolNo
        }
    }

    /** 型号能力表的一行：官方 locktype/all 返回的协议能力。 */
    private data class LockCapability(
        val aesSupport: Boolean? = null,
        val pwdSupport: Boolean? = null,
        val pwdLength: Int? = null,
        val typeName: String? = null,
    )

    private fun JsonObject.capabilityOf(): LockCapability = LockCapability(
        aesSupport = flagOf("aesSupport"),
        pwdSupport = flagOf("pwdSupport"),
        pwdLength = this["pwdLength"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
        typeName = this["typeName"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
    )

    private fun JsonObject.aesSupportOf(): Boolean? = flagOf("aesSupport")

    /**
     * 解析型号表里的布尔能力。
     *
     * 官方 4.2.2 是用「值 == 1」判断，所以字段显式为 JSON null 时等价于 false；
     * 字段缺失或值无法识别返回 null（未知）。
     */
    private fun JsonObject.flagOf(key: String): Boolean? {
        if (!containsKey(key)) return null
        val primitive = this[key]?.jsonPrimitive ?: return null
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

    private fun JsonObject.toLock(school: SchoolEntry, capabilities: Map<Int, LockCapability>): Lock? {
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
            // getuserlock 不返回 schoolName，userschool 的 school 子对象也只有一个 serverUrl；
            // 名称用内置的单位对照表按 schoolNo 反查（001 → 中国计量大学），完全不发请求。
            schoolName = str("schoolName")?.takeIf { it.isNotBlank() }
                ?: UnitCodes.name(school.schoolNo.orEmpty())
                ?: school.school?.schoolName?.takeIf { it.isNotBlank() }
                .orEmpty(),
            lockNo = lockNo,
            lockTypeId = typeId,
            aesSupport = typeId?.let { capabilities[it]?.aesSupport },
            pwdSupport = typeId?.let { capabilities[it]?.pwdSupport },
            pwdLength = typeId?.let { capabilities[it]?.pwdLength },
            lockElectricity = str("lockElectricity")?.toIntOrNull(),
            // 官方字段名是 updateTime；lastUpdateTime 作为兼容别名一起认。
            updateTime = str("updateTime", "lastUpdateTime"),
            areaNo = str("areaNo").orEmpty(),
            buildNo = str("buildNo").orEmpty(),
            dormNo = str("dormNo").orEmpty(),
            areaName = str("areaName").orEmpty(),
            buildName = str("buildName").orEmpty(),
            dormName = str("dormName").orEmpty(),
            lockTypeName = typeId?.let { capabilities[it]?.typeName }.orEmpty(),
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
        val element = client.api(serverUrl).getLockPassword(lock.lockNo)
        // 官方直接读 body.lockPwd；个别学校的网关会再包一层 data，两种形状都兼容。
        val body = element as? JsonObject
        val payload = (body?.get("data") as? JsonObject) ?: body
        val code = payload?.jsonText("lockPwd", "pwd")
        if (!code.isNullOrBlank()) return@withLock code
        val message = body?.jsonText("msg") ?: payload?.jsonText("msg")
        throw YunMeiException(message ?: appContext.getString(R.string.unlock_code_failed))
    }

    /**
     * 拉取门锁所在宿舍的成员列表（官方 `dormuser/getbyloginuser`）。
     *
     * 官方「我的群组」是按宿舍（校区/楼栋/房间）取人、不是按 lockNo 取，
     * 这四个值都来自本地保存的 [Lock]，此前为上报开门记录已经存了下来。
     * 返回原始 JSON：服务端是否再包一层 `data` 尚未实测，解析交给调用方。
     */
    suspend fun getDormMembers(lock: Lock): JsonElement {
        // findSchool 内部还会取一次 mutex（fetchSchools），必须放在 withLock 之外，
        // 否则同一协程重入不可重入的 Mutex 会永久挂起——表现为点了没有任何反应。
        val school = findSchool(lock.schoolNo)
        val userId = requireUserId()
        val serverUrl = requireServerUrl(school)
        return mutex.withLock {
            applySchoolAuth(school, userId)
            client.api(serverUrl).getDormUsers(
                schoolNo = lock.schoolNo,
                areaNo = lock.areaNo,
                buildNo = lock.buildNo,
                dormNo = lock.dormNo,
            )
        }
    }

    /**
     * 拉取当前账号在本校的开门记录（官方 `lockrecord/getrecordbyschool`）。
     *
     * 接口只接受 `schoolNo` + `pageSize`：服务端按登录账号聚合该账号名下所有门锁的记录，
     * 不接受按门锁/按人筛选，所以这里只借用 [lock] 定位学校，不参与过滤。
     * 返回体既可能是裸数组，也可能被包在 `data` 或 `o` 里，三种形状都兼容。
     */
    suspend fun getUnlockRecords(lock: Lock): List<UnlockRecordItem> {
        // findSchool 内部会再取一次 mutex，必须放在 withLock 之外（同 getDormMembers 的教训）。
        val school = findSchool(lock.schoolNo)
        val userId = requireUserId()
        val serverUrl = requireServerUrl(school)
        val element = mutex.withLock {
            applySchoolAuth(school, userId)
            client.api(serverUrl).getUnlockRecords(schoolNo = lock.schoolNo)
        }
        val array = when (element) {
            is JsonArray -> element
            is JsonObject -> (element["data"] as? JsonArray)
                ?: (element["o"] as? JsonArray)
                ?: JsonArray(emptyList())
            else -> JsonArray(emptyList())
        }
        return array.mapNotNull { item -> item as? JsonObject }.map { item ->
            UnlockRecordItem(
                buildName = item.jsonText("buildName").orEmpty(),
                dormName = item.jsonText("dormName").orEmpty(),
                realName = item.jsonText("realName").orEmpty(),
                lockTime = item.jsonText("lockTime").orEmpty(),
            )
        }
    }

    /** 依次尝试多个字段名，取到第一个非空字符串。 */    private fun JsonObject.jsonText(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> this[key]?.jsonPrimitive?.contentOrNull }

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
            pwdSupport = latest.pwdSupport ?: lock.pwdSupport,
            pwdLength = latest.pwdLength ?: lock.pwdLength,
            lockElectricity = latest.lockElectricity ?: lock.lockElectricity,
            updateTime = latest.updateTime ?: lock.updateTime,
            areaNo = latest.areaNo.ifBlank { lock.areaNo },
            buildNo = latest.buildNo.ifBlank { lock.buildNo },
            dormNo = latest.dormNo.ifBlank { lock.dormNo },
        )
        merged.takeIf { it != lock }
    }.getOrElse { error ->
        // 协程取消（含调用方 withTimeout 超时）必须继续向上抛，不能被兜底吞掉；
        // 其余异常按「刷新失败」处理，交给调用方沿用本地门锁。
        if (error is CancellationException) throw error
        null
    }
    /**
     * 上报一次开门记录（基础服务 `lockrecord/savelockrecord`）。
     *
     * 官方前端把所有接口分成两类：命中 `BaseServerUrl` 前缀的走学校域并带学校 token，
     * 其余一律走基础服务、用登录 token，学校只体现在 `schoolNo` 参数里。开门记录属于后者——
     * 实测（设备上导出的 BaseServerUrl 共 25 条）里没有 lockrecord，所以早期实现用学校会话
     * 请求时，服务端稳定回 `{"error":true,"msg":"未登录或登录超时,请重新登录！"}`。
     */
    suspend fun saveUnlockRecord(record: UnlockRecord, deviceId: String): Boolean {
        return runCatching {
            // findSchool 内部会再抢一次 mutex（fetchSchools），必须放在 withLock 之外。
            val school = findSchool(record.schoolNo)
            val userId = requireUserId()
            mutex.withLock {
                // 记录接口在学校域上，官方对学校域请求统一用学校 token + adminSchoolNo
                // （实测：学校服务 + 学校 token 返回 {"success":true,"msg":"保存成功！","o":{"el":38,"sign":-1}}，
                //  基础服务上是 404，换成登录 token 则报「用户验证错误」）。
                applySchoolAuth(school, userId)
                client.api(requireServerUrl(school)).saveLockRecord(
                    isDel = 0,
                    schoolNo = record.schoolNo,
                    areaNo = record.areaNo,
                    buildNo = record.buildNo,
                    dormNo = record.dormNo,
                    lockNo = record.lockNo,
                    // 官方的 userName 是用户 id（getUser().id），不是登录名；服务端按 id 关联账号。
                    userName = userId,
                    lockDevice = deviceId,
                    lockSuccess = if (record.success) 1 else 0,
                    lockResult = record.resultJson,
                    lockDuration = record.durationSeconds,
                    lockType = 1,
                    appVersion = BuildConfig.VERSION_NAME,
                    lockLog = record.lockLog,
                    el = record.battery,
                    voltage = record.voltage,
                    lockTime = record.lockTime,
                    newPwd = record.newPwd,
                    location = record.location,
                    phoneBrand = Build.MANUFACTURER.orEmpty(),
                    phoneModel = Build.MODEL.orEmpty(),
                    phonePlatform = "Android",
                    phonePlatformVersion = Build.VERSION.RELEASE.orEmpty(),
                ).success
            }
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            android.util.Log.i("YunMei", "saveUnlockRecord failed=" + (error.message ?: ""))
            false
        }
    }

    /**
     * 重新拉一次门锁列表，用于把服务端最新的电量/更新时间同步到本地。
     *
     * 官方把「开门者当时读到的电量」随记录上报，服务端再把它写回门锁；
     * 因此刚上报完立刻重拉一次，其他手机（以及本机的门锁列表）才能看到同一个数。
     *
     * @return 拉取成功时返回门锁列表；未登录/网络失败返回 null，调用方保持本地值。
     */
    suspend fun locksOf(
        schoolNo: String,
        account: StoredUser? = null,
    ): List<com.yunmei.vibe.data.model.Lock>? = runCatching {
        if (client.token == null) {
            account ?: return@runCatching null
            login(account.username, account.passwordMd5)
        }
        fetchLocks(findSchool(schoolNo))
    }.getOrElse { error ->
        if (error is CancellationException) throw error
        null
    }

    private suspend fun findSchool(schoolNo: String): SchoolEntry =
        fetchSchools().firstOrNull { it.schoolNo == schoolNo }
            ?: throw YunMeiException(appContext.getString(R.string.school_info_missing))

    private fun requireUserId(): String =
        client.userId ?: throw YunMeiException(appContext.getString(R.string.login_error_not_logged_in))

    private fun requireServerUrl(school: SchoolEntry): String =
        school.school?.serverUrl?.takeIf { it.isNotBlank() }
            ?: throw YunMeiException(appContext.getString(R.string.school_server_missing))

    /**
     * 切到某所学校的会话：学校 token + 学校编号。
     *
     * `adminSchoolNo` 是官方前端对学校域接口统一附带的头，服务端用它定位学校上下文；
     * 取码接口只传 `lockNo`，少了这个头服务端无法判断是哪所学校的门锁。
     */
    private fun applySchoolAuth(school: SchoolEntry, userId: String) {
        client.token = school.token
        client.userId = userId
        client.adminSchoolNo = school.schoolNo
    }
}