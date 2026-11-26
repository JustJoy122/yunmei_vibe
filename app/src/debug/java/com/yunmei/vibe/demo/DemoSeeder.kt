package com.yunmei.vibe.demo

import com.yunmei.vibe.core.di.AppContainer
import com.yunmei.vibe.data.local.SchoolSession
import com.yunmei.vibe.data.local.StoredSession
import com.yunmei.vibe.data.local.StoredUser
import com.yunmei.vibe.data.model.Lock
import com.yunmei.vibe.data.model.UserProfile
import com.yunmei.vibe.data.security.Md5

/**
 * Demo 模式的演示数据（**仅 debug 源集存在**）。
 *
 * 为什么需要它：门锁列表、个人中心、门锁页账号入口的数据源都是**本地存储**
 * （`LockStore` / `AccountStore` / `SessionStore`），不是网络。只拦网络的话首页会是空壳，
 * 所以这里把「已登录 + 有三把锁」的最小状态直接写进存储。
 *
 * 幂等：已经存在演示账号时不做任何写入，避免每次冷启动把用户改过的状态重置。
 */
object DemoSeeder {

    /** 与 [DemoMockInterceptor] 里的登录响应、档案 JSON 保持一致。 */
    private const val DEMO_TEL = "13800000000"
    private const val DEMO_USER_ID = "345943"
    private const val DEMO_TOKEN = "demo-login-token"
    private const val DEMO_SCHOOL_NO = "001"
    private const val DEMO_SCHOOL_URL = "https://xiaoyun.yunmeitech.com"
    private const val DEMO_SCHOOL_TOKEN = "demo-school-token"

    private const val WRITE_UUID = "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"
    private const val SERVICE_UUID = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"

    fun seed(container: AppContainer) {
        val usernameMd5 = Md5.hex(DEMO_TEL)
        runCatching {
            val alreadySeeded = container.accountStore.getAll().any { it.usernameMd5 == usernameMd5 }
            if (!alreadySeeded) {
                container.accountStore.replaceAll(
                    listOf(StoredUser(username = DEMO_TEL, usernameMd5 = usernameMd5, passwordMd5 = Md5.hex("demo1234")))
                )
            }

            val locks = demoLocks(usernameMd5)
            // 列表为空时才写，避免覆盖用户在一台演示机上手动改过的状态。
            if (container.lockStore.getAll().isEmpty()) {
                container.lockStore.replaceAll(locks, locks.first())
            }

            val sessionStore = container.sessionStore
            if (sessionStore.loadSession() == null) {
                sessionStore.saveSession(
                    StoredSession(
                        usernameMd5 = usernameMd5,
                        token = DEMO_TOKEN,
                        userId = DEMO_USER_ID,
                        savedAt = System.currentTimeMillis(),
                        realName = "张三",
                        userTel = DEMO_TEL,
                    )
                )
            }
            sessionStore.saveSchool(
                SchoolSession(
                    schoolNo = DEMO_SCHOOL_NO,
                    serverUrl = DEMO_SCHOOL_URL,
                    schoolToken = DEMO_SCHOOL_TOKEN,
                )
            )
            if (sessionStore.loadProfile(usernameMd5) == null) {
                sessionStore.saveProfile(usernameMd5, demoProfile())
            }
        }
    }

    /** 两把锁：一把信息完整、一把缺字段，字段含义见 [DemoMockInterceptor.LOCKS]。 */
    private fun demoLocks(usernameMd5: String): List<Lock> = listOf(
        Lock(
            label = "示例门锁",
            mac = "E1:C4:5E:97:49:94",
            writeUuid = WRITE_UUID,
            serviceUuid = SERVICE_UUID,
            secret = "demo-secret-0001",
            usernameMd5 = usernameMd5,
            schoolNo = DEMO_SCHOOL_NO,
            schoolName = "云莓公寓",
            lockNo = "E1:C4:5E:97:49:94",
            lockTypeId = 1,
            aesSupport = false,
            pwdSupport = true,
            pwdLength = 6,
            lockElectricity = 49,
            updateTime = "2026-11-26 19:04:12",
            areaNo = "01",
            buildNo = "09",
            dormNo = "215",
            areaName = "东区",
            buildName = "D9东",
            dormName = "215",
            lockTypeName = "APP",
        ),
        Lock(
            label = "扫码导入的门锁",
            mac = "AA:BB:CC:DD:EE:FF",
            writeUuid = WRITE_UUID,
            serviceUuid = SERVICE_UUID,
            secret = "demo-secret-0003",
            usernameMd5 = usernameMd5,
            schoolNo = DEMO_SCHOOL_NO,
            schoolName = "云莓公寓",
            lockNo = "AA:BB:CC:DD:EE:FF",
        ),
    )

    private fun demoProfile(): UserProfile = UserProfile(
        userId = DEMO_USER_ID,
        realName = "张三",
        stuNo = "1030449643",
        userTel = DEMO_TEL,
        schoolNo = DEMO_SCHOOL_NO,
        collegeName = "法学院",
        majorName = "知识产权",
        schoolYear = "2026",
        className = "26产权3班",
        areaName = "东区",
        buildName = "D9东",
        dormName = "215",
        attr1 = "1号床",
        // 演示头像用固定外链，避免 README 截图里出现真实用户头像。
        headImg = "https://github.com/JustJoy122.png",
    )
}
