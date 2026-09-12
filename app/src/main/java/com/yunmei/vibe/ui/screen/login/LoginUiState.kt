package com.yunmei.vibe.ui.screen.login

import androidx.compose.runtime.Immutable
import com.yunmei.vibe.data.local.StoredUser
import com.yunmei.vibe.data.model.Lock
import com.yunmei.vibe.data.model.SchoolEntry

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
    /**
     * 是否正在复用已保存账号的凭证。
     * 已保存账号只存密码 MD5（见 [com.yunmei.vibe.data.local.StoredUser]），不能回填进密码输入框，
     * 因此选中已保存账号时输入框留空并置此标记：显示占位提示、禁用「显示密码」开关。
     */
    val usingSavedCredential: Boolean = false,
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
