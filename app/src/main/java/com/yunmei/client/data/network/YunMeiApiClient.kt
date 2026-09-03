package com.yunmei.client.data.network

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

class YunMeiApiClient {

    @Volatile
    var token: String? = null

    @Volatile
    var userId: String? = null

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
            token?.let { request.header("token_data", it) }
            userId?.let {
                request.header("token_userId", it)
                request.header("tokenUserId", it)
            }
            chain.proceed(request.build())
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
    }
}
