package com.yunmei.vibe.ui.theme

import android.annotation.SuppressLint
import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat
import com.materialkolor.dynamiccolor.ColorSpec
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

@SuppressLint("NewApi")
@Composable
fun MiuixTemplateTheme(
    appSettings: AppSettings,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDarkTheme = isSystemInDarkTheme()
    val darkTheme = appSettings.colorMode.isDark || (appSettings.colorMode.isSystem && systemDarkTheme)
    val colorStyle = appSettings.paletteStyle
    val colorSpec = appSettings.colorSpec

    val miuixPaletteStyle = try {
        ThemePaletteStyle.valueOf(colorStyle.name)
    } catch (_: Exception) {
        ThemePaletteStyle.TonalSpot
    }

    val miuixColorSpec = if (colorSpec.effectiveFor(colorStyle) == ColorSpec.SpecVersion.SPEC_2025) {
        ThemeColorSpec.Spec2025
    } else {
        ThemeColorSpec.Spec2021
    }

    // 强调色优先：只要用户选了自定义强调色（keyColor != 0），无论莫奈开关是否开启、
    // 无论系统版本如何，都把该颜色作为种子色传给 ThemeController，覆盖 Miuix 自带默认种子。
    // 没有手选色且开启了莫奈时才读系统动态取色；低版本（< Android 12）没有动态取色能力，
    // 此时回落到静态色板（keyColor = null）。
    val resolvedKeyColor: Color? = when {
        appSettings.keyColor != 0 -> Color(appSettings.keyColor)
        appSettings.colorMode.isMonet && isDynamicColorSupported ->
            dynamicBaseColorScheme(context, darkTheme).primary
        else -> null
    }

    val controller = remember(
        appSettings.colorMode,
        appSettings.keyColor,
        darkTheme,
        miuixPaletteStyle,
        miuixColorSpec,
    ) {
        ThemeController(
            when {
                // 低版本没有系统动态取色资源：勾了莫奈又没有手选强调色时回落到普通深浅色（静态色板），
                // 避免 Miuix 的 Monet 模式去读不存在的系统动态色资源；手选了强调色则保留莫奈模式。
                appSettings.colorMode.isMonet && !isDynamicColorSupported && appSettings.keyColor == 0 ->
                    if (darkTheme) ColorSchemeMode.Dark else ColorSchemeMode.Light
                appSettings.colorMode == ColorMode.SYSTEM -> ColorSchemeMode.System
                appSettings.colorMode == ColorMode.LIGHT -> ColorSchemeMode.Light
                appSettings.colorMode == ColorMode.DARK -> ColorSchemeMode.Dark
                appSettings.colorMode == ColorMode.MONET_SYSTEM -> ColorSchemeMode.MonetSystem
                appSettings.colorMode == ColorMode.MONET_LIGHT -> ColorSchemeMode.MonetLight
                else -> ColorSchemeMode.MonetDark
            },
            keyColor = resolvedKeyColor,
            isDark = darkTheme,
            paletteStyle = miuixPaletteStyle,
            colorSpec = miuixColorSpec,
        )
    }

    MiuixTheme(
        controller = controller,
        content = {
            LaunchedEffect(darkTheme) {
                val window = (context as? Activity)?.window ?: return@LaunchedEffect
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            CompositionLocalProvider(
                LocalContentColor provides MiuixTheme.colorScheme.onBackground,
            ) {
                content()
            }
        }
    )
}
