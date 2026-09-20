package com.yunmei.vibe.data.repository
import com.yunmei.vibe.data.preferences.SettingsPrefs

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
        SettingsPrefs.of(YunMeiApp.app)
    }

    override var uiMode: String
        get() = prefs.getString(SettingsPrefs.UI_MODE, UiMode.DEFAULT_VALUE) ?: UiMode.DEFAULT_VALUE
        set(value) = prefs.edit { putString(SettingsPrefs.UI_MODE, value) }

    override var checkUpdate: Boolean
        get() = prefs.getBoolean(SettingsPrefs.CHECK_UPDATE, true)
        set(value) = prefs.edit { putBoolean(SettingsPrefs.CHECK_UPDATE, value) }

    override var themeMode: Int
        get() = prefs.getInt(SettingsPrefs.COLOR_MODE, 0)
        set(value) = prefs.edit { putInt(SettingsPrefs.COLOR_MODE, value) }

    override var miuixMonet: Boolean
        get() = prefs.getBoolean("miuix_monet", false)
        set(value) = prefs.edit { putBoolean("miuix_monet", value) }

    override var amoled: Boolean
        get() = prefs.getBoolean(SettingsPrefs.AMOLED, false)
        set(value) = prefs.edit { putBoolean(SettingsPrefs.AMOLED, value) }

    override var keyColor: Int
        get() = prefs.getInt(SettingsPrefs.KEY_COLOR, 0)
        set(value) = prefs.edit { putInt(SettingsPrefs.KEY_COLOR, value) }

    override var colorStyle: String
        get() = prefs.getString(SettingsPrefs.COLOR_STYLE, PaletteStyle.TonalSpot.name) ?: PaletteStyle.TonalSpot.name
        set(value) = prefs.edit { putString(SettingsPrefs.COLOR_STYLE, value) }

    override var colorSpec: String
        get() = prefs.getString(SettingsPrefs.COLOR_SPEC, ColorSpec.SpecVersion.Default.name) ?: ColorSpec.SpecVersion.Default.name
        set(value) = prefs.edit { putString(SettingsPrefs.COLOR_SPEC, value) }

    override var enablePredictiveBack: Boolean
        get() = prefs.getBoolean(SettingsPrefs.ENABLE_PREDICTIVE_BACK, false)
        set(value) = prefs.edit { putBoolean(SettingsPrefs.ENABLE_PREDICTIVE_BACK, value) }

    /** 返回动画档位；取值与 InstallerX Revived 一致（aosp / miuix / scale / ksu_classic），默认 aosp。 */
    override var predictiveBackAnimation: String
        get() = prefs.getString(SettingsPrefs.PREDICTIVE_BACK_ANIMATION, PredictiveBackAnimation.DEFAULT.value)
            ?: PredictiveBackAnimation.DEFAULT.value
        set(value) = prefs.edit { putString(SettingsPrefs.PREDICTIVE_BACK_ANIMATION, value) }

    /** 返回方向；取值与 InstallerX Revived 一致（follow_gesture / always_right / always_left）。 */
    override var predictiveBackExitDirection: String
        get() = prefs.getString(SettingsPrefs.PREDICTIVE_BACK_EXIT_DIRECTION, PredictiveBackExitDirection.DEFAULT.value)
            ?: PredictiveBackExitDirection.DEFAULT.value
        set(value) = prefs.edit { putString(SettingsPrefs.PREDICTIVE_BACK_EXIT_DIRECTION, value) }

    override var enableBlur: Boolean
        get() = prefs.getBoolean(SettingsPrefs.ENABLE_BLUR, true)
        set(value) = prefs.edit { putBoolean(SettingsPrefs.ENABLE_BLUR, value) }

    override var enableFloatingBottomBar: Boolean
        get() = prefs.getBoolean("enable_floating_bottom_bar", true)
        set(value) = prefs.edit { putBoolean("enable_floating_bottom_bar", value) }

    override var enableFloatingBottomBarBlur: Boolean
        get() = prefs.getBoolean("enable_floating_bottom_bar_blur", true)
        set(value) = prefs.edit { putBoolean("enable_floating_bottom_bar_blur", value) }

    override var pageScale: Float
        get() = prefs.getFloat(SettingsPrefs.PAGE_SCALE, 1.0f)
        set(value) = prefs.edit { putFloat(SettingsPrefs.PAGE_SCALE, value) }
}
