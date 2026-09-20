package com.yunmei.vibe.ui.navigation

import android.os.Parcelable
import top.yukonga.miuix.kmp.nav.core.NavKey
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * Type-safe navigation keys for miuix-nav。
 * 每个目的地都是 NavKey（data object / data class），可随 back stack 保存恢复。
 *
 * 注意：接口本身必须标注 @Serializable（与上游 InstallerX 的 Route 一致）——
 * miuix-nav 的返回栈 saver 用 kotlinx 多态序列化持久化整个返回栈，基类缺少 @Serializable 时
 * 子类不会被自动注册进多态作用域，应用启动即抛
 * "Serializer for subclass 'X' is not found in the polymorphic scope of 'Route'"。
 */
@Serializable
sealed interface Route : NavKey, Parcelable {

    /** 主界面（含 首页/门锁/开门/设置 四个 tab 的 Pager）。 */
    @Parcelize
    @Serializable
    data object Main : Route

    /** 登录页（首次进入 / 切换账号）。 */
    @Parcelize
    @Serializable
    data object Login : Route

    /** 主题设置二级页（模板 ColorPalette 同款）。 */
    @Parcelize
    @Serializable
    data object ThemeSettings : Route

    /** 关于页（模板 About 同款）。 */
    @Parcelize
    @Serializable
    data object About : Route

    /** 开放源代码许可页（复用 InstallerX Revived 的 License 页）。 */
    @Parcelize
    @Serializable
    data object OpenSourceLicense : Route

    /** 门锁详情。 */
    @Parcelize
    @Serializable
    data class LockDetail(val label: String) : Route
}
