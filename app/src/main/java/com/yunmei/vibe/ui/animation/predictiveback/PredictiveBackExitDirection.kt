package com.yunmei.vibe.ui.animation.predictiveback

import com.yunmei.vibe.R

/**
 * 返回方向（退出方向）。
 *
 * 取值字符串与 InstallerX Revived 的
 * `domain/settings/model/preferences/PredictiveBackExitDirection.kt` 完全一致；默认 [ALWAYS_RIGHT] 亦与上游一致。
 * 上游只在「缩放」档使用该选项，本项目同样只在缩放动画里读取它。
 */
enum class PredictiveBackExitDirection(
    val value: String,
    val labelRes: Int,
) {
    /** 跟随手势方向（例如从左边沿滑动 → 页面向右退出）。 */
    FOLLOW_GESTURE("follow_gesture", R.string.settings_predictive_back_direction_follow_gesture),

    /** 始终向右退出。 */
    ALWAYS_RIGHT("always_right", R.string.settings_predictive_back_direction_always_right),

    /** 始终向左退出。 */
    ALWAYS_LEFT("always_left", R.string.settings_predictive_back_direction_always_left),
    ;

    companion object {
        val DEFAULT: PredictiveBackExitDirection = ALWAYS_RIGHT

        fun fromValueOrDefault(value: String): PredictiveBackExitDirection =
            entries.find { it.value == value } ?: DEFAULT
    }
}
