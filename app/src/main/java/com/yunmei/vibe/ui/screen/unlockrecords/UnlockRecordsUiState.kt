package com.yunmei.vibe.ui.screen.unlockrecords

import androidx.compose.runtime.Immutable
import com.yunmei.vibe.data.model.UnlockRecordItem

/**
 * 开门记录页的状态。
 *
 * 记录由服务端按账号聚合返回（接口不接受门锁参数），因此这里不区分门锁维度，
 * 只关心「拉没拉到」「内容是什么」。
 */
@Immutable
data class UnlockRecordsUiState(
    val isLoading: Boolean = true,
    val records: List<UnlockRecordItem> = emptyList(),
    val error: String? = null,
    /** 与设置项 enableRecordUpload 绑定的开关状态。 */
    val recordUpload: Boolean = true,
)

@Immutable
data class UnlockRecordsActions(
    val onBack: () -> Unit,
    val onRefresh: () -> Unit,
    val onSetRecordUpload: (Boolean) -> Unit,
)
