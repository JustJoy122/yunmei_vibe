package com.yunmei.vibe.ui.screen.unlockrecords

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yunmei.vibe.R
import com.yunmei.vibe.YunMeiApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 开门记录页的 ViewModel。
 *
 * 服务端按账号返回记录（不按门锁过滤），所以只需要任意一把本地门锁来定位学校；
 * 没有门锁时说明还没登录/还没拉过门锁，直接给空列表而不是发一个注定失败的请求。
 */
class UnlockRecordsViewModel : ViewModel() {

    private val container = YunMeiApp.app.container
    private val _uiState = MutableStateFlow(UnlockRecordsUiState())
    val uiState: StateFlow<UnlockRecordsUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { it.copy(recordUpload = container.settingsRepository.enableRecordUpload) }
        refresh()
    }

    fun refresh() {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val lock = container.lockStore.getDefault() ?: container.lockStore.getAll().firstOrNull()
            if (lock == null) {
                _uiState.update {
                    it.copy(isLoading = false, records = emptyList())
                }
                return@launch
            }
            try {
                val records = container.repository.getUnlockRecords(lock)
                _uiState.update { it.copy(isLoading = false, records = records, error = null) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = error.message
                            ?: YunMeiApp.app.getString(R.string.unlock_records_failed_generic),
                    )
                }
            }
        }
    }

    /** 上传开关：与设置页读写同一个偏好，关闭后 UnlockRecordReporter 会直接跳过上报。 */
    fun setRecordUpload(enabled: Boolean) {
        container.settingsRepository.enableRecordUpload = enabled
        _uiState.update { it.copy(recordUpload = enabled) }
    }
}
