package com.yunmei.vibe.ui.screen.dormmembers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yunmei.vibe.R
import com.yunmei.vibe.YunMeiApp
import com.yunmei.vibe.data.model.DormMember
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 群组成员页的三态：加载中 / 成功 / 失败。 */
sealed interface DormMembersUiState {
    data object Loading : DormMembersUiState

    data class Success(val members: List<DormMember>) : DormMembersUiState

    data class Error(val message: String) : DormMembersUiState
}

/**
 * 群组成员页的 ViewModel。
 *
 * 接口按宿舍（学校 + 区 + 楼栋 + 房间）取人，这几个值都保存在本地 [Lock] 里，
 * 所以页面只需要门锁 label 就能定位数据；没有对应门锁时直接给出错误，不发注定失败的请求。
 */
class DormMembersViewModel : ViewModel() {

    private val container = YunMeiApp.app.container

    private val _uiState = MutableStateFlow<DormMembersUiState>(DormMembersUiState.Loading)
    val uiState: StateFlow<DormMembersUiState> = _uiState.asStateFlow()

    /** 已经发起过请求的 label，避免重组时重复拉取。 */
    private var loadedLabel: String? = null

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
        viewModelScope.launch {
            _uiState.value = DormMembersUiState.Loading
            val lock = container.lockStore.getAll().firstOrNull { it.label == label }
            if (lock == null) {
                _uiState.value =
                    DormMembersUiState.Error(YunMeiApp.app.getString(R.string.dorm_members_lock_missing))
                return@launch
            }
            try {
                _uiState.value = DormMembersUiState.Success(container.repository.getDormMembers(lock))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.value = DormMembersUiState.Error(
                    error.message ?: YunMeiApp.app.getString(R.string.dorm_members_failed),
                )
            }
        }
    }
}
