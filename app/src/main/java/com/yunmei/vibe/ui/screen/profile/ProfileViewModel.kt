package com.yunmei.vibe.ui.screen.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yunmei.vibe.R
import com.yunmei.vibe.YunMeiApp
import com.yunmei.vibe.data.local.StoredUser
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    /** 正在进行的刷新任务：切号或重试时先取消，避免旧请求占着连接。 */
    private var refreshJob: Job? = null

    /** 会话纪元：每次 refresh 自增，用来丢弃迟到的旧结果（取消点是协作式的）。 */
    private var sessionEpoch = 0

    private companion object {
        const val TAG = "YunMeiProfile"
    }

    fun load() {
        if (loaded) return
        loaded = true
        refresh()
    }

    /**
     * 加载/刷新页面数据：两段式（缓存优先 + 静默刷新）。
     *
     * 1. 先读本机缓存，命中就立刻上屏（`loading = false`），避免每次进页面都白屏等网络；
     * 2. 再请求本人档案，成功后与缓存做非空合并，失败也只留一条提示，不清空已有内容。
     *
     * 并发：切号或用户点重试都会重新走 [refresh]，旧请求可能晚到。这里同时用「取消旧任务」
     * 和 [sessionEpoch] 双重拦截——前者省流量，后者保证即使旧任务已经跑过取消点，
     * 它的结果也不会写进新账号的界面。
     */
    fun refresh() {
        val epoch = ++sessionEpoch
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            // ---- 第一步：缓存优先 ----
            val cached = runCatching { container.repository.cachedProfile() }.getOrNull()
            if (epoch != sessionEpoch) return@launch
            if (cached != null) {
                _uiState.update { it.copy(profile = cached, loading = false) }
            } else {
                _uiState.update { it.copy(loading = true) }
            }

            val accounts = withContext(Dispatchers.IO) { container.accountStore.getAll() }
            val defaultLock = withContext(Dispatchers.IO) { container.lockStore.getDefault() }
            val session = runCatching { container.repository.cachedSession() }.getOrNull()
            if (epoch != sessionEpoch) return@launch
            _uiState.update {
                it.copy(
                    accounts = accounts,
                    currentUsernameMd5 = container.repository.currentUsernameMd5.orEmpty(),
                    // 切号后必须连会话里的姓名/手机号一起换，否则会和档案对不上。
                    cachedName = session?.realName.orEmpty(),
                    cachedTel = session?.userTel.orEmpty(),
                    cachedUserId = session?.userId.orEmpty(),
                    defaultLock = defaultLock,
                )
            }

            // ---- 第二步：静默刷新 ----
            var softError: String? = null
            val fresh = try {
                container.repository.getMyProfile()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // 网络失败属可恢复：有缓存就一直用缓存，只留提示，不把页面变成错误页。
                softError = error.message ?: YunMeiApp.app.getString(R.string.profile_load_failed)
                null
            }
            if (epoch != sessionEpoch) return@launch
            Log.i(
                TAG,
                "profile refresh epoch=" + epoch +
                    " fresh=" + (fresh != null) +
                    " cached=" + (cached != null) +
                    " id=" + (fresh?.userId ?: "-"),
            )
            _uiState.update {
                val merged = fresh?.let { current -> it.profile?.mergeWith(current) ?: current }
                it.copy(
                    profile = merged ?: it.profile,
                    loading = false,
                    error = when {
                        softError != null -> softError
                        merged == null && it.profile == null ->
                            YunMeiApp.app.getString(R.string.profile_load_failed)
                        else -> null
                    },
                )
            }

            val targetLock = _uiState.value.defaultLock
            val signStatus = targetLock?.let {
                runCatching { container.repository.getSignStatus(it) }.getOrNull()
            }
            if (epoch != sessionEpoch) return@launch
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
