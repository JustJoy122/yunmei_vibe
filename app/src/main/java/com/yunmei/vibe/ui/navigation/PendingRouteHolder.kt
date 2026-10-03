package com.yunmei.vibe.ui.navigation

/**
 * 跨 Activity 重建保存"重建前所在路由"的进程级持有者。
 *
 * 使用场景只有一处：主题设置页的预测性返回开关需要 `recreate()` 才能让
 * `setEnableOnBackInvokedCallback` 生效，重建前把当前路由记在这里，
 * 重建后由 MainActivity 取出并还原，避免用户被甩回首页。
 *
 * 刻意不使用 ViewModel/SavedStateHandle：在导航条目内解析 ViewModel
 * 会受 ViewModelStoreOwner 作用域影响（曾导致进入主题设置页即崩溃），
 * 而进程级持有者在 Activity 重建中天然存活，且不参与常规导航。
 */
object PendingRouteHolder {

    @Volatile
    var route: Route? = null

    /** 取出并清除（只消费一次）。 */
    fun consume(): Route? {
        val pending = route
        route = null
        return pending
    }
}