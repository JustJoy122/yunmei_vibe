package com.yunmei.client.ui.navigation3

import android.os.Parcelable
import androidx.navigation3.runtime.NavKey
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * Type-safe navigation keys for Navigation3.
 * 每个目的地都是 NavKey（data object / data class），可随 back stack 保存恢复。
 */
sealed interface Route : NavKey, Parcelable {

    /** 主界面（含 首页/门锁/开门/设置 四个 tab 的 Pager）。 */
    @Parcelize
    @Serializable
    data object Main : Route

    /** 登录页（首次进入 / 切换账号）。 */
    @Parcelize
    @Serializable
    data object Login : Route

    /** 扫码添加门锁（结果通过 Navigator.setResult 回传）。 */
    @Parcelize
    @Serializable
    data object Scan : Route

    /** 主题设置二级页（模板 ColorPalette 同款）。 */
    @Parcelize
    @Serializable
    data object ThemeSettings : Route

    /** 关于页（模板 About 同款）。 */
    @Parcelize
    @Serializable
    data object About : Route

    /** 门锁详情。 */
    @Parcelize
    @Serializable
    data class LockDetail(val label: String) : Route
}
