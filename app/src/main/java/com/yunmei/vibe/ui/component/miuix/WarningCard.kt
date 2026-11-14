package com.yunmei.vibe.ui.component.miuix

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunmei.vibe.ui.component.WarningLevel
import com.yunmei.vibe.ui.theme.isInDarkTheme
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.isDynamicColor
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * Miuix 侧的提示条，与 Material 侧的 `WarningCard` 保持同一套 API。
 *
 * 配色按 [level] 取语义色（莫奈开启时用主题令牌，未开启时回落到固定色，
 * 与 KernelSU 原版 WarningCard 的做法一致）：
 * - [WarningLevel.Error]：`errorContainer` / `onErrorContainer`，按压反馈 Tilt；
 * - [WarningLevel.Notice]：`tertiaryContainer` / `onTertiaryContainer`，按压反馈 Sink。
 *
 * 内容自带 16dp 内边距，因此卡片本体把 [CardDefaults.InsideMargin] 置零，
 * 避免与外层 padding 叠加成 28dp。
 */
@Composable
fun WarningCard(
    message: String,
    modifier: Modifier = Modifier,
    level: WarningLevel = WarningLevel.Error,
    color: Color? = null,
    onClick: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Card(
        modifier = modifier,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(
            color = color ?: level.containerColor(),
            contentColor = level.contentColor(),
        ),
        pressFeedbackType = if (level == WarningLevel.Notice) PressFeedbackType.Sink else PressFeedbackType.Tilt,
        showIndication = onClick != null,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                color = level.contentColor(),
                fontSize = 14.sp
            )
            action?.invoke()
        }
    }
}

/**
 * KernelSU 原版 WarningCard（level = Error）的告警容器色。
 * 首页 Banner 的异常分支复用同一取色，保证告警语义一致。
 */
@Composable
internal fun warningCardContainerColor(): Color = WarningLevel.Error.containerColor()

/** 告警内容色（文字 / 图标）：莫奈开用 onErrorContainer，否则用固定告警红。 */
@Composable
internal fun warningCardContentColor(): Color = WarningLevel.Error.contentColor()

@Composable
internal fun WarningLevel.containerColor(): Color = when {
    isDynamicColor && this == WarningLevel.Notice -> colorScheme.tertiaryContainer
    isDynamicColor -> colorScheme.errorContainer
    isInDarkTheme() && this == WarningLevel.Notice -> Color(0xFF3E2F1B)
    isInDarkTheme() -> Color(0xFF310808)
    this == WarningLevel.Notice -> Color(0xFFFFF0DB)
    else -> Color(0xFFF8E2E2)
}

@Composable
internal fun WarningLevel.contentColor(): Color = when {
    isDynamicColor && this == WarningLevel.Notice -> colorScheme.onTertiaryContainer
    isDynamicColor -> colorScheme.onErrorContainer
    this == WarningLevel.Notice -> Color(0xFFF5A623)
    else -> Color(0xFFF72727)
}
