package com.yunmei.vibe.ui.screen.locks

import androidx.compose.runtime.Immutable
import com.yunmei.vibe.data.local.StoredUser
import com.yunmei.vibe.data.model.Lock
import com.yunmei.vibe.data.model.UserProfile

@Immutable
data class LocksUiState(
    val locks: List<Lock> = emptyList(),
    val defaultLabel: String? = null,
    val message: String? = null,
    val accounts: List<StoredUser> = emptyList(),
    /** 本人档案快照：账号入口卡用它显示头像、姓名与学号。 */
    val profile: UserProfile? = null,
    /** 已解析、等待用户确认添加的门锁（非空时页面弹确认框）。 */
    val pendingLock: Lock? = null,
)

@Immutable
data class LocksActions(
    val onAddScan: () -> Unit,
    /** 粘贴剪贴板中的分享链接导入（与扫码同级的一级入口）。 */
    val onPasteLink: () -> Unit,
    val onLogin: () -> Unit,
    val onSetDefault: (Lock) -> Unit,
    val onDelete: (Lock) -> Unit,
    val onOpenDetail: (Lock) -> Unit,
    /** 打开群组成员页（卡片底部按钮，页面本体另行实现）。 */
    val onOpenMembers: (Lock) -> Unit,
    /** 打开开门记录页（服务端按账号聚合，无需传入门锁）。 */
    val onOpenRecords: () -> Unit,
    /** 打开个人中心（账号列表、切换与删除都在那一页）。 */
    val onOpenProfile: () -> Unit,
    val onMessageShown: () -> Unit,
    /** 确认框：确认添加 / 取消添加（仅清空待确认态，不写库）。 */
    val onConfirmImport: () -> Unit,
    val onCancelImport: () -> Unit,
)
