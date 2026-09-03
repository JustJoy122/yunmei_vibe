package com.yunmei.client.ui.screen.locks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yunmei.client.YunMeiApp
import com.yunmei.client.data.model.Lock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LocksViewModel : ViewModel() {

    private val container get() = YunMeiApp.app.container

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
        showMessage("${lock.label} 已设为默认门锁")
    }

    fun delete(lock: Lock) {
        container.lockStore.remove(lock.label)
        refresh()
        showMessage("${lock.label} 已删除")
    }

    /** 解析扫码/链接得到的内容并添加门锁（兼容 addlock/、lock_id/、lock_info/、lockInfo/ 前缀）。 */
    fun addLockFromShare(raw: String) {
        val lock = Lock.from(raw)
        if (lock == null) {
            showMessage("无法识别的门锁二维码")
            return
        }
        if (!lock.isUsable) {
            showMessage("门锁数据不完整，无法添加")
            return
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    container.lockStore.add(lock)
                }
                refresh()
                showMessage("${lock.label} 已添加")
            } catch (e: IllegalStateException) {
                showMessage(e.message ?: "添加门锁失败")
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
