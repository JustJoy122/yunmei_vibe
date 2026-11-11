package com.yunmei.vibe.data.network

import android.util.Log
import com.yunmei.vibe.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
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
            val path = built.url.encodedPath
            val verbose = BuildConfig.DEBUG && LOGGED_PATHS.any { path.contains(it) }
            if (verbose) {
                Log.i(HTTP_TAG, "→ " + built.method + " " + built.url + " school=" + adminSchoolNo)
            }
            val response = chain.proceed(built)
            // 登录接口自己就是拿 token 的地方，它的失败（账号或密码错误）不能被当成「会话过期」。
            if (response.code != 200 || IGNORED_PATHS.any { path.contains(it) }) return@addInterceptor response
            // 会话过期在服务端表现为 200 + `{"error":true,"msg":"未登录或登录超时,请重新登录！"}`，
            // 或响应头 `session_status: timeout|loginerror`（官方 ajax.js 就是这么判断的）。
            // 这里必须读一次 body 才能判定，读完再原样放回去给 Retrofit。
            val body = runCatching { response.body.string() }.getOrNull() ?: return@addInterceptor response
            if (verbose) Log.i(HTTP_TAG, "← " + response.code + " " + body)
            val rebuilt = response.newBuilder()
                .body(body.toResponseBody(response.body.contentType()))
                .build()
            if (isSessionExpired(response, body)) {
                throw SessionExpiredException(expiredMessage(body))
            }
            rebuilt
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

        /** 这些接口的失败属于业务失败（账号或密码错误），不参与会话过期判定。 */
        private val IGNORED_PATHS = listOf("/login")

        private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

        /** 与服务端/官方 ajax.js 一致的过期判定：响应体 `error:true` 或 `session_status` 头。 */
        private fun isSessionExpired(response: Response, body: String): Boolean {
            val status = response.header("session_status")?.lowercase()
            if (status == "timeout" || status == "loginerror") return true
            val element = runCatching { lenientJson.parseToJsonElement(body) }.getOrNull() ?: return false
            if (element !is JsonObject) return false
            return element["error"]?.jsonPrimitive?.booleanOrNull == true
        }

        /** 尽量把服务端的中文提示带给上层，取不到时交给 Repository 兜底成本地化文案。 */
        private fun expiredMessage(body: String): String = runCatching {
            val element = lenientJson.parseToJsonElement(body) as? JsonObject ?: return@runCatching ""
            element["msg"]?.jsonPrimitive?.contentOrNull.orEmpty()
        }.getOrDefault("")
    }
}

/**
 * 服务端判定「未登录 / 登录超时」。
 *
 * 由 [YunMeiApiClient] 的拦截器在读到特定响应时抛出，Repository 捕获后清空会话、静默重登并
 * 重试一次；重试仍失败说明密码已改，此时转成 [com.yunmei.vibe.data.repository.YunMeiAuthException]
 * 让界面提示重新登录。
 */
class SessionExpiredException(message: String) : IOException(message)
