package com.yunmei.vibe.data.network

import com.yunmei.vibe.data.model.ApiMessage
import com.yunmei.vibe.data.model.LoginResponse
import com.yunmei.vibe.data.model.SchoolEntry
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

/**
 * 云莓智能接口。与 zxy19/yunmei_unintelligent 完全一致：
 * 全部走 form-urlencoded POST，鉴权放在 header 的 token_data / token_userId / tokenUserId。
 */
interface YunMeiApi {

    @FormUrlEncoded
    @POST("login")
    suspend fun login(
        @Field("userName") userName: String,
        @Field("userPwd") userPwd: String,
    ): LoginResponse

    @FormUrlEncoded
    @POST("userschool/getbyuserid")
    suspend fun getSchools(@Field("userId") userId: String): List<SchoolEntry>

    @FormUrlEncoded
    @POST("dormuser/getuserlock")
    suspend fun getLocks(
        @Field("schoolNo") schoolNo: String,
        @Field("userId") userId: String,
    ): JsonElement

    @FormUrlEncoded
    @POST("signrecord/signbyschool")
    suspend fun sign(
        @Field("schoolNo") schoolNo: String,
        @Field("lockNo") lockNo: String,
        @Field("location") location: String,
    ): ApiMessage

    /**
     * 门锁型号能力表（基础服务）：返回 [{ id, aesSupport, pwdSupport, pwdLength }, ...]。
     *
     * 官方前端用空 body 的 POST 调用因此这里不能加 `@FormUrlEncoded`：Retrofit 要求表单方法
     * 至少有一个 `@Field`，加了会直接抛「Form-encoded method must contain at least one @Field」。
     */
    @POST("locktype/all")
    suspend fun getLockTypes(): JsonElement
    /**
     * 取开门密码（学校服务 `lockpassword/getlockpwdbylockno`）。
     *
     * 官方只传 `lockNo`，学校上下文靠 `adminSchoolNo` 头确定，所以这里同样只带一个字段；
     * 响应体是 `{ "lockPwd": "123456", "success": true, "msg": null }`，但服务端偶尔会
     * 把它再包一层 `data`，因此返回原始 JSON 由仓库层兼容两种形状。
     */
    @FormUrlEncoded
    @POST("lockpassword/getlockpwdbylockno")
    suspend fun getLockPassword(@Field("lockNo") lockNo: String): JsonElement

    /**
     * 开门记录上报（学校服务，官方 `lockrecord/savelockrecord`）。
     *
     * 字段名与官方前端逐字一致：服务端只做业务校验、不校验签名，
     * 因此这里原样透传，缺少的字段（本应用没有的楼栋/房间）由调用方传空串。
     */
    @FormUrlEncoded
    @POST("lockrecord/savelockrecord")
    suspend fun saveLockRecord(
        @Field("isDel") isDel: Int,
        @Field("schoolNo") schoolNo: String,
        @Field("areaNo") areaNo: String,
        @Field("buildNo") buildNo: String,
        @Field("dormNo") dormNo: String,
        @Field("lockNo") lockNo: String,
        @Field("userName") userName: String,
        @Field("lockDevice") lockDevice: String,
        @Field("lockSuccess") lockSuccess: Int,
        @Field("lockResult") lockResult: String,
        @Field("lockDuration") lockDuration: Int,
        @Field("lockType") lockType: Int,
        @Field("appVersion") appVersion: String,
        @Field("lockLog") lockLog: String,
        @Field("el") el: Int,
        @Field("voltage") voltage: Int,
        @Field("lockTime") lockTime: String,
        @Field("newPwd") newPwd: String,
        @Field("location") location: String,
        @Field("phoneBrand") phoneBrand: String,
        @Field("phoneModel") phoneModel: String,
        @Field("phonePlatform") phonePlatform: String,
        @Field("phonePlatformVersion") phonePlatformVersion: String,
    ): ApiMessage

    /**
     * 宿舍成员列表（学校服务，官方「我的群组」页 `dormuser/getbyloginuser`）。
     *
     * 官方按「校区/楼栋/房间」而不是按门锁取成员，因此入参是宿舍四元组；
     * 返回原始 JSON 由仓库层透出——服务端真实层级（是否再包一层 `data`）尚未实测。
     */
    @FormUrlEncoded
    @POST("dormuser/getbyloginuser")
    suspend fun getDormUsers(
        @Field("schoolNo") schoolNo: String,
        @Field("areaNo") areaNo: String,
        @Field("buildNo") buildNo: String,
        @Field("dormNo") dormNo: String,
    ): JsonElement

    /**
     * 同宿舍的学生名单（学校服务，官方「我的群组」页 `student/getbydormloginuser`）。
     *
     * 按宿舍而不是按门锁定位：`dormuser/getbyloginuser` 给的是「谁能开这把锁」，
     * 这个接口给的是「这些人是谁」——学院、班级、电话、头像都在这份数据里。
     */
    @FormUrlEncoded
    @POST("student/getbydormloginuser")
    suspend fun getDormStudents(
        @Field("schoolNo") schoolNo: String,
        @Field("areaNo") areaNo: String,
        @Field("buildNo") buildNo: String,
        @Field("dormNo") dormNo: String,
    ): JsonElement

    /**
     * 开门记录列表（学校服务，官方 `lockrecord/getrecordbyschool`）。
     *
     * 官方只传 `schoolNo` 与 `pageSize`：没有按门锁、按用户或按时间段的筛选参数，
     * 也没有翻页参数（一次取回首屏）。返回是裸 JSON 数组，由仓库层兼容包装形状。
     */
    @FormUrlEncoded
    @POST("lockrecord/getrecordbyschool")
    suspend fun getUnlockRecords(
        @Field("schoolNo") schoolNo: String,
        @Field("pageSize") pageSize: Int = 20,
    ): JsonElement

    /**
     * 本人档案（学校服务，官方「个人中心」`student/getbyloginuser`）。
     *
     * 入参只有 schoolNo；返回本人 User 对象（含 collegeName / className / dormName）。
     */
    @FormUrlEncoded
    @POST("student/getbyloginuser")
    suspend fun getMyProfile(@Field("schoolNo") schoolNo: String): JsonElement

    /**
     * 打卡状态（学校服务，官方 `signrecord/getsignstatusbyschool`）。
     *
     * 与打卡动作 `signrecord/signbyschool` 配套：动作接口不返回状态，
     * 官方在打卡成功后重新调本接口刷新「已打卡 HH:mm」。
     */
    @FormUrlEncoded
    @POST("signrecord/getsignstatusbyschool")
    suspend fun getSignStatus(@Field("schoolNo") schoolNo: String): JsonElement
}
