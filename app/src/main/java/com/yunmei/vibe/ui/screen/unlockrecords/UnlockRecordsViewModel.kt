package com.yunmei.vibe.ui.screen.unlockrecords

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yunmei.vibe.R
import com.yunmei.vibe.YunMeiApp
import retrofit2.HttpException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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

    /** 正在进行的请求；用它判重，避免重复点击并发拉取。 */
    private var refreshJob: Job? = null
    private val _uiState = MutableStateFlow(UnlockRecordsUiState())
    val uiState: StateFlow<UnlockRecordsUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { it.copy(recordUpload = container.settingsRepository.enableRecordUpload) }
        refresh()
    }

    /**
     * 拉取记录。
     *
     * 这里用「上一次请求是否还在跑」做并发保护，而不是看 `isLoading`：
     * `isLoading` 的初值就是 true（首帧先显示转圈），拿它做早退条件会让 init 里的
     * 第一次 refresh 直接返回，页面永远停在加载中、一条请求都不发。
     */
    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val lock = container.lockStore.getDefault() ?: container.lockStore.getAll().firstOrNull()
            if (lock == null) {
                _uiState.update {
                    it.copy(isLoading = false, records = emptyList())
                }
                return@launch
            }
            try {
                val records = container.repository.getUnlockRecords(lock)
                _uiState.update { it.copy(isLoading = false, records = records, errorMessage = null) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = describe(error))
                }
            }
        }
    }

    /**
     * 把异常翻译成能直接给用户看的一句话。
     *
     * HTTP 失败给出状态码（401 未登录 / 403 无权限 / 404 接口不存在，都是排查时的关键线索），
     * 网络异常单独提示，其余保留原始 message；都没有时退回通用文案。
     */
    private fun describe(error: Exception): String = when (error) {
        is HttpException -> "HTTP " + error.code() + " " + error.message()
        is IOException -> "网络请求失败：" + (error.message ?: "")
        else -> error.message ?: YunMeiApp.app.getString(R.string.unlock_records_failed_generic)
    }

    /** 上传开关：与设置页读写同一个偏好，关闭后 UnlockRecordReporter 会直接跳过上报。 */
    fun setRecordUpload(enabled: Boolean) {
        container.settingsRepository.enableRecordUpload = enabled
        _uiState.update { it.copy(recordUpload = enabled) }
    }
}
