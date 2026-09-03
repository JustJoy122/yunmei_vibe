package com.yunmei.client.data.network

import com.yunmei.client.data.model.ApiMessage
import com.yunmei.client.data.model.LockPasswordResponse
import com.yunmei.client.data.model.LoginResponse
import com.yunmei.client.data.model.SchoolEntry
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

    @FormUrlEncoded
    @POST("lockpassword/getlockpwdbylockno")
    suspend fun getLockPassword(@Field("lockNo") lockNo: String): LockPasswordResponse
}
