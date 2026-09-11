// SPDX-License-Identifier: GPL-3.0-only
// 逻辑对齐 KernelSU-Style-UI-Kit `ui/util/Downloader.kt` 的 checkNewVersion()。
package com.yunmei.vibe.ui.util

import com.yunmei.vibe.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** 本仓库的 GitHub Releases latest 接口。 */
private const val LATEST_RELEASE_API =
    "https://api.github.com/repos/JustJoy122/yunmei_vibe/releases/latest"

private val updateHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
}

/**
 * 检查是否存在更新的正式 Release 版本。
 *
 * 与 KernelSU-Style-UI-Kit 的 `checkNewVersion()` 一致：请求 GitHub Releases latest，
 * 遍历 assets，只认正式 release APK（跳过 debug 产物），解析出版本名并做语义化版本比较。
 *
 * 仅面向正式 Release：`BuildConfig.DEBUG` 为 true 时直接返回空结果，
 * 确保 Debug 包不会触发更新提示与跳转。
 */
fun checkNewVersion(): LatestVersionInfo {
    if (BuildConfig.DEBUG) return LatestVersionInfo()

    val empty = LatestVersionInfo()
    return runCatching {
        updateHttpClient.newCall(Request.Builder().url(LATEST_RELEASE_API).build())
            .execute()
            .use { response ->
                if (!response.isSuccessful) return@use empty
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@use empty

                val json = JSONObject(body)
                val changelog = json.optString("body")
                val assets = json.optJSONArray("assets") ?: return@use empty

                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.getString("name")
                    // 只认正式 Release APK，Debug 产物不作为更新目标。
                    if (!name.endsWith(".apk", ignoreCase = true)) continue
                    if (!name.contains("release", ignoreCase = true)) continue

                    val versionName = Regex("v(\\d+(?:\\.\\d+)+)")
                        .find(name)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?: continue

                    if (compareVersion(versionName, BuildConfig.VERSION_NAME) <= 0) return@use empty

                    return@use LatestVersionInfo(
                        versionName = versionName,
                        downloadUrl = asset.getString("browser_download_url"),
                        changelog = changelog,
                    )
                }
                empty
            }
    }.getOrDefault(empty)
}

/** 语义化版本比较：a > b 时返回正数。 */
private fun compareVersion(a: String, b: String): Int {
    val pa = a.split(".").map { it.toIntOrNull() ?: 0 }
    val pb = b.split(".").map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(pa.size, pb.size)) {
        val diff = pa.getOrElse(i) { 0 } - pb.getOrElse(i) { 0 }
        if (diff != 0) return diff
    }
    return 0
}
