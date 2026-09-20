package com.yunmei.vibe.ui.theme
import com.yunmei.vibe.data.preferences.SettingsPrefs

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.yunmei.vibe.ui.LocalUiMode
import com.yunmei.vibe.ui.UiMode

enum class ColorMode(val value: Int) {
    SYSTEM(0),
    LIGHT(1),
    DARK(2),
    MONET_SYSTEM(3),
    MONET_LIGHT(4),
    MONET_DARK(5),
    DARK_AMOLED(6);

    companion object {
        fun fromValue(value: Int) = entries.find { it.value == value } ?: SYSTEM
    }

    val isSystem: Boolean get() = value == 0 || value == 3
    val isDark: Boolean get() = value == 2 || value == 5 || value == 6
    val isAmoled: Boolean get() = value == 6
    val isMonet: Boolean get() = value >= 3

    fun toNonMonetMode(): Int = when (this) {
        MONET_SYSTEM -> 0
        MONET_LIGHT -> 1
        MONET_DARK, DARK_AMOLED -> 2
        else -> value
    }

    fun toMonetMode(): Int = when (this) {
        SYSTEM -> 3
        LIGHT -> 4
        DARK -> 5
        else -> value
    }
}

data class AppSettings(
    val colorMode: ColorMode,
    val keyColor: Int,
    val paletteStyle: PaletteStyle,
    val colorSpec: ColorSpec.SpecVersion,
    /** 深色模式下是否使用纯黑背景（AMOLED），独立于深浅色选择的开关。 */
    val amoled: Boolean,
)

/**
 * KernelSU 本体的色板规则（manager/app/src/main/java/me/weishu/kernelsu/ui/theme/Theme.kt）：
 * SPEC_2025 只对部分色板风格成立，其余风格仍按 SPEC_2021 推导，避免与上游取色结果不一致。
 */
val PaletteStyle.supportsSpec2025: Boolean
    get() = this == PaletteStyle.TonalSpot ||
        this == PaletteStyle.Neutral ||
        this == PaletteStyle.Vibrant ||
        this == PaletteStyle.Expressive

fun ColorSpec.SpecVersion.effectiveFor(style: PaletteStyle): ColorSpec.SpecVersion =
    if (this == ColorSpec.SpecVersion.SPEC_2025 && !style.supportsSpec2025) {
        ColorSpec.SpecVersion.SPEC_2021
    } else {
        this
    }

object ThemeController {
    fun getAppSettings(context: Context): AppSettings {
        val prefs = SettingsPrefs.of(context)
        val uiMode = prefs.getString(SettingsPrefs.UI_MODE, UiMode.DEFAULT_VALUE) ?: UiMode.DEFAULT_VALUE
        var colorModeValue = prefs.getInt(SettingsPrefs.COLOR_MODE, ColorMode.SYSTEM.value)

        if (uiMode == "miuix") {
            val miuixMonet = prefs.getBoolean(SettingsPrefs.MIUIX_MONET, false)
            val colorMode = ColorMode.fromValue(colorModeValue)
            colorModeValue = if (!miuixMonet && colorMode.isMonet) {
                colorMode.toNonMonetMode()
            } else if (miuixMonet && !colorMode.isMonet) {
                colorMode.toMonetMode()
            } else {
                colorModeValue
            }
        }

        val colorMode = ColorMode.fromValue(colorModeValue)
        val keyColor = prefs.getInt(SettingsPrefs.KEY_COLOR, 0)
        val paletteStyleStr = prefs.getString(SettingsPrefs.COLOR_STYLE, PaletteStyle.TonalSpot.name)
        val paletteStyle = try {
            PaletteStyle.valueOf(paletteStyleStr!!)
        } catch (_: Exception) {
            PaletteStyle.TonalSpot
        }
        val colorSpecStr = prefs.getString(SettingsPrefs.COLOR_SPEC, ColorSpec.SpecVersion.Default.name)
        val colorSpec = try {
            ColorSpec.SpecVersion.valueOf(colorSpecStr!!)
        } catch (_: Exception) {
            ColorSpec.SpecVersion.Default
        }
        val amoled = prefs.getBoolean(SettingsPrefs.AMOLED, false)

        return AppSettings(colorMode, keyColor, paletteStyle, colorSpec, amoled)
    }
}

@Composable
fun TemplateTheme(
    appSettings: AppSettings? = null,
    uiMode: UiMode = LocalUiMode.current,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val currentAppSettings = appSettings ?: ThemeController.getAppSettings(context)

    when (uiMode) {
        UiMode.Miuix -> MiuixTemplateTheme(
            appSettings = currentAppSettings,
            content = content
        )

        UiMode.Material -> MaterialTemplateTheme(
            appSettings = currentAppSettings,
            content = content
        )
    }
}

@Composable
@ReadOnlyComposable
fun isInDarkTheme(): Boolean {
    return when (LocalColorMode.current) {
        1, 4 -> false  // Force light mode
        2, 5, 6 -> true   // Force dark mode
        else -> isSystemInDarkTheme()  // Follow system (0 or default)
    }
}


val LocalColorMode = staticCompositionLocalOf { 0 }

val LocalEnableBlur = staticCompositionLocalOf { false }

val LocalEnableFloatingBottomBar = staticCompositionLocalOf { false }

val LocalEnableFloatingBottomBarBlur = staticCompositionLocalOf { false }
