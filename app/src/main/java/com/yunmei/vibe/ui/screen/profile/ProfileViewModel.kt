package com.yunmei.vibe.ui.screen.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yunmei.vibe.R
import com.yunmei.vibe.YunMeiApp
import com.yunmei.vibe.data.local.StoredUser
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 个人中心 ViewModel。
 *
 * 页面同时要三份数据：本人档案（学校服务）、当前会话（姓名/手机号兜底）、本机账号列表。
 * 三者来源不同、失败互不影响：档案失败只显示提示条，账号列表照常可用。
 */
class ProfileViewModel : ViewModel() {

    private val container = YunMeiApp.app.container

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private var loaded = false

    private companion object {
        const val TAG = "YunMeiProfile"
    }

    fun load() {
        if (loaded) return
        loaded = true
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val accounts = withContext(Dispatchers.IO) { container.accountStore.getAll() }
            val defaultLock = withContext(Dispatchers.IO) { container.lockStore.getDefault() }
            // 档案接口本身会确保登录态（必要时静默重登）；先请求它，再读会话缓存，
            // 否则「会话还没建立」的时刻读到的是空 userId，头像地址就拼不出来。
            val profile = try {
                container.repository.getMyProfile()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { it.copy(error = error.message) }
                null
            }
            val session = runCatching { container.repository.cachedSession() }.getOrNull()
            Log.i(
                TAG,
                "profile loaded=" + (profile != null) +
                    " id=" + (profile?.userId ?: "-") +
                    " sessionUserId=" + (session?.userId ?: "-"),
            )
            _uiState.update {
                it.copy(
                    accounts = accounts,
                    currentUsernameMd5 = container.repository.currentUsernameMd5.orEmpty(),
                    cachedName = session?.realName.orEmpty(),
                    cachedTel = session?.userTel.orEmpty(),
                    cachedUserId = session?.userId.orEmpty(),
                    defaultLock = defaultLock,
                    profile = profile ?: it.profile,
                    loading = false,
                )
            }
            val signStatus = defaultLock?.let {
                runCatching { container.repository.getSignStatus(it) }.getOrNull()
            }
            _uiState.update { it.copy(signStatus = signStatus) }
        }
    }

    /** 切换账号：重新登录 → 同步门锁快照 → 重新拉档案。 */
    fun switchAccount(account: StoredUser) {
        if (account.usernameMd5 == _uiState.value.currentUsernameMd5) return
        viewModelScope.launch {
            _uiState.update { it.copy(switching = true, error = null) }
            try {
                container.repository.switchAccount(account)
                syncLocks()
                _uiState.update { it.copy(switching = false) }
                refresh()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        switching = false,
                        error = YunMeiApp.app.getString(
                            R.string.profile_switch_failed,
                            error.message.orEmpty(),
                        ),
                    )
                }
            }
        }
    }

    /**
     * 删除账号。
     *
     * 删的是当前账号时要换一个会话继续用（否则界面会停在「已登录但没有 token」的状态）；
     * 删到没有账号就清空会话，页面回到未登录样式。
     */
    fun removeAccount(account: StoredUser) {
        viewModelScope.launch {
            val wasCurrent = account.usernameMd5 == _uiState.value.currentUsernameMd5
            withContext(Dispatchers.IO) { container.accountStore.removeByUsernameMd5(account.usernameMd5) }
            val left = withContext(Dispatchers.IO) { container.accountStore.getAll() }
            when {
                left.isEmpty() -> container.repository.clearSession()
                wasCurrent -> runCatching { container.repository.switchAccount(left.first()) }
                    .onFailure { error -> Log.i(TAG, "switch after remove failed: " + error.message) }
            }
            syncLocks()
            refresh()
        }
    }

    /**
     * 切号后把门锁快照刷一遍。
     *
     * 门锁是按账号/单位下发的，不同账号可能看到不同的锁；这里只更新本地已有的锁，
     * 不擅自新增（新增门锁仍走扫码/粘贴导入或登录流程）。
     */
    private suspend fun syncLocks() {
        val anyLock = withContext(Dispatchers.IO) { container.lockStore.getAll().firstOrNull() } ?: return
        if (anyLock.schoolNo.isBlank()) return
        val account = withContext(Dispatchers.IO) { container.accountStore.getAll().firstOrNull() } ?: return
        runCatching {
            container.repository.locksOf(anyLock.schoolNo, account)?.forEach { fresh ->
                container.lockStore.updateFromServer(fresh)
            }
        }
    }
}
