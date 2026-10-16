package com.yunmei.vibe.ui.screen.locks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yunmei.vibe.R
import com.yunmei.vibe.YunMeiApp
import com.yunmei.vibe.data.local.DuplicateLockException
import com.yunmei.vibe.data.local.StoredUser
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
                val accounts = container.accountStore.getAll()
                Triple(locks, defaultLabel, accounts)
            }
            _uiState.update {
                it.copy(
                    locks = data.first,
                    defaultLabel = data.second,
                    accounts = data.third,
                )
            }
        }
    }

    /** 移除已保存账号（与设置页原账号区同一实现：按用户名 MD5 删除）。 */
    fun removeAccount(user: StoredUser) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                container.accountStore.removeByUsernameMd5(user.usernameMd5)
            }
            refresh()
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

    /**
     * 解析扫码/链接得到的内容，**只解析不写库**（兼容 addlock/、lock_id/、lock_info/、lockInfo/ 前缀），
     * 校验通过后把门锁放进 [LocksUiState.pendingLock] 等待用户在确认框里决定。
     */
    fun importFrom(raw: String) {
        val lock = Lock.from(raw)
        if (lock == null) {
            showMessage(str(R.string.scan_invalid))
            return
        }
        if (!lock.isUsable) {
            showMessage(str(R.string.locks_add_incomplete))
            return
        }
        _uiState.update { it.copy(pendingLock = lock) }
    }

    /** 用户在确认框点「添加」：写库 → 刷新 → 清空待确认态，并给出成功/重复/失败提示。 */
    fun confirmImport() {
        val lock = _uiState.value.pendingLock ?: return
        _uiState.update { it.copy(pendingLock = null) }
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

    /** 用户在确认框点「取消」：仅清空待确认态，不写库。 */
    fun cancelImport() {
        _uiState.update { it.copy(pendingLock = null) }
    }

    fun showMessage(message: String) {
        _uiState.update { it.copy(message = message) }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(message = null) }
    }
}
