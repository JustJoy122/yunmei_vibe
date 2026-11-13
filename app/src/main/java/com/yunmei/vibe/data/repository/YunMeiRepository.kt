package com.yunmei.vibe.data.repository

import android.content.Context
import android.os.Build
import android.util.Log
import com.yunmei.vibe.BuildConfig
import com.yunmei.vibe.R
import com.yunmei.vibe.data.local.AccountStore
import com.yunmei.vibe.data.local.SchoolSession
import com.yunmei.vibe.data.local.SecureStore
import com.yunmei.vibe.data.local.SessionStore
import com.yunmei.vibe.data.local.StoredSession
import com.yunmei.vibe.data.local.StoredUser
import com.yunmei.vibe.data.model.DormMember
import com.yunmei.vibe.data.model.Lock
import com.yunmei.vibe.data.model.LoginUser
import com.yunmei.vibe.data.model.LoginUserInfo
import com.yunmei.vibe.data.model.SchoolEntry
import com.yunmei.vibe.data.model.SignStatus
import com.yunmei.vibe.data.model.UnitCodes
import com.yunmei.vibe.data.model.UnlockRecord
import com.yunmei.vibe.data.model.UnlockRecordItem
import com.yunmei.vibe.data.model.UserProfile
import com.yunmei.vibe.data.network.SessionExpiredException
import com.yunmei.vibe.data.network.YunMeiApiClient
import com.yunmei.vibe.data.security.Md5
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale

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
class YunMeiRepository(
    context: Context,
    private val client: YunMeiApiClient,
    private val accountStore: AccountStore,
) {

    /** 应用 Context，仅用于读取界面文案资源（数据层不再硬编码中文）。 */
    private val appContext = context.applicationContext

    private val mutex = Mutex()

    /**
     * 会话级操作（恢复 / 静默登录 / 过期续期）的串行锁。
     *
     * 与 [mutex] 分开是为了避免「拿着请求锁再去 login()」——[login] 自己要取 [mutex]，
     * 同一个 Mutex 不可重入，混用会永久挂起（本项目已经踩过一次 `findSchool` 的坑）。
     * 全项目的加锁顺序固定为 sessionMutex → mutex，不允许反向。
     */
    private val sessionMutex = Mutex()

    private val sessionStore = SessionStore(SecureStore.get(appContext))

    private companion object {
        const val TAG = "YunMeiSession"
    }

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
        // 会话持久化：进程被杀后冷启动仍能直接复用，不必让用户重新输密码。
        sessionStore.saveSession(
            StoredSession(
                usernameMd5 = this.usernameMd5.orEmpty(),
                token = safeUser.token,
                userId = safeUser.userId,
                savedAt = System.currentTimeMillis(),
                realName = safeUser.realName.orEmpty(),
                userTel = safeUser.userTel.orEmpty(),
            )
        )
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
        val element = client.api(serverUrl).getLocks(school.schoolNo.orEmpty(), userId)
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
            android.util.Log.w("YunMei", "locktype/all failed: " + (error.message ?: ""))
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
    suspend fun getDormMembers(lock: Lock): List<DormMember> = withSessionRetry {
        // findSchool 内部还会取一次 mutex（fetchSchools），必须放在 withLock 之外，
        // 否则同一协程重入不可重入的 Mutex 会永久挂起——表现为点了没有任何反应。
        val school = findSchool(lock.schoolNo)
        val userId = requireUserId()
        val serverUrl = requireServerUrl(school)
        val members = mutex.withLock {
            applySchoolAuth(school, userId)
            parseDormMembers(
                client.api(serverUrl).getDormUsers(
                    schoolNo = lock.schoolNo,
                    areaNo = lock.areaNo,
                    buildNo = lock.buildNo,
                    dormNo = lock.dormNo,
                )
            )
        }
        enrichWithDormStudents(
            members = members,
            lock = lock,
            school = school,
            userId = userId,
            serverUrl = serverUrl,
        )
    }

    /**
     * 用同宿舍的学生名单补齐每位成员的学院/班级。
     *
     * 权限接口 `dormuser/getbyloginuser` 只给住宿关系，学籍在 `student/getbydormloginuser`
     * （同样按 areaNo/buildNo/dormNo 定位宿舍）里，官方「我的群组」页就是这么拼出来的：
     * 前者决定「谁能开这把锁」，后者决定「这个人是谁、哪个学院哪个班」。
     *
     * 学籍接口失败不影响成员列表本身——住宿关系才是这个页面的主体。
     */
    private suspend fun enrichWithDormStudents(
        members: List<DormMember>,
        lock: Lock,
        school: SchoolEntry,
        userId: String,
        serverUrl: String,
    ): List<DormMember> {
        if (members.isEmpty()) return members
        val students = runCatching {
            mutex.withLock {
                applySchoolAuth(school, userId)
                client.api(serverUrl).getDormStudents(
                    schoolNo = lock.schoolNo,
                    areaNo = lock.areaNo,
                    buildNo = lock.buildNo,
                    dormNo = lock.dormNo,
                )
            }
        }.getOrNull() ?: return members
        val list = when (students) {
            is JsonArray -> students
            is JsonObject -> (students["data"] as? JsonArray) ?: (students["o"] as? JsonArray) ?: JsonArray(emptyList())
            else -> JsonArray(emptyList())
        }
        if (list.isEmpty()) return members
        val byUserId = HashMap<String, JsonObject>()
        val byTel = HashMap<String, JsonObject>()
        list.mapNotNull { it as? JsonObject }.forEach { item ->
            item.jsonText("userId")?.takeIf { it.isNotBlank() }?.let { byUserId[it] = item }
            item.jsonText("userTel")?.takeIf { it.isNotBlank() }?.let { byTel[it] = item }
        }
        return members.map { member ->
            val student = byUserId[member.userId] ?: byTel[member.loginUserInfo.userTel]
            if (student == null) {
                member
            } else {
                member.copy(
                    college = member.college ?: student.jsonText("collegeName", "college")
                        ?.takeIf { it.isNotBlank() },
                    className = member.className ?: student.jsonText("className", "class")
                        ?.takeIf { it.isNotBlank() },
                    loginUserInfo = member.loginUserInfo.copy(
                        realName = member.loginUserInfo.realName.ifBlank {
                            student.jsonText("realName").orEmpty()
                        },
                        userTel = member.loginUserInfo.userTel.ifBlank {
                            student.jsonText("userTel").orEmpty()
                        },
                    ),
                )
            }
        }
    }

    /** 成员列表 JSON 解析：真机抓到的形状是裸数组，同时兼容 `data` / `o` 包裹。 */
    private fun parseDormMembers(element: JsonElement): List<DormMember> {
        val array = when (element) {
            is JsonArray -> element
            is JsonObject -> {
                // 官方失败时给的是 {"success":false,"msg":"…"}：不能静默当成「没有记录」，
                // 否则页面只剩空白，排查时看不出是权限、登录态还是接口问题。
                val success = element["success"]?.jsonPrimitive?.booleanOrNull
                if (success == false) {
                    val msg = element["msg"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    throw YunMeiException(
                        if (msg.isBlank()) appContext.getString(R.string.unlock_records_failed_generic) else msg
                    )
                }
                (element["data"] as? JsonArray)
                    ?: (element["o"] as? JsonArray)
                    ?: JsonArray(emptyList())
            }
            else -> JsonArray(emptyList())
        }
        return array.mapNotNull { item -> item as? JsonObject }.map { item ->
            val info = item["loginUserInfo"] as? JsonObject
            DormMember(
                userId = item.jsonText("userId").orEmpty(),
                roleType = item.jsonInt("roleType") ?: DormMember.ROLE_MEMBER,
                status = item.jsonInt("status") ?: DormMember.STATUS_ENABLED,
                startTime = item.jsonText("startTime").orEmpty(),
                endTime = item.jsonText("endTime").orEmpty(),
                schoolName = item.jsonText("schoolName").orEmpty(),
                areaName = item.jsonText("areaName").orEmpty(),
                buildName = item.jsonText("buildName").orEmpty(),
                dormName = item.jsonText("dormName").orEmpty(),
                // 成员接口目前不带学籍字段，服务端若将来补上也能直接接住。
                college = item.jsonText("collegeName", "college")?.takeIf { it.isNotBlank() }
                    ?: info?.jsonText("collegeName")?.takeIf { it.isNotBlank() },
                className = item.jsonText("className", "class")?.takeIf { it.isNotBlank() }
                    ?: info?.jsonText("className")?.takeIf { it.isNotBlank() },
                loginUserInfo = LoginUserInfo(
                    id = info?.jsonText("id").orEmpty(),
                    userId = info?.jsonText("userId").orEmpty(),
                    userTel = info?.jsonText("userTel").orEmpty(),
                    nickName = info?.jsonText("nickName").orEmpty(),
                    realName = info?.jsonText("realName").orEmpty(),
                    email = info?.jsonText("email").orEmpty(),
                    userName = info?.jsonText("userName").orEmpty(),
                ),
            )
        }
    }


    /** 取整数字段，兼容服务端把数字写成字符串的情况。 */
    private fun JsonObject.jsonInt(key: String): Int? =
        this[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull()

    /**
     * 拉取当前账号在本校的开门记录（官方 `lockrecord/getrecordbyschool`）。
     *
     * 接口只接受 `schoolNo` + `pageSize`：服务端按登录账号聚合该账号名下所有门锁的记录，
     * 不接受按门锁/按人筛选，所以这里只借用 [lock] 定位学校，不参与过滤。
     * 返回体既可能是裸数组，也可能被包在 `data` 或 `o` 里，三种形状都兼容。
     */
    suspend fun getUnlockRecords(lock: Lock): List<UnlockRecordItem> = withSessionRetry {
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
            is JsonObject -> {
                // 官方失败时给的是 {"success":false,"msg":"…"}：不能静默当成「没有记录」，
                // 否则页面只剩空白，排查时看不出是权限、登录态还是接口问题。
                val success = element["success"]?.jsonPrimitive?.booleanOrNull
                if (success == false) {
                    val msg = element["msg"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    throw YunMeiException(
                        if (msg.isBlank()) appContext.getString(R.string.unlock_records_failed_generic) else msg
                    )
                }
                (element["data"] as? JsonArray)
                    ?: (element["o"] as? JsonArray)
                    ?: JsonArray(emptyList())
            }
            else -> JsonArray(emptyList())
        }
        array.mapNotNull { item -> item as? JsonObject }.map { item ->
            UnlockRecordItem(
                buildName = item.jsonText("buildName").orEmpty(),
                dormName = item.jsonText("dormName").orEmpty(),
                realName = item.jsonText("realName").orEmpty(),
                lockTime = item.jsonText("lockTime").orEmpty(),
            )
        }
    }

    /**
     * 查询学校当前打卡状态（官方 `signrecord/getsignstatusbyschool`）。
     *
     * 返回值语义：null = 学校没有开启打卡（数组为空 / 响应 null），界面上不显示状态行；
     * 非 null 表示至少有一个打卡时段，[SignStatus.signed] 决定「已打卡 / 未打卡」。
     */
    suspend fun getSignStatus(school: SchoolEntry): SignStatus? = mutex.withLock {
        val userId = requireUserId()
        val serverUrl = requireServerUrl(school)
        applySchoolAuth(school, userId)
        parseSignStatus(client.api(serverUrl).getSignStatus(school.schoolNo.orEmpty()))
    }

    /** 按门锁所在学校查打卡状态：先补齐会话、再定位学校，最后复用上面的重载。 */
    suspend fun getSignStatus(lock: Lock): SignStatus? = withSessionRetry {
        getSignStatus(findSchool(lock.schoolNo))
    }

    /**
     * 解析打卡状态数组。
     *
     * 服务端可能把数组再包一层 `data` / `o`，[extractArray] 已经兼容；这里只取第一项
     * （官方页面同样只渲染一个时段）。`signTime` 既可能是字符串，也可能是
     * `{hours, minutes}` 对象，两种形状都兼容。
     */
    private fun parseSignStatus(element: JsonElement): SignStatus? {
        val item = extractArray(element).firstOrNull() ?: return null
        val window = item["set"] as? JsonObject
        val record = item["signRecord"] as? JsonObject
        val rawTime = record?.signTimeText().orEmpty()
        return SignStatus(
            startTime = window?.jsonText("startTime").orEmpty(),
            endTime = window?.jsonText("endTime").orEmpty(),
            // 只保留时分秒：官方前端也是 `signTime.split(" ")[1]`。
            signTime = rawTime.substringAfter(' ').ifBlank { rawTime },
            askLeave = item["askLeave"].truthy(),
        )
    }

    /** `signRecord.signTime` 的两种形状：字符串，或 `{hours, minutes}`。 */
    private fun JsonObject.signTimeText(): String {
        val raw = this["signTime"] ?: return ""
        (raw as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
        if (raw is JsonObject) {
            val hours = raw["hours"]?.jsonPrimitive?.intOrNull
            val minutes = raw["minutes"]?.jsonPrimitive?.intOrNull
            if (hours != null && minutes != null) {
                return String.format(Locale.US, "%02d:%02d:00", hours, minutes)
            }
        }
        return ""
    }

    /** 兼容 `true` / `"true"` / `1` 三种布尔写法。 */
    private fun JsonElement?.truthy(): Boolean {
        val primitive = this as? JsonPrimitive ?: return false
        if (primitive.isString) {
            val text = primitive.content
            return text == "1" || text.equals("true", ignoreCase = true)
        }
        return primitive.booleanOrNull == true
    }

    /** 依次尝试多个字段名，取到第一个非空字符串。 */    private fun JsonObject.jsonText(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> this[key]?.jsonPrimitive?.contentOrNull }

    /** 用已保存账号重新登录后获取开门密码（供主界面长期使用）。 */
    suspend fun getLockPassword(user: StoredUser, lock: Lock): String = withSessionRetry(user) {
        val school = findSchool(lock.schoolNo)
        getLockPassword(school, lock)
    }

    /** 用已保存账号重新登录后打卡。 */
    suspend fun sign(user: StoredUser, lock: Lock, location: String): String = withSessionRetry(user) {
        val school = findSchool(lock.schoolNo)
        // 打卡接口只依赖 schoolNo + lockNo + location，不依赖门锁列表。
        // 服务器 getuserlock 返回空时，门锁是扫码/手动导入的，不能因为
        // 列表查不到而拒绝打卡（与原版 SignService 行为一致）。
        sign(school, lock, location)
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
        // 没有会话时用调用方给的账号（或本地第一个）静默登录；拿不到账号就按「离线」处理。
        ensureSession(account)
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
            ensureSession()
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
            android.util.Log.w("YunMei", "saveUnlockRecord failed=" + (error.message ?: ""))
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

    /**
     * 冷启动恢复会话：把上次保存的 token / userId 填回内存。
     *
     * 只在启动时调用一次；失败（没有会话、安全存储不可用）按未登录处理，
     * 后续任何需要网络的入口都会走 [ensureSession] 自动补登录。
     */
    suspend fun restoreSession(): Boolean {
        val stored = sessionStore.loadSession() ?: return false
        sessionMutex.withLock {
            client.token = stored.token
            client.userId = stored.userId
            client.adminSchoolNo = null
            baseToken = stored.token
            usernameMd5 = stored.usernameMd5
        }
        Log.i(TAG, "已恢复上次的登录会话")
        return true
    }

    /**
     * 统一会话守卫：内存里没有登录 token 时，用传入账号或本地第一个已保存账号静默登录。
     *
     * 调用前不能持有 [mutex]（[login] 自己要取它）；本方法内部按 sessionMutex → mutex 顺序加锁，
     * 并做双检，避免并发时重复登录（登录会把 client 的 header 切来切去）。
     *
     * @param force 为 true 时即使当前有 token 也重新登录（用于「服务端说已过期」的续期）。
     */
    suspend fun ensureSession(account: StoredUser? = null, force: Boolean = false) {
        if (!force && client.token != null) return
        sessionMutex.withLock {
            if (!force && client.token != null) return@withLock
            val target = account ?: pickAccount()
                ?: throw YunMeiException(appContext.getString(R.string.login_error_not_logged_in))
            Log.i(TAG, "静默登录以恢复会话")
            login(target.username, target.passwordMd5)
        }
    }

    /** 本地已有账号里挑一个：优先当前会话对应的账号，否则取列表第一个。 */
    private suspend fun pickAccount(): StoredUser? = withContext(Dispatchers.IO) {
        val current = usernameMd5 ?: username?.let { Md5.hex(it) }
        current?.let { accountStore.getByUsernameMd5(it) } ?: accountStore.getAll().firstOrNull()
    }

    /** 作废内存里的 token 与持久化会话（服务端说过期、或用户退出登录时）。 */
    private fun invalidateSession() {
        client.token = null
        client.adminSchoolNo = null
        baseToken = null
        sessionStore.clearSession()
    }

    /** 退出登录 / 移除账号：内存会话与持久化会话一并清掉。 */
    fun clearSession() {
        invalidateSession()
        client.userId = null
        username = null
        usernameMd5 = null
        sessionStore.clearAll()
    }

    /** 当前会话对应的账号标识（未登录为 null）。 */
    val currentUsernameMd5: String? get() = usernameMd5

    /** 最近一次会话里缓存的账号信息：断网/失败时界面用它兜底显示姓名。 */
    /** 已缓存的本人档案（个人中心/门锁页先用它渲染，再后台刷新）。 */
    suspend fun cachedProfile(): UserProfile? =
        withContext(Dispatchers.IO) { sessionStore.loadProfile() }

    suspend fun cachedSession(): StoredSession? =
        withContext(Dispatchers.IO) { sessionStore.loadSession() }

    /**
     * 本人档案（学校服务 `student/getbyloginuser`）。
     *
     * 单位取当前会话：优先 `adminSchoolNo` 命中的那家，否则取账号所属的第一家。
     * 没有可用单位或接口失败时返回 null，界面退回 [cachedSession] 里的姓名。
     */
    suspend fun getMyProfile(): UserProfile? = withSessionRetry {
        val school = currentSchool() ?: return@withSessionRetry null
        val userId = requireUserId()
        applySchoolAuth(school, userId)
        val element = client.api(requireServerUrl(school)).getMyProfile(school.schoolNo.orEmpty())
        val parsed = parseUserProfile(element)
        // 档案接口没给 userId 时用会话里的 userId 兜底：头像地址只依赖它，缺了就只剩首字头像。
        val resolved = if (parsed != null && parsed.userId.isBlank()) {
            val sessionUserId = sessionStore.loadSession()?.userId.orEmpty()
            if (sessionUserId.isNotBlank()) parsed.copy(userId = sessionUserId) else parsed
        } else {
            parsed
        }
        // 存一份快照：门锁页入口与个人中心下次可以立即渲染，不必等网络。
        if (resolved != null) sessionStore.saveProfile(resolved)
        resolved
    }

    /**
     * 切换到某个已保存账号。
     *
     * 强制重新登录以拿到属于该账号的 token（多账号共用一份内存会话），
     * 门锁快照由调用方在刷新时通过 [locksOf] 同步。
     */
    suspend fun switchAccount(account: StoredUser) {
        ensureSession(account, force = true)
    }

    /** 当前会话所在单位：优先与 `adminSchoolNo` 一致，否则取第一项。 */
    private suspend fun currentSchool(): SchoolEntry? {
        val target = client.adminSchoolNo
        val schools = fetchSchools()
        return schools.firstOrNull { it.schoolNo == target } ?: schools.firstOrNull()
    }

    private fun parseUserProfile(element: JsonElement): UserProfile? {
        val candidate = when {
            element is JsonObject && element["data"] is JsonObject -> element["data"] as JsonObject
            element is JsonObject && element["o"] is JsonObject -> element["o"] as JsonObject
            element is JsonArray -> element.firstOrNull() as? JsonObject
            else -> element as? JsonObject
        } ?: return null
        return UserProfile(
            // 学校的 User 对象主键在不同接口里叫 `userId` 或 `id`（官方前端也做过 id→userId 归一化），
            // 两个都读，否则头像 URL 拼不出来。
            userId = candidate.jsonText("userId").orEmpty()
                .ifBlank { candidate.jsonText("id").orEmpty() },
            // 学校域的 User 对象用 `stuName` / `tel` / `stuNo`，基础服务用 `realName` / `userTel` /
            // `userName`，两套叫法都读，取到哪个算哪个。
            realName = candidate.jsonText("realName").orEmpty()
                .ifBlank { candidate.jsonText("stuName").orEmpty() },
            stuNo = candidate.jsonText("stuNo").orEmpty()
                .ifBlank { candidate.jsonText("userName").orEmpty() },
            userTel = candidate.jsonText("userTel").orEmpty()
                .ifBlank { candidate.jsonText("tel").orEmpty() },
            schoolNo = candidate.jsonText("schoolNo").orEmpty(),
            collegeName = candidate.jsonText("collegeName").orEmpty(),
            majorName = candidate.jsonText("majorName").orEmpty(),
            schoolYear = candidate.jsonText("schoolYear").orEmpty(),
            className = candidate.jsonText("className").orEmpty(),
            areaName = candidate.jsonText("areaName").orEmpty(),
            buildName = candidate.jsonText("buildName").orEmpty(),
            dormName = candidate.jsonText("dormName").orEmpty(),
            attr1 = candidate.jsonText("attr1").orEmpty(),
            headImg = candidate.jsonText("headImg").orEmpty(),
        )
    }

    /**
     * 带「过期 → 静默续期 → 重试一次」的请求包装。
     *
     * 防死循环：重试**只发生一次**，且重试路径不再进入本包装（直接执行 [block]），
     * 第二次仍被判过期时立刻转成 [YunMeiAuthException] 抛出（说明密码已改，只能人工重登）。
     */
    private suspend fun <T> withSessionRetry(account: StoredUser? = null, block: suspend () -> T): T {
        ensureSession(account)
        return try {
            block()
        } catch (expired: SessionExpiredException) {
            Log.i(TAG, "服务端判定会话过期，尝试静默续期")
            invalidateSession()
            ensureSession(account, force = true)
            try {
                block()
            } catch (again: SessionExpiredException) {
                invalidateSession()
                throw YunMeiAuthException(
                    again.message?.takeIf { it.isNotBlank() }
                        ?: appContext.getString(R.string.login_error_expired)
                )
            }
        }
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
        // 学校 token 与登录 token 是两套凭据，一并缓存，重启后可省掉一次 userschool 往返。
        sessionStore.saveSchool(
            SchoolSession(
                schoolNo = school.schoolNo.orEmpty(),
                serverUrl = school.school?.serverUrl.orEmpty(),
                schoolToken = school.token.orEmpty(),
            )
        )
    }
}