package com.yunmei.vibe.ui.theme

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.dynamicColorScheme

/**
 * 非 Compose 上下文（通知进度条、快捷方式等）可用的主题强调色。
 *
 * 取色与 [MaterialTemplateTheme] 使用同一套输入与同一个推导函数（material-kolor 的
 * [dynamicColorScheme]），因此通知颜色与界面主题同源，不会出现两套逻辑漂移；
 * 结果按主题设置签名缓存，避免每次进度更新都重新推导整份色板。
 */
object ThemeColors {

    /** 通知进度条分段与结果状态需要的强调色。 */
    data class Accent(val primary: Int, val tertiary: Int, val error: Int)

    @Volatile
    private var cacheSignature: String? = null

    @Volatile
    private var cacheAccent: Accent? = null

    fun accent(context: Context): Accent {
        val settings = ThemeController.getAppSettings(context)
        val systemDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val dark = settings.colorMode.isDark || (settings.colorMode.isSystem && systemDark)

        val signature = buildString {
            append(settings.colorMode.value).append('|')
            append(settings.keyColor).append('|')
            append(settings.paletteStyle.name).append('|')
            append(settings.colorSpec.name).append('|')
            append(settings.amoled).append('|')
            append(dark)
        }
        cacheAccent?.let { cached -> if (cacheSignature == signature) return cached }

        val amoled = settings.amoled && dark
        val style = settings.paletteStyle
        val specVersion = settings.colorSpec.effectiveFor(style)
        val scheme = if (settings.keyColor == 0) {
            // 系统取色（莫奈）：与 MaterialTemplateTheme 一样，用系统方案的关键色作种子。
            val base = dynamicBaseColorScheme(context, dark)
            dynamicColorScheme(
                seedColor = Color.Unspecified,
                isDark = dark,
                isAmoled = amoled,
                style = style,
                specVersion = specVersion,
                primary = base.primary,
                secondary = base.secondary,
                tertiary = base.tertiary,
                neutral = base.surface,
                neutralVariant = base.surfaceVariant,
                error = base.error,
            )
        } else {
            dynamicColorScheme(
                seedColor = Color(settings.keyColor),
                isDark = dark,
                isAmoled = amoled,
                style = style,
                specVersion = specVersion,
            )
        }

        val accent = Accent(
            primary = scheme.primary.toArgb(),
            tertiary = scheme.tertiary.toArgb(),
            error = scheme.error.toArgb(),
        )
        cacheSignature = signature
        cacheAccent = accent
        return accent
    }
}

/**
 * 把 Material 3 色板的"表面族"强制压成纯黑。
 *
 * KernelSU 的等价实现见 `ui/theme/ThemeExt.kt:15-26`：material-kolor 的 `isAmoled`
 * 只改写 `background` / `surface` / `onBackground` / `onSurface` 四个角色（已用 javap
 * 核对 5.0.0 的 `MaterialKolors`：`surfaceContainer*`、`surfaceDim`、`surfaceBright`
 * 都没有 amoled 分支），而本项目的页面底色用的是 `surfaceContainer`、卡片用的是
 * `surfaceBright`，所以仅靠库内参数开 AMOLED 会出现"页面还是深灰"的观感。
 */
fun ColorScheme.amoledBackground(amoled: Boolean): ColorScheme =
    if (!amoled) this
    else copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color.Black,
        surfaceContainer = Color.Black,
        surfaceContainerHigh = Color.Black,
        surfaceContainerHighest = Color.Black,
        surfaceBright = Color.Black,
    )
