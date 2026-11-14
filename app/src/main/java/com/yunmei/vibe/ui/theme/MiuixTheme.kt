package com.yunmei.vibe.ui.theme

import android.annotation.SuppressLint
import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

@SuppressLint("NewApi")
@Composable
fun MiuixTemplateTheme(
    appSettings: AppSettings,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDarkTheme = isSystemInDarkTheme()
    val darkTheme = appSettings.colorMode.isDark || (appSettings.colorMode.isSystem && systemDarkTheme)
    // AMOLED：独立的 amoled 开关与历史遗留的 DARK_AMOLED 模式等价；Miuix 侧只压黑中性面族（参考 InstallerX）。
    val amoledMode = darkTheme && (appSettings.amoled || appSettings.colorMode.isAmoled)
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

    // 低版本（< Android 12）没有系统动态取色。Miuix 的 Monet 模式在这类机型上实测不保证
    // 用传入的 keyColor 生成配色（强调色看起来“没生效”），所以这里彻底绕开库内部的莫奈分支：
    // 自己用 material-kolor 从强调色推导明暗两套 MD3 配色，再映射成 Miuix 的 Colors，
    // 通过 lightColors/darkColors 直接覆盖，色彩模式回落到普通 Light/Dark。
    val customSeed = if (!isDynamicColorSupported && appSettings.keyColor != 0) {
        Color(appSettings.keyColor)
    } else {
        null
    }
    val seedLightScheme: ColorScheme? = customSeed?.let { seed ->
        rememberDynamicColorScheme(
            seedColor = seed,
            isDark = false,
            isAmoled = false,
            style = colorStyle,
            specVersion = colorSpec.effectiveFor(colorStyle),
        )
    }
    val seedDarkScheme: ColorScheme? = customSeed?.let { seed ->
        rememberDynamicColorScheme(
            seedColor = seed,
            isDark = true,
            isAmoled = amoledMode,
            style = colorStyle,
            specVersion = colorSpec.effectiveFor(colorStyle),
        )
    }
    val customLightColors = seedLightScheme?.let { scheme ->
        remember(scheme) { lightColorScheme().mappedFrom(scheme) }
    }
    val customDarkColors = seedDarkScheme?.let { scheme ->
        remember(scheme) { darkColorScheme().mappedFrom(scheme) }
    }

    val controller = remember(
        appSettings.colorMode,
        appSettings.keyColor,
        amoledMode,
        darkTheme,
        miuixPaletteStyle,
        miuixColorSpec,
        customLightColors,
        customDarkColors,
    ) {
        ThemeController(
            when {
                // 低版本手选了强调色：用自建色板，不再让库走莫奈分支。
                customLightColors != null && customDarkColors != null ->
                    if (darkTheme) ColorSchemeMode.Dark else ColorSchemeMode.Light
                // 低版本没有系统动态取色资源：勾了莫奈又没有手选强调色时回落到普通深浅色（静态色板），
                // 避免 Miuix 的 Monet 模式去读不存在的系统动态色资源。
                appSettings.colorMode.isMonet && !isDynamicColorSupported && appSettings.keyColor == 0 ->
                    if (darkTheme) ColorSchemeMode.Dark else ColorSchemeMode.Light
                appSettings.colorMode == ColorMode.SYSTEM -> ColorSchemeMode.System
                appSettings.colorMode == ColorMode.LIGHT -> ColorSchemeMode.Light
                appSettings.colorMode == ColorMode.DARK -> ColorSchemeMode.Dark
                appSettings.colorMode == ColorMode.MONET_SYSTEM -> ColorSchemeMode.MonetSystem
                appSettings.colorMode == ColorMode.MONET_LIGHT -> ColorSchemeMode.MonetLight
                else -> ColorSchemeMode.MonetDark
            },
            lightColors = customLightColors ?: lightColorScheme(),
            darkColors = customDarkColors ?: darkColorScheme(),
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
            // AMOLED 深色下把中性面族压黑：Miuix 的 Colors 由 ThemeController 生成，
            // 库内没有 isAmoled 参数，这里照 InstallerX 的做法在外层再套一层 colors 覆盖。
            val colors = MiuixTheme.colorScheme
            MiuixTheme(colors = if (amoledMode) colors.amoledBlackened() else colors) {
                CompositionLocalProvider(
                    LocalContentColor provides MiuixTheme.colorScheme.onBackground,
                ) {
                    content()
                }
            }
        },
    )
}

/**
 * 把 material-kolor 推导出的 MD3 配色映射到 Miuix 的 [Colors]。
 *
 * 只覆盖与品牌色相关的角色，其余（禁用态、滑杆、窗口遮罩等）沿用默认色板的取值，
 * 避免低版本覆盖了强调色却在禁用态/对比度上出现新的问题。
 */
private fun Colors.mappedFrom(colorScheme: ColorScheme): Colors = copy(
    primary = colorScheme.primary,
    onPrimary = colorScheme.onPrimary,
    primaryVariant = colorScheme.primaryContainer,
    onPrimaryVariant = colorScheme.onPrimaryContainer,
    primaryContainer = colorScheme.primaryContainer,
    onPrimaryContainer = colorScheme.onPrimaryContainer,
    secondary = colorScheme.secondary,
    onSecondary = colorScheme.onSecondary,
    secondaryVariant = colorScheme.secondaryContainer,
    onSecondaryVariant = colorScheme.onSecondaryContainer,
    secondaryContainer = colorScheme.secondaryContainer,
    onSecondaryContainer = colorScheme.onSecondaryContainer,
    tertiaryContainer = colorScheme.tertiaryContainer,
    onTertiaryContainer = colorScheme.onTertiaryContainer,
    error = colorScheme.error,
    onError = colorScheme.onError,
    errorContainer = colorScheme.errorContainer,
    onErrorContainer = colorScheme.onErrorContainer,
    background = colorScheme.background,
    onBackground = colorScheme.onBackground,
    onBackgroundVariant = colorScheme.onSurfaceVariant,
    surface = colorScheme.surface,
    onSurface = colorScheme.onSurface,
    surfaceVariant = colorScheme.surfaceVariant,
    onSurfaceSecondary = colorScheme.onSurfaceVariant,
    onSurfaceVariantSummary = colorScheme.onSurfaceVariant,
    onSurfaceVariantActions = colorScheme.onSurfaceVariant,
    surfaceContainer = colorScheme.surfaceContainer,
    onSurfaceContainer = colorScheme.onSurface,
    onSurfaceContainerVariant = colorScheme.onSurfaceVariant,
    surfaceContainerHigh = colorScheme.surfaceContainerHigh,
    onSurfaceContainerHigh = colorScheme.onSurface,
    surfaceContainerHighest = colorScheme.surfaceContainerHighest,
    onSurfaceContainerHighest = colorScheme.onSurface,
    outline = colorScheme.outline,
    dividerLine = colorScheme.outlineVariant,
)


/**
 * 把 Miuix 的中性「面族」压成纯黑（AMOLED）。
 *
 * 对齐 InstallerX `ui/theme/InstallerTheme.kt:163` 的 `colors.copy(surface = Color.Black)` 思路，
 * 但本项目页面底色用 `surfaceContainer`、卡片用 `surfaceContainerHigh`，故一并覆盖。
 */
private fun Colors.amoledBlackened(): Colors = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainer = Color.Black,
    surfaceContainerHigh = Color.Black,
    surfaceContainerHighest = Color.Black,
)