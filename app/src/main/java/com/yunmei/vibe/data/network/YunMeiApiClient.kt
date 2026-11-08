package com.yunmei.vibe.data.network

import android.util.Log
import com.yunmei.vibe.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

class YunMeiApiClient {

    @Volatile
    var token: String? = null

    @Volatile
    var userId: String? = null

    /**
     * 当前学校编号，学校域接口（`dormuser/`、`lockpassword/`、`lockrecord/` 等）会作为
     * `adminSchoolNo` 头下发给服务端。
     *
     * 官方前端在「非基础服务」请求上固定带这个头（`common/ajax.js`），服务端据此把只传
     * `lockNo` 的接口（取码、开门记录）定位到具体学校；基础服务请求必须为 null。
     */
    @Volatile
    var adminSchoolNo: String? = null

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("x-requested-with", "XMLHttpRequest")
            token?.let {
                // 官方客户端同时下发下划线与驼峰两份 token，服务端两条都会读。
                request.header("token_data", it)
                request.header("tokenData", it)
            }
            userId?.let {
                request.header("token_userId", it)
                request.header("tokenUserId", it)
            }
            adminSchoolNo?.takeIf { it.isNotBlank() }?.let {
                request.header("adminSchoolNo", it)
            }
            val built = request.build()
            // 兼容排查开关：取码/开门记录这类「静默失败」的学校接口在 debug 包里打印真实请求与响应，
            // release 包不受影响（BuildConfig.DEBUG 恒为 false）。
            if (BuildConfig.DEBUG && LOGGED_PATHS.any { built.url.encodedPath.contains(it) }) {
                Log.i(HTTP_TAG, "→ " + built.method + " " + built.url + " school=" + adminSchoolNo)
                val response = chain.proceed(built)
                val body = response.body.string()
                Log.i(HTTP_TAG, "← " + response.code + " " + body)
                return@addInterceptor response.newBuilder()
                    .body(body.toResponseBody(response.body.contentType()))
                    .build()
            }
            chain.proceed(built)
        }
        .build()

    /** 按 baseUrl 创建接口实例；base 与学校各自持有不同 token/userId。 */
    fun api(baseUrl: String): YunMeiApi {
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(YunMeiApi::class.java)
    }

    companion object {
        const val BASE_URL = "https://base.yunmeitech.com/"

        private const val HTTP_TAG = "YunMeiHttp"

        /** 只对会被服务端静默拒绝的接口打日志，避免把登录等敏感请求也写进日志。 */
        private val LOGGED_PATHS = listOf("/lockrecord/", "/lockpassword/")
    }
}
