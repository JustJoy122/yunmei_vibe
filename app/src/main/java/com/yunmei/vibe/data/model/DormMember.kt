package com.yunmei.vibe.data.model

import androidx.compose.runtime.Immutable

/**
 * 宿舍成员（官方「我的群组」列表项，接口 `dormuser/getbyloginuser`）。
 *
 * 字段名取自真机抓到的真实返回（`dorm_debug.txt`）：顶层是成员关系（角色、状态、有效期、
 * 宿舍信息），人员信息嵌在 `loginUserInfo` 对象里。官方用它拼头像地址
 * `https://base.yunmeitech.com/headimg/<userId>.png`，所以 [userId] 必须保留原始字符串。
 */
@Immutable
data class DormMember(
    /** 成员账号 id；官方用它拼头像地址 `headimg/<userId>.png`。 */
    val userId: String = "",
    /** 角色：0 = 管理员（宿管），1 = 普通成员。 */
    val roleType: Int = ROLE_MEMBER,
    /** 状态：1 = 启用，0 = 停用。 */
    val status: Int = STATUS_ENABLED,
    /** 有效期开始，服务端格式形如 `2021-08-17 12:26:21`。 */
    val startTime: String = "",
    /** 有效期结束。 */
    val endTime: String = "",
    /** 学校名（弹窗里展示）。 */
    val schoolName: String = "",
    val areaName: String = "",
    val buildName: String = "",
    val dormName: String = "",
    /**
     * 学院名称。成员接口 `dormuser/getbyloginuser` 不返回这一项，
     * 官方是在「个人资料」页用 `student/getbyloginuser` 取到 `collegeName`；
     * 因此只有当前登录账号自己那一条能补上，其余成员为 null。
     */
    val college: String? = null,
    /** 班级名称，来源与 [college] 相同。 */
    val className: String? = null,
    /** 人员信息，接口里是嵌套对象，缺字段时退化为空串。 */
    val loginUserInfo: LoginUserInfo = LoginUserInfo(),
) {
    /** 真实姓名优先，其次昵称，再次手机号，最后退回 userId。 */
    val displayName: String
        get() = loginUserInfo.realName
            .ifBlank { loginUserInfo.nickName }
            .ifBlank { loginUserInfo.userTel }
            .ifBlank { userId }

    /** 手机号（人员信息里带，列表与弹窗都直接用）。 */
    val userTel: String get() = loginUserInfo.userTel

    /** 头像加载失败时的首字，取 [displayName] 的第一个字符。 */
    val initial: String get() = displayName.take(1).ifBlank { "?" }

    /** 官方头像地址；`userId` 为空时不请求。 */
    val avatarUrl: String
        get() = if (userId.isBlank()) "" else HEADIMG_BASE + userId + ".png"

    /** 管理员（宿管）才能管理成员，普通成员只读。 */
    val isAdmin: Boolean get() = roleType == ROLE_ADMIN

    val isEnabled: Boolean get() = status == STATUS_ENABLED

    /** 弹窗里的一行宿舍地址：学校 / 区 / 楼栋 / 房间，全空时返回空串。 */
    val location: String
        get() = listOf(schoolName, areaName, buildName, dormName)
            .filter { it.isNotBlank() }
            .joinToString(" · ")

    companion object {
        const val ROLE_ADMIN = 0
        const val ROLE_MEMBER = 1
        const val STATUS_DISABLED = 0
        const val STATUS_ENABLED = 1

        /** 头像服务固定在基础域，与学校服务域无关（官方 JS 里是硬编码字符串拼接）。 */
        private const val HEADIMG_BASE = "https://base.yunmeitech.com/headimg/"
    }
}

/** 成员的人员信息（官方返回里的 `loginUserInfo` 对象）。 */
@Immutable
data class LoginUserInfo(
    val id: String = "",
    val userId: String = "",
    val userTel: String = "",
    val nickName: String = "",
    val realName: String = "",
    val email: String = "",
    val userName: String = "",
)
