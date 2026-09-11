package com.yunmei.vibe.ui.screen.locks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yunmei.vibe.R
import com.yunmei.vibe.YunMeiApp
import com.yunmei.vibe.data.local.DuplicateLockException
import com.yunmei.vibe.data.model.Lock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LocksViewModel : ViewModel() {

    private val container get() = YunMeiApp.app.container

    private fun str(resId: Int): String = YunMeiApp.app.getString(resId)

    private fun str(resId: Int, vararg args: Any): String = YunMeiApp.app.getString(resId, *args)

    private val _uiState = MutableStateFlow(LocksUiState())
    val uiState: StateFlow<LocksUiState> = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            // 存储读取移到 IO 线程，避免页面首次组合被主线程解密阻塞。
            val data = withContext(Dispatchers.IO) {
                val locks = container.lockStore.getAll()
                val defaultLabel = container.lockStore.getDefault()?.label
                locks to defaultLabel
            }
            _uiState.update {
                it.copy(
                    locks = data.first,
                    defaultLabel = data.second,
                )
            }
        }
    }

    fun setDefault(lock: Lock) {
        container.lockStore.setDefault(lock)
        refresh()
        showMessage(str(R.string.locks_default_set, lock.label))
    }

    fun delete(lock: Lock) {
        container.lockStore.remove(lock.label)
        refresh()
        showMessage(str(R.string.locks_deleted, lock.label))
    }

    /** 解析扫码/链接得到的内容并添加门锁（兼容 addlock/、lock_id/、lock_info/、lockInfo/ 前缀）。 */
    fun addLockFromShare(raw: String) {
        val lock = Lock.from(raw)
        if (lock == null) {
            showMessage(str(R.string.scan_invalid))
            return
        }
        if (!lock.isUsable) {
            showMessage(str(R.string.locks_add_incomplete))
            return
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    container.lockStore.add(lock)
                }
                refresh()
                showMessage(str(R.string.locks_added, lock.label))
            } catch (_: DuplicateLockException) {
                showMessage(str(R.string.locks_duplicate))
            } catch (_: IllegalStateException) {
                showMessage(str(R.string.locks_add_failed))
            }
        }
    }

    fun showMessage(message: String) {
        _uiState.update { it.copy(message = message) }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(message = null) }
    }
}
