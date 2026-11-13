package com.yunmei.vibe.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * 本人档案（官方学校服务 `student/getbyloginuser`）。
 *
 * 官方个人中心用它显示姓名/学院/班级；头像不是接口字段，而是服务端按 userId
 * 固定生成的 `headimg/<userId>.png`，所以这里用 [avatarUrl] 拼出地址。
 */
@Serializable
@Immutable
data class UserProfile(
    val userId: String = "",
    val realName: String = "",
    /** 学号：官方学籍接口的 `stuNo`，部分接口里与登录名 `userName` 同值。 */
    val stuNo: String = "",
    val userTel: String = "",
    val schoolNo: String = "",
    val collegeName: String = "",
    val majorName: String = "",
    val schoolYear: String = "",
    val className: String = "",
    val areaName: String = "",
    val buildName: String = "",
    val dormName: String = "",
    /** 床位号，官方首页把它拼在宿舍名后面（`dormName-attr1`）。 */
    val attr1: String = "",
    /** 官方偶尔会下发完整头像地址（本地缓存路径），以 http 开头时优先使用。 */
    val headImg: String = "",
) {
    /**
     * 与一份新档案合并：新值非空才覆盖，空值保留旧值。
     *
     * 学校接口对小号/非学生账号会少给字段（例如只回姓名和手机号），若直接覆盖落盘，
     * 缓存里本来正常的学院/宿舍/床位就会被空串冲掉，离线打开整页空白。
     */
    fun mergeWith(fresh: UserProfile): UserProfile = UserProfile(
        userId = fresh.userId.ifBlank { userId },
        realName = fresh.realName.ifBlank { realName },
        stuNo = fresh.stuNo.ifBlank { stuNo },
        userTel = fresh.userTel.ifBlank { userTel },
        schoolNo = fresh.schoolNo.ifBlank { schoolNo },
        collegeName = fresh.collegeName.ifBlank { collegeName },
        majorName = fresh.majorName.ifBlank { majorName },
        schoolYear = fresh.schoolYear.ifBlank { schoolYear },
        className = fresh.className.ifBlank { className },
        areaName = fresh.areaName.ifBlank { areaName },
        buildName = fresh.buildName.ifBlank { buildName },
        dormName = fresh.dormName.ifBlank { dormName },
        attr1 = fresh.attr1.ifBlank { attr1 },
        headImg = fresh.headImg.ifBlank { headImg },
    )

    /** 头像地址：优先接口给的绝对地址，否则按官方规则用 userId 拼。 */
    val avatarUrl: String
        get() = when {
            headImg.startsWith("http") -> headImg
            userId.isBlank() -> ""
            else -> HEADIMG_BASE + userId + ".png"
        }

    companion object {
        const val HEADIMG_BASE = "https://base.yunmeitech.com/headimg/"
    }
}
