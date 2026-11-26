package com.yunmei.vibe.demo

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * Demo 模式下的网络替身（**仅 debug 源集存在**）。
 *
 * 只拦「读数据」和演示用的几个写接口，返回官方真实形状的 JSON，让界面在没有网络、
 * 没有账号的情况下也能走完整条路径（首页电量、打卡状态、门锁列表、群组、记录、个人中心）。
 *
 * 约定：
 * - 按 [Interceptor.Chain.request] 的 `encodedPath` 匹配，命中就返回 200 + 固定 JSON；
 * - 未命中的请求原样放行（例如以后新增的接口会真的发出去，便于尽早发现漏配）；
 * - 不写任何日志，避免与线上日志混淆。
 */
class DemoMockInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath
        val json = payloadFor(path) ?: return chain.proceed(request)
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(json.toResponseBody(JSON_MEDIA_TYPE))
            .build()
    }

    /** 路径 → 固定响应。顺序有意义：更具体的路径要排在包含它的前缀之前。 */
    private fun payloadFor(path: String): String? = when {
        path.contains("/loginuser/") -> LOGINUSER_OK
        path.contains("/login") -> LOGIN_OK
        path.contains("/userschool/getbyuserid") -> SCHOOLS
        path.contains("/dormuser/getuserlock") -> LOCKS
        path.contains("/locktype/all") -> LOCK_TYPES
        path.contains("/dormuser/getbyloginuser") -> DORM_MEMBERS
        path.contains("/student/getbydormloginuser") -> DORM_STUDENTS
        path.contains("/student/getbyloginuser") -> PROFILE
        path.contains("/signrecord/getsignstatusbyschool") -> SIGN_STATUS
        path.contains("/signrecord/signbyschool") -> SAVE_OK
        path.contains("/lockrecord/getrecordbyschool") -> UNLOCK_RECORDS
        path.contains("/lockrecord/") -> SAVE_OK
        path.contains("/lockpassword/getlockpwdbylockno") -> LOCK_PASSWORD
        else -> null
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        const val LOGINUSER_OK = """{"success":true,"msg":"操作成功"}"""

        const val SAVE_OK = """{"success":true,"msg":"保存成功！"}"""

        const val LOCK_PASSWORD = """
            {"success":true,"msg":"获取成功","lockPwd":"246810"}
        """

        /** 登录：名字和手机号与 [DemoSeeder] 落盘的会话保持一致。 */
        const val LOGIN_OK = """
            {"success":true,"msg":"登录成功","o":{
              "token":"demo-login-token",
              "userId":"345943",
              "userTel":"13800000000",
              "realName":"张三"
            }}
        """

        /** 学校列表：只有一所，serverUrl 指向真实的学校域，方便肉眼确认请求确实被拦截。 */
        const val SCHOOLS = """
            [{
              "schoolNo":"001",
              "token":"demo-school-token",
              "school":{"schoolName":"云莓公寓","serverUrl":"https://xiaoyun.yunmeitech.com"}
            }]
        """

        /**
         * 两把锁覆盖两种展示分支：
         * 1. 示例门锁——电量/更新时间齐全，首页 Banner 与详情页都是完整信息；
         * 2. 缺字段锁——没有校区/楼栋/房间名，用来验证「缺字段整行隐藏」。
         */
        const val LOCKS = """
            {"data":[
              {
                "lockNo":"E1:C4:5E:97:49:94",
                "lockName":"示例门锁",
                "lockCharacterUuid":"6E400002-B5A3-F393-E0A9-E50E24DCCA9E",
                "lockServiceUuid":"6E400001-B5A3-F393-E0A9-E50E24DCCA9E",
                "lockSecret":"demo-secret-0001",
                "lockTypeId":1,
                "lockElectricity":49,
                "updateTime":"2026-11-26 19:04:12",
                "schoolNo":"001",
                "schoolName":"云莓公寓",
                "areaNo":"01","areaName":"东区",
                "buildNo":"09","buildName":"D9东",
                "dormNo":"215","dormName":"215"
              },
              {
                "lockNo":"AA:BB:CC:DD:EE:FF",
                "lockName":"扫码导入的门锁",
                "lockCharacterUuid":"6E400002-B5A3-F393-E0A9-E50E24DCCA9E",
                "lockServiceUuid":"6E400001-B5A3-F393-E0A9-E50E24DCCA9E",
                "lockSecret":"demo-secret-0003",
                "schoolNo":"001"
              }
            ]}
        """

        /** 型号能力表：1 = 普通（支持临时密码），2 = AES。 */
        const val LOCK_TYPES = """
            [
              {"id":1,"typeName":"APP","aesSupport":0,"pwdSupport":1,"pwdLength":6},
              {"id":2,"typeName":"APP-AES","aesSupport":1,"pwdSupport":1,"pwdLength":6}
            ]
        """

        /** 打卡状态：当前时段已打卡，展示「已打卡 HH:mm:ss」。 */
        const val SIGN_STATUS = """
            [{
              "set":{"startTime":"18:00","endTime":"22:30"},
              "signRecord":{"signTime":"2026-11-26 19:04:12"},
              "askLeave":false
            }]
        """

        /** 本人档案：字段名与学校域 User 对象一致（学生用 stuName/tel/stuNo）。 */
        const val PROFILE = """
            {
              "userId":"345943",
              "stuName":"张三",
              "stuNo":"1030449643",
              "tel":"13800000000",
              "schoolNo":"001",
              "collegeName":"法学院",
              "majorName":"知识产权",
              "schoolYear":"2026",
              "className":"26产权3班",
              "areaName":"东区",
              "buildName":"D9东",
              "dormName":"215",
              "attr1":"1号床",
              "headImg":"https://github.com/JustJoy122.png"
            }
        """

        /** 群组成员：住宿关系（谁能开这把锁），学籍由下一个接口补齐。 */
        const val DORM_MEMBERS = """
            [{
              "id":"1","userId":"345943","roleType":1,"schoolNo":"001",
              "schoolName":"云莓公寓","areaNo":"01","areaName":"东区",
              "buildNo":"09","buildName":"D9东","dormNo":"215","dormName":"215",
              "loginUserInfo":{"id":"1","userId":"345943","userTel":"13800000000",
                "nickName":"张三","realName":"张三","userName":"13800000000"}
            },{
              "id":"2","userId":"345944","roleType":1,"schoolNo":"001",
              "schoolName":"云莓公寓","areaNo":"01","areaName":"东区",
              "buildNo":"09","buildName":"D9东","dormNo":"215","dormName":"215",
              "loginUserInfo":{"id":"2","userId":"345944","userTel":"13800000001",
                "nickName":"李四","realName":"李四","userName":"13800000001"}
            },{
              "id":"3","userId":"345945","roleType":1,"schoolNo":"001",
              "schoolName":"云莓公寓","areaNo":"01","areaName":"东区",
              "buildNo":"09","buildName":"D9东","dormNo":"215","dormName":"215",
              "loginUserInfo":{"id":"3","userId":"345945","userTel":"13800000002",
                "nickName":"王五","realName":"王五","userName":"13800000002"}
            }]
        """

        /** 同宿舍学生名单：给上面三位补学院/班级（按 dormuserId 或手机号匹配）。 */
        const val DORM_STUDENTS = """
            [{
              "userId":"345943","realName":"张三","userTel":"13800000000",
              "collegeName":"法学院","className":"26产权3班"
            },{
              "userId":"345944","realName":"李四","userTel":"13800000001",
              "collegeName":"法学院","className":"26产权3班"
            },{
              "userId":"345945","realName":"王五","userTel":"13800000002",
              "collegeName":"法学院","className":"26产权3班"
            }]
        """

        /** 开门记录：官方只按学校聚合，字段名与真实响应一致。 */
        const val UNLOCK_RECORDS = """
            [{
              "buildName":"D9东","dormName":"215","realName":"张三",
              "lockTime":"2026-11-26 19:04:12"
            },{
              "buildName":"D9东","dormName":"215","realName":"李四",
              "lockTime":"2026-11-26 08:12:05"
            },{
              "buildName":"D9东","dormName":"216","realName":"王五",
              "lockTime":"2026-11-25 23:41:37"
            }]
        """
    }
}
