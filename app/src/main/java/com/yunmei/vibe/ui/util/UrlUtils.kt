package com.yunmei.vibe.ui.util

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import com.yunmei.vibe.data.network.YunMeiApiClient

/**
 * 服务协议页：官方 App 硬编码在基础服务根目录下的静态页。
 *
 * 官方 `pages/login/login` 里是 `getBaseServer() + "/fwxy.html"`；这里用同一个根地址拼，
 * 保证与官方展示的是同一份内容（学校域上没有这两页）。
 */
fun serviceAgreementUrl(): String = docUrl("fwxy.html")

/** 隐私政策页：同上，官方为 `getBaseServer() + "/privacy.html"`。 */
fun privacyPolicyUrl(): String = docUrl("privacy.html")

private fun docUrl(file: String): String =
    YunMeiApiClient.BASE_URL.trimEnd('/') + "/" + file

/**
 * 用 Custom Tabs 打开外部页面。
 *
 * 系统在没有支持 Custom Tabs 的浏览器时由框架自行回退到普通 VIEW Intent，
 * 因此这里不再手写 `try ACTION_VIEW catch` 的兜底；调用本身仍包在 runCatching 里，
 * 避免设备上没有任何浏览器时崩在主线程。
 */
fun openUrlWithCustomTab(context: Context, url: String) {
    runCatching {
        CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
    }.onFailure { error ->
        Log.w("YunMeiUrl", "打开链接失败：$url", error)
    }
}
