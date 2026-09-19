package com.yunmei.vibe.data.preferences

import android.content.Context
import android.content.SharedPreferences

/**
 * 全局设置偏好（SharedPreferences 文件 `settings`）的**唯一访问入口**。
 *
 * 背景：此前有四处各自 `getSharedPreferences("settings", …)` 并各自手写键名字符串——
 * `data/repository/SettingsRepositoryImpl.kt`（读写主体）、`ui/theme/Theme.kt` 的 ThemeController、
 * `ui/viewmodel/MainActivityViewModel.kt` 的观察键列表、`YunMeiApp.kt` 的启动读取。
 * 任何一处键名写错都会表现为"改了没生效 / 读了旧值"，因此集中文件访问与键名常量。
 *
 * **不改任何键名、不做值迁移**：常量值与原字面量逐字一致，已保存的偏好不受影响。
 */
object SettingsPrefs {

    private const val FILE_NAME = "settings"

    const val UI_MODE = "ui_mode"
    const val CHECK_UPDATE = "check_update"
    const val COLOR_MODE = "color_mode"
    const val AMOLED = "amoled"
    const val KEY_COLOR = "key_color"
    const val COLOR_STYLE = "color_style"
    const val COLOR_SPEC = "color_spec"
    const val ENABLE_PREDICTIVE_BACK = "enable_predictive_back"
    const val PREDICTIVE_BACK_ANIMATION = "predictive_back_animation"
    const val PREDICTIVE_BACK_EXIT_DIRECTION = "predictive_back_exit_direction"
    const val ENABLE_BLUR = "enable_blur"
    const val PAGE_SCALE = "page_scale"

    /** Miuix 侧莫奈取色开关。注意与 DataStore 中的 `theme_mode`（AppPreferences）语义不同，勿混用。 */
    const val MIUIX_MONET = "miuix_monet"

    fun of(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
}
