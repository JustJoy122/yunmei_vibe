package com.yunmei.vibe.ui.animation.predictiveback

import com.yunmei.vibe.R

/**
 * 返回动画档位。
 *
 * 取值字符串与 InstallerX Revived 的
 * `domain/settings/model/preferences/PredictiveBackAnimation.kt` 完全一致（便于与上游对照）。
 *
 * 注意：上游枚举里还有一项 `None`（不启用返回动画）。本项目把该语义交给「预测性返回手势」开关本身
 * （开关关闭 = 不启用返回动画），因此菜单只暴露下面四档，[DEFAULT] 按拍板取 `aosp`
 * （与本项目历史观感一致，避免升级后返回动画突变）。
 */
enum class PredictiveBackAnimation(
    val value: String,
    val labelRes: Int,
) {
    /** AOSP / androidx navigation3 默认预测性返回效果（当前项目原有观感）。 */
    AOSP("aosp", R.string.settings_predictive_back_animation_aosp),

    /** Miuix 导航默认效果（照抄 miuix 上游 NavTransitions.MiuixDefault 的公式）。 */
    MIUIX("miuix", R.string.settings_predictive_back_animation_miuix),

    /** 缩放：被返回页 0.85 → 1 缩放并自退出方向滑入。 */
    SCALE("scale", R.string.settings_predictive_back_animation_scale),

    /** 经典（KSU）：被返回页 0.9 → 1 缩放淡入，当前页向离开方向整屏滑出。 */
    CLASSIC("ksu_classic", R.string.settings_predictive_back_animation_classic),
    ;

    companion object {
        val DEFAULT: PredictiveBackAnimation = AOSP

        fun fromValueOrDefault(value: String): PredictiveBackAnimation =
            entries.find { it.value == value } ?: DEFAULT
    }
}
