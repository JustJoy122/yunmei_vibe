package com.yunmei.client.ui.screen.locks

import androidx.compose.runtime.Immutable
import com.yunmei.client.data.model.Lock

@Immutable
data class LocksUiState(
    val locks: List<Lock> = emptyList(),
    val defaultLabel: String? = null,
    val message: String? = null,
)

@Immutable
data class LocksActions(
    val onAddScan: () -> Unit,
    val onAddLogin: () -> Unit,
    val onSetDefault: (Lock) -> Unit,
    val onDelete: (Lock) -> Unit,
    val onOpenDetail: (Lock) -> Unit,
    val onMessageShown: () -> Unit,
)
