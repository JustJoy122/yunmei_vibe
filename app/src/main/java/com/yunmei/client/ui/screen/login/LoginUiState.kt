package com.yunmei.client.ui.screen.login

import androidx.compose.runtime.Immutable
import com.yunmei.client.data.local.StoredUser
import com.yunmei.client.data.model.Lock
import com.yunmei.client.data.model.SchoolEntry

@Immutable
data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val saveCurrent: Boolean = true,
    val savedUsers: List<StoredUser> = emptyList(),
    val loading: Boolean = false,
    val message: String? = null,
    val schoolChoices: List<SchoolEntry>? = null,
    val lockChoices: List<Lock>? = null,
    /** 显示密码明文；每次进入登录页默认关闭（星号显示）。 */
    val showPassword: Boolean = false,
)

@Immutable
data class LoginActions(
    val onUsernameChange: (String) -> Unit,
    val onPasswordChange: (String) -> Unit,
    val onSaveCurrentChange: (Boolean) -> Unit,
    val onLogin: () -> Unit,
    val onPickSavedUser: (StoredUser) -> Unit,
    val onPickSchool: (SchoolEntry) -> Unit,
    val onPickLock: (Lock) -> Unit,
    /** 显示密码开关。 */
    val onToggleShowPassword: (Boolean) -> Unit,
    /** 学校/门锁选择列表「暂不添加」：只保存账号，直接进入主界面。 */
    val onSkipSelection: () -> Unit,
    val onMessageShown: () -> Unit,
)
