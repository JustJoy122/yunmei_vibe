// SPDX-License-Identifier: GPL-3.0-only
// 正式渠道逻辑对齐 KernelSU-Style-UI-Kit `ui/util/Downloader.kt` 的 checkNewVersion()。
package com.yunmei.vibe.ui.util

import com.yunmei.vibe.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** 本仓库的 GitHub Releases latest 接口（正式渠道，不含 pre-release）。 */
private const val LATEST_RELEASE_API =
    "https://api.github.com/repos/JustJoy122/yunmei_vibe/releases/latest"

/** GitHub Releases 列表接口（Debug / CI 渠道）。 */
private const val RELEASES_API =
    "https://api.github.com/repos/JustJoy122/yunmei_vibe/releases?per_page=100"

/** CI 渠道 tag 前缀，格式为 `ci-YYYYMMDD-<run_number>`（如 `ci-20260912-14`）。 */
private const val CI_TAG_PREFIX = "ci-"

/** 从 `ci-YYYYMMDD-<run_number>` 中解析 run_number。 */
private val CI_TAG_REGEX = Regex("^ci-\\d{8}-(\\d+)$")

/** 从资产名（如 `YunmeiVibe-v0.4.2-debug-ci20260912-14.apk`）中解析版本名。 */
private val APK_VERSION_REGEX = Regex("v(\\d+(?:\\.\\d+)+)")

private val updateHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
}

/**
 * 检查是否存在更新的构建版本。
 *
 * - **Release 构建**：走 [checkReleaseChannel]（GitHub Releases latest），只认正式 release APK，
 *   与 KernelSU-Style-UI-Kit 的 `checkNewVersion()` 行为一致；
 * - **Debug 构建**：走 [checkDebugChannel]，比较远端 `ci-*` 标签的 run_number 与本地
 *   `BuildConfig.BUILD_STAMP`（CI 通过 `-PbuildStamp` 注入），远端更大才提示更新。
 */
fun checkNewVersion(): LatestVersionInfo =
    if (BuildConfig.DEBUG) checkDebugChannel() else checkReleaseChannel()

/**
 * 正式渠道：请求 GitHub Releases latest，遍历 assets，只认正式 release APK（跳过 debug 产物），
 * 解析出版本名并做语义化版本比较。
 */
private fun checkReleaseChannel(): LatestVersionInfo {
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

                    val versionName = APK_VERSION_REGEX.find(name)?.groupValues?.getOrNull(1) ?: continue
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

/**
 * Debug / CI 渠道：请求 Releases 列表，取 run_number 最大的 `ci-*` Pre-release，
 * 与本地的 `BuildConfig.BUILD_STAMP` 比较，远端 run_number 更大才提示更新。
 *
 * 按 run_number 而非日期比较：同一天可能多次构建（`ci-YYYYMMDD-<run_number>`），
 * run_number 全局严格递增，可正确区分同一天的先后构建，不会漏更新。
 */
private fun checkDebugChannel(): LatestVersionInfo {
    val empty = LatestVersionInfo()
    val localStamp = BuildConfig.BUILD_STAMP.toIntOrNull() ?: 0

    return runCatching {
        updateHttpClient.newCall(Request.Builder().url(RELEASES_API).build())
            .execute()
            .use { response ->
                if (!response.isSuccessful) return@use empty
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@use empty

                val releases = JSONArray(body)
                var bestRun = localStamp
                var best = empty

                for (i in 0 until releases.length()) {
                    val release = releases.optJSONObject(i) ?: continue
                    val tag = release.optString("tag_name")
                    if (!tag.startsWith(CI_TAG_PREFIX)) continue

                    val runNumber = CI_TAG_REGEX.find(tag)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.toIntOrNull()
                        ?: continue
                    if (runNumber <= bestRun) continue

                    val asset = findChannelApk(release, keyword = "debug") ?: continue
                    val versionName = APK_VERSION_REGEX
                        .find(asset.optString("name"))
                        ?.groupValues
                        ?.getOrNull(1)
                        .orEmpty()

                    bestRun = runNumber
                    best = LatestVersionInfo(
                        versionName = if (versionName.isBlank()) {
                            "CI #$runNumber"
                        } else {
                            "$versionName (CI #$runNumber)"
                        },
                        downloadUrl = asset.optString("browser_download_url"),
                        changelog = release.optString("body"),
                    )
                }
                best
            }
    }.getOrDefault(empty)
}

/** 在 release 的资产中查找包含指定关键字的 APK（CI 渠道产物名含 `debug`）。 */
private fun findChannelApk(release: JSONObject, keyword: String): JSONObject? {
    val assets = release.optJSONArray("assets") ?: return null
    for (i in 0 until assets.length()) {
        val asset = assets.optJSONObject(i) ?: continue
        val name = asset.optString("name")
        if (!name.endsWith(".apk", ignoreCase = true)) continue
        if (!name.contains(keyword, ignoreCase = true)) continue
        return asset
    }
    return null
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
