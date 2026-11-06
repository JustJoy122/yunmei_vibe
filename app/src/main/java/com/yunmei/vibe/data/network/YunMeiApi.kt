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

    /** 门锁型号能力表（基础服务）：返回 [{ id, aesSupport }, ...]，不需要额外请求参数。 */
    @FormUrlEncoded
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
}
