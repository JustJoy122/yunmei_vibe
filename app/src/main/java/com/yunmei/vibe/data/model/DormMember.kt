package com.yunmei.vibe.data.model

import androidx.compose.runtime.Immutable

/**
 * 宿舍成员（官方「我的群组」列表项）。
 *
 * 字段名取自官方前端 `pages/mygroup/mygroup.vue` 与 `pages/manage/dormUser/dormUser.vue`
 * 的渲染取值；**服务端真实返回尚未实测**，因此先按字符串宽松保存，
 * 等真机跑通 `dormuser/getbyloginuser` 后再补类型与必填约束。
 */
@Immutable
data class DormMember(
    /** 成员账号 id；官方用它拼头像地址 `headimg/<userId>.png`。 */
    val userId: String = "",
    /** 真实姓名。 */
    val realName: String = "",
    /** 昵称/备注。 */
    val nickName: String = "",
    /** 角色：0 = 管理员（宿管），1 = 普通成员。 */
    val roleType: Int = ROLE_MEMBER,
    /** 状态：1 = 启用，0 = 停用。 */
    val status: Int = STATUS_ENABLED,
    /** 有效期开始，格式以服务端为准（形如 yyyy-MM-dd HH:mm:ss）。 */
    val startTime: String = "",
    /** 有效期结束。 */
    val endTime: String = "",
) {
    /** 管理员（宿管）才能管理成员，普通成员只读。 */
    val isAdmin: Boolean get() = roleType == ROLE_ADMIN

    val isEnabled: Boolean get() = status == STATUS_ENABLED

    companion object {
        const val ROLE_ADMIN = 0
        const val ROLE_MEMBER = 1
        const val STATUS_DISABLED = 0
        const val STATUS_ENABLED = 1
    }
}
