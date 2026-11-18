package com.yunmei.vibe.ui.screen.profile

import androidx.compose.runtime.Immutable
import com.yunmei.vibe.data.local.StoredUser
import com.yunmei.vibe.data.model.Lock
import com.yunmei.vibe.data.model.SignStatus
import com.yunmei.vibe.data.model.UserProfile

/**
 * 个人中心状态。
 *
 * [profile] 来自学校服务，断网/失败时为 null，此时用 [cachedName] / [cachedTel]
 * （登录时写进会话的那份）继续显示，避免整页空白。
 */
@Immutable
data class ProfileUiState(
    val loading: Boolean = true,
    val switching: Boolean = false,
    val profile: UserProfile? = null,
    val cachedName: String = "",
    val cachedTel: String = "",
    /** 会话里缓存的 userId：档案接口没给 userId 时用它拼头像地址。 */
    val cachedUserId: String = "",
    val error: String? = null,
    val accounts: List<StoredUser> = emptyList(),
    val currentUsernameMd5: String = "",
    val signStatus: SignStatus? = null,
    val defaultLock: Lock? = null,
    /** 注销请求进行中：界面据此禁用入口并显示进度。 */
    val destroying: Boolean = false,
    /** 注销成功：页面观察该标记跳回登录页。 */
    val destroyed: Boolean = false,
    /** 头像上传中：界面据此禁用入口，避免重复提交。 */
    val uploadingAvatar: Boolean = false,
    /**
     * 头像版本号。
     *
     * 头像是服务端固定路径 `headimg/<userId>.png`，同名文件会被本地内存缓存与磁盘缓存拦住，
     * 所以上传成功后把时间戳拼进 URL，强制换一个缓存键重新下载。
     */
    val avatarVersion: Long = 0L,
) {
    /** 姓名：接口 > 会话缓存 > 账号手机号。 */
    val displayName: String
        get() = profile?.realName?.takeIf { it.isNotBlank() }
            ?: cachedName.ifBlank {
                accounts.firstOrNull { it.usernameMd5 == currentUsernameMd5 }?.username.orEmpty()
            }

    val displayTel: String
        get() = profile?.userTel?.takeIf { it.isNotBlank() } ?: cachedTel

    /** 学院 · 班级：两者都空时返回空串，界面隐藏该行。 */
    val education: String
        get() = listOfNotNull(
            profile?.collegeName?.takeIf { it.isNotBlank() },
            profile?.className?.takeIf { it.isNotBlank() },
        ).joinToString(" · ")

    val dormName: String get() = profile?.dormName.orEmpty()

    /**
     * 头像地址：档案接口的 [UserProfile.avatarUrl] 优先；拿不到 userId 时退回会话缓存，
     * 都没有才返回空串（界面显示姓名首字）。
     */
    val avatarUrl: String
        get() {
            val base = profile?.avatarUrl?.takeIf { it.isNotBlank() }
                ?: cachedUserId.takeIf { it.isNotBlank() }
                    ?.let { UserProfile.HEADIMG_BASE + it + ".png" }
                    .orEmpty()
            return if (base.isBlank() || avatarVersion == 0L) base else base + "?v=" + avatarVersion
        }
}

@Immutable
data class ProfileActions(
    val onBack: () -> Unit,
    /** 跳转登录页添加新账号。 */
    val onAddAccount: () -> Unit,
    val onSwitchAccount: (StoredUser) -> Unit,
    val onRemoveAccount: (StoredUser) -> Unit,
    val onRetry: () -> Unit,
    /** 进入「重设密码」二级页。 */
    val onResetPassword: () -> Unit,
    /** 进入「换绑手机号」二级页。 */
    val onChangePhone: () -> Unit,
    /** 点头像右下角的编辑角标：弹出「拍照 / 从相册选择」。 */
    val onEditAvatar: () -> Unit,
    /** 已确认注销：调用接口并清理本地数据。 */
    val onDestroyAccount: () -> Unit,
)
