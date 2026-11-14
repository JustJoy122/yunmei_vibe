package com.yunmei.vibe.ui.screen.dormmembers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yunmei.vibe.R
import com.yunmei.vibe.YunMeiApp
import com.yunmei.vibe.data.model.DormMember
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 群组成员页的三态：加载中 / 成功 / 失败。
 *
 * [Success.stale] 表示这份名单来自本地缓存、本次刷新失败了——只在有缓存时才可能为 true，
 * 界面据此在列表上方挂一条「已展示本地记录」的提示，而不是整页变错误。
 */
sealed interface DormMembersUiState {
    data object Loading : DormMembersUiState

    data class Success(val members: List<DormMember>, val stale: Boolean = false) : DormMembersUiState

    data class Error(val message: String) : DormMembersUiState
}

/**
 * 群组成员页的 ViewModel。
 *
 * 接口按宿舍（学校 + 区 + 楼栋 + 房间）取人，这几个值都保存在本地 [Lock] 里，
 * 所以页面只需门锁 label 就能定位数据；没有对应门锁时直接给出错误，不发注定失败的请求。
 *
 * 加载策略是两段式：先出本地缓存，再静默刷新。用户切锁时旧请求会被取消，
 * 并用 [requestId] 兜住「已经跑过取消点、结果还没写回」的迟到响应。
 */
class DormMembersViewModel : ViewModel() {

    private val container = YunMeiApp.app.container

    private val _uiState = MutableStateFlow<DormMembersUiState>(DormMembersUiState.Loading)
    val uiState: StateFlow<DormMembersUiState> = _uiState.asStateFlow()

    /** 已经发起过请求的 label，避免重组时重复拉取。 */
    private var loadedLabel: String? = null

    /** 正在进行的请求：切锁/重试时先取消，省一次无用的网络往返。 */
    private var fetchJob: Job? = null

    /** 请求序号：协程取消是协作式的，用它丢弃「已经拿到响应但还没写回」的旧结果。 */
    private var requestId = 0

    fun load(label: String) {
        if (loadedLabel == label) return
        loadedLabel = label
        fetch(label)
    }

    fun retry() {
        val label = loadedLabel ?: return
        fetch(label)
    }

    private fun fetch(label: String) {
        val request = ++requestId
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            val lock = withContext(Dispatchers.IO) {
                container.lockStore.getAll().firstOrNull { it.label == label }
            }
            if (request != requestId) return@launch
            if (lock == null) {
                _uiState.value = DormMembersUiState.Error(
                    YunMeiApp.app.getString(R.string.dorm_members_lock_missing),
                )
                return@launch
            }

            // ---- 第一步：缓存优先，命中就立刻出名单 ----
            val cached = runCatching { container.repository.cachedDormMembers(lock) }.getOrNull()
            if (request != requestId) return@launch
            if (cached.isNullOrEmpty()) {
                _uiState.value = DormMembersUiState.Loading
            } else {
                _uiState.value = DormMembersUiState.Success(cached, stale = false)
            }

            // ---- 第二步：静默刷新 ----
            try {
                val fresh = container.repository.getDormMembers(lock)
                if (request != requestId) return@launch
                // 内容没变就不重设同一份列表，避免 UI 闪一下。
                val current = _uiState.value
                if (current is DormMembersUiState.Success && current.members == fresh) {
                    if (current.stale) {
                        _uiState.value = DormMembersUiState.Success(fresh, stale = false)
                    }
                } else {
                    _uiState.value = DormMembersUiState.Success(fresh, stale = false)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (request != requestId) return@launch
                val current = _uiState.value
                if (current is DormMembersUiState.Success && current.members.isNotEmpty()) {
                    // 有缓存就把失败降级为「提示刷新失败」，名单继续留在屏幕上。
                    _uiState.value = current.copy(stale = true)
                } else {
                    _uiState.value = DormMembersUiState.Error(
                        error.message ?: YunMeiApp.app.getString(R.string.dorm_members_failed),
                    )
                }
            }
        }
    }
}
