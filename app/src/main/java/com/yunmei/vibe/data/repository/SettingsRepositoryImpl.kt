package com.yunmei.vibe.data.repository

import android.content.Context
import androidx.core.content.edit
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.yunmei.vibe.YunMeiApp
import com.yunmei.vibe.ui.UiMode
import com.yunmei.vibe.ui.animation.predictiveback.PredictiveBackAnimation
import com.yunmei.vibe.ui.animation.predictiveback.PredictiveBackExitDirection

class SettingsRepositoryImpl : SettingsRepository {

    private val prefs by lazy {
        YunMeiApp.app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    }

    override var uiMode: String
        get() = prefs.getString("ui_mode", UiMode.DEFAULT_VALUE) ?: UiMode.DEFAULT_VALUE
        set(value) = prefs.edit { putString("ui_mode", value) }

    override var checkUpdate: Boolean
        get() = prefs.getBoolean("check_update", true)
        set(value) = prefs.edit { putBoolean("check_update", value) }

    override var themeMode: Int
        get() = prefs.getInt("color_mode", 0)
        set(value) = prefs.edit { putInt("color_mode", value) }

    override var miuixMonet: Boolean
        get() = prefs.getBoolean("miuix_monet", false)
        set(value) = prefs.edit { putBoolean("miuix_monet", value) }

    override var amoled: Boolean
        get() = prefs.getBoolean("amoled", false)
        set(value) = prefs.edit { putBoolean("amoled", value) }

    override var keyColor: Int
        get() = prefs.getInt("key_color", 0)
        set(value) = prefs.edit { putInt("key_color", value) }

    override var colorStyle: String
        get() = prefs.getString("color_style", PaletteStyle.TonalSpot.name) ?: PaletteStyle.TonalSpot.name
        set(value) = prefs.edit { putString("color_style", value) }

    override var colorSpec: String
        get() = prefs.getString("color_spec", ColorSpec.SpecVersion.Default.name) ?: ColorSpec.SpecVersion.Default.name
        set(value) = prefs.edit { putString("color_spec", value) }

    override var enablePredictiveBack: Boolean
        get() = prefs.getBoolean("enable_predictive_back", false)
        set(value) = prefs.edit { putBoolean("enable_predictive_back", value) }

    /** 返回动画档位；取值与 InstallerX Revived 一致（aosp / miuix / scale / ksu_classic），默认 aosp。 */
    override var predictiveBackAnimation: String
        get() = prefs.getString("predictive_back_animation", PredictiveBackAnimation.DEFAULT.value)
            ?: PredictiveBackAnimation.DEFAULT.value
        set(value) = prefs.edit { putString("predictive_back_animation", value) }

    /** 返回方向；取值与 InstallerX Revived 一致（follow_gesture / always_right / always_left）。 */
    override var predictiveBackExitDirection: String
        get() = prefs.getString("predictive_back_exit_direction", PredictiveBackExitDirection.DEFAULT.value)
            ?: PredictiveBackExitDirection.DEFAULT.value
        set(value) = prefs.edit { putString("predictive_back_exit_direction", value) }

    override var enableBlur: Boolean
        get() = prefs.getBoolean("enable_blur", true)
        set(value) = prefs.edit { putBoolean("enable_blur", value) }

    override var enableFloatingBottomBar: Boolean
        get() = prefs.getBoolean("enable_floating_bottom_bar", true)
        set(value) = prefs.edit { putBoolean("enable_floating_bottom_bar", value) }

    override var enableFloatingBottomBarBlur: Boolean
        get() = prefs.getBoolean("enable_floating_bottom_bar_blur", true)
        set(value) = prefs.edit { putBoolean("enable_floating_bottom_bar_blur", value) }

    override var pageScale: Float
        get() = prefs.getFloat("page_scale", 1.0f)
        set(value) = prefs.edit { putFloat("page_scale", value) }
}
