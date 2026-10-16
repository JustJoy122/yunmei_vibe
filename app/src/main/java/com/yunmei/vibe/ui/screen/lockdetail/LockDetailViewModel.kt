package com.yunmei.vibe.ui.screen.lockdetail

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yunmei.vibe.YunMeiApp
import com.yunmei.vibe.data.model.Lock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 门锁详情页状态。
 *
 * [loaded] 用于区分「尚未读取」与「读取后确认不存在」：页面只在前者不满足且 [lock] 为空时才返回，
 * 避免页面异步化后首帧就把用户弹回上一页。
 */
@Immutable
data class LockDetailUiState(
    val lock: Lock? = null,
    val isDefault: Boolean = false,
    val accountSaved: Boolean = false,
    val loaded: Boolean = false,
)

/**
 * 门锁详情页 ViewModel：把原先散在 Composable 里的 LockStore / AccountStore 读取收敛到此处。
 *
 * 刷新时机与原实现保持一致（页面进入 + 每次回到前台由 LifecycleResumeEffect 调用 [refresh]）；
 * 所有存储读取都在 IO 线程执行，组合期不再触碰数据层。
 */
class LockDetailViewModel : ViewModel() {

    private val container get() = YunMeiApp.app.container

    private val _uiState = MutableStateFlow(LockDetailUiState())
    val uiState: StateFlow<LockDetailUiState> = _uiState.asStateFlow()

    private var label: String = ""

    /** 按 label 读取当前锁 / 是否默认 / 账号是否已保存。 */
    fun refresh(label: String) {
        this.label = label
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                val lock = container.lockStore.getAll().firstOrNull { it.label == label }
                val isDefault = container.lockStore.getDefault()?.label == lock?.label
                val saved = lock?.let { container.accountStore.getByUsernameMd5(it.usernameMd5) != null } ?: false
                Triple(lock, isDefault, saved)
            }
            _uiState.update {
                it.copy(
                    lock = snapshot.first,
                    isDefault = snapshot.second,
                    accountSaved = snapshot.third,
                    loaded = true,
                )
            }
        }
    }

    /** 设为默认 / 取消默认（IO 完成后刷新默认态）。 */
    fun setDefault(value: Boolean) {
        viewModelScope.launch {
            val current = _uiState.value.lock ?: return@launch
            withContext(Dispatchers.IO) {
                container.lockStore.setDefault(if (value) current else null)
            }
            refresh(label)
        }
    }

    /**
     * 删除当前门锁。刻意做成 suspend：由页面在"确认后"的同一协程里调用，
     * 与改动前的执行时机一致（删除完成后页面才 pop），避免 VM 随页面销毁而取消删除。
     */
    suspend fun removeLock() {
        val current = _uiState.value.lock ?: return
        withContext(Dispatchers.IO) { container.lockStore.remove(current.label) }
    }
}