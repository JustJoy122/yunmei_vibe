package com.yunmei.vibe.ui.theme

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
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
            val base = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
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
