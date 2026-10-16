package com.yunmei.vibe.ui.screen.locks

import androidx.compose.runtime.Immutable
import com.yunmei.vibe.data.local.StoredUser
import com.yunmei.vibe.data.model.Lock

@Immutable
data class LocksUiState(
    val locks: List<Lock> = emptyList(),
    val defaultLabel: String? = null,
    val message: String? = null,
    val accounts: List<StoredUser> = emptyList(),
    /** 已解析、等待用户确认添加的门锁（非空时页面弹确认框）。 */
    val pendingLock: Lock? = null,
)

@Immutable
data class LocksActions(
    val onAddScan: () -> Unit,
    val onLogin: () -> Unit,
    val onRemoveAccount: (StoredUser) -> Unit,
    val onSetDefault: (Lock) -> Unit,
    val onDelete: (Lock) -> Unit,
    val onOpenDetail: (Lock) -> Unit,
    val onMessageShown: () -> Unit,
    /** 确认框：确认添加 / 取消添加（仅清空待确认态，不写库）。 */
    val onConfirmImport: () -> Unit,
    val onCancelImport: () -> Unit,
)
