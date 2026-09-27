package com.yunmei.vibe.ui.component.miuix

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunmei.vibe.ui.theme.isInDarkTheme
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.isDynamicColor
import top.yukonga.miuix.kmp.utils.PressFeedbackType


@Composable
fun WarningCard(
    message: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
    onClick: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Card(
        modifier = modifier,
        onClick = { onClick?.invoke() },
        colors = CardDefaults.defaultColors(
            color = color ?: warningCardContainerColor()
        ),
        showIndication = onClick != null,
        pressFeedbackType = PressFeedbackType.Tilt
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
                color = warningCardContentColor(),
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
internal fun warningCardContainerColor(): Color = when {
    isDynamicColor -> colorScheme.errorContainer
    isInDarkTheme() -> Color(0xFF310808)
    else -> Color(0xFFF8E2E2)
}

/** 告警内容色（文字 / 图标）：莫奈开用 onErrorContainer，否则用固定告警红。 */
@Composable
internal fun warningCardContentColor(): Color =
    if (isDynamicColor) colorScheme.onErrorContainer else Color(0xFFF72727)
