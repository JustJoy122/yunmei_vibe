package com.yunmei.client.ui.screen.home

import androidx.compose.runtime.Immutable
import com.yunmei.client.data.model.Lock
import com.yunmei.client.data.preferences.AppSettings
import com.yunmei.client.ui.util.SystemInfo

@Immutable
data class HomeUiState(
    val lock: Lock? = null,
    /** 仅在「门锁管理」中显式设置的默认门锁；未设置时为 null（状态 Banner 的绿色判定依据）。 */
    val defaultLock: Lock? = null,
    val lockCount: Int = 0,
    val showSignButton: Boolean = true,
    val showCodeButton: Boolean = true,
    val systemInfo: SystemInfo = SystemInfo(""),
    // 开门（原开门页内容并入首页）
    val progress: Int = 0,
    val statusText: String = "",
    val battery: Int? = null,
    val isOpening: Boolean = false,
    // 获取密码
    val code: String? = null,
    val codeLoading: Boolean = false,
    val codeError: String? = null,
    // 打卡
    val signMessage: String? = null,
    val signing: Boolean = false,
    val signAsk: SignAskState? = null,
    // 开门选项
    val quickConnect: Boolean = true,
    val settings: AppSettings = AppSettings(),
)

@Immutable
data class SignAskState(
    val lastLocation: String,
)

enum class SignAskChoice {
    USE_LAST,
    RELOCATE_SAVE,
    LOCATE_ONLY,
}

@Immutable
data class HomeActions(
    val onOpenDetail: (Lock) -> Unit,
    val onOpenLocks: () -> Unit,
    val onOpenDoor: () -> Unit,
    val onSetQuickConnect: (Boolean) -> Unit,
    val onSetAutoConnect: (Boolean) -> Unit,
    val onSetAutoExit: (Boolean) -> Unit,
    val onSetAutoCode: (Boolean) -> Unit,
    val onGetCode: () -> Unit,
    val onSign: () -> Unit,
    val onResolveSignAsk: (SignAskChoice) -> Unit,
    val onDismissSignAsk: () -> Unit,
)
