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
)
