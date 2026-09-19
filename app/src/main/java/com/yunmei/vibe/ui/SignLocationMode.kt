package com.yunmei.vibe.ui

import androidx.annotation.StringRes
import com.yunmei.vibe.R

/**
 * 打卡定位方式。
 *
 * 持久化取值与原项目保持一致（`ask` / `rel` / `lst`）——改动会破坏已保存的偏好，勿改。
 *
 * 原先这套映射散落在四处：首页打卡卡片副标题（mode→中文）、设置页下拉的 index↔value、
 * 以及 Material / Miuix 两个设置页各自的 mode↔index。任何一处顺序或文案不同步都会导致
 * 选中项错位或显示错文案，因此统一到本枚举：
 * 列表顺序由 [entries] 决定、文案由 [labelRes] 决定，全项目仅此一处。
 */
enum class SignLocationMode(
    val value: String,
    @StringRes val labelRes: Int,
) {
    /** 每次询问 */
    ASK("ask", R.string.settings_sign_location_ask),

    /** 重新定位并保存 */
    RELOCATE("rel", R.string.settings_sign_location_relocate),

    /** 使用上次位置 */
    LAST("lst", R.string.settings_sign_location_last),
    ;

    companion object {
        /** 兜底档位（与偏好默认值一致）。 */
        val DEFAULT: SignLocationMode = ASK

        /** 把持久化取值解析成枚举；无法识别时回退 [DEFAULT]，不抛异常。 */
        fun fromValue(value: String): SignLocationMode =
            entries.firstOrNull { it.value == value } ?: DEFAULT
    }
}
