package com.yunmei.vibe.ui.theme

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme

/**
 * 系统取色（莫奈）是否可用。
 *
 * `dynamicDarkColorScheme` / `dynamicLightColorScheme` 内部会读 `android.R.color.system_accent1_*`
 * 这类只存在于 Android 12（API 31）及以上的系统资源；低版本调用会抛
 * `Resources.NotFoundException: Resource ID #0x106002c`。这类调用又常发生在协程与组合中，
 * 异常会直接把进程打崩（Android 10 真机实测）。
 */
internal val isDynamicColorSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * 取系统基础色板（莫奈种子色来源）。
 *
 * 与直接调 `dynamic*ColorScheme` 的区别只有一处：低版本回退到 Material 默认色板，
 * 不再读取不存在的系统资源。调用方拿到结果后照常交给 material-kolor 推导。
 */
@SuppressLint("NewApi")
internal fun dynamicBaseColorScheme(context: Context, dark: Boolean): ColorScheme = when {
    isDynamicColorSupported && dark -> dynamicDarkColorScheme(context)
    isDynamicColorSupported -> dynamicLightColorScheme(context)
    dark -> darkColorScheme()
    else -> lightColorScheme()
}
