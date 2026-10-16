package com.yunmei.vibe.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 深链导入门锁的**可观察暂存槽位**。
 *
 * 由 `ui/locks/AddLockLinkActivity` 在深链唤起时 [post] 原始 URL；`MainActivity` 观察到非空后
 * 切到门锁页；`LocksScreen` 在自己的组合里消费（调用 `LocksViewModel.importFrom` 并 [consume]），
 * 由确认框等待用户决定是否写库。
 *
 * 只保存"待处理的原始文本"，不在此处做任何解析（解析统一在 `Lock.from`）。
 */
object PendingLockImport {

    private val _raw = MutableStateFlow<String?>(null)

    /** 待处理的深链原文（`yunmeiui://lock_info/<Base64>` 等）；null 表示无待处理项。 */
    val raw: StateFlow<String?> = _raw.asStateFlow()

    /** 投递一条深链原文。 */
    fun post(value: String) {
        _raw.value = value
    }

    /** 取出并清空（保证同一次深链只被消费一次）。 */
    fun consume(): String? {
        val value = _raw.value
        _raw.value = null
        return value
    }
}