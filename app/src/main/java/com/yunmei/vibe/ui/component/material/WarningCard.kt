package com.yunmei.vibe.ui.component.material

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yunmei.vibe.ui.component.WarningLevel

/**
 * Material 侧的提示条，与 Miuix 的 `WarningCard` 保持同一套 API。
 *
 * 配色按 [level] 取语义色：`Error` → `errorContainer` / `onErrorContainer`，
 * `Notice` → `tertiaryContainer` / `onTertiaryContainer`；容器沿用项目统一的
 * [TonalCard]（大圆角卡片），与页面里其它卡片同一层级。
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
    val containerColor = color ?: level.containerColor()
    val contentColor = level.contentColor()
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )
            action?.invoke()
        }
    }
    if (onClick != null) {
        TonalCard(modifier = modifier, containerColor = containerColor, onClick = onClick) {
            content()
        }
    } else {
        TonalCard(modifier = modifier, containerColor = containerColor) {
            content()
        }
    }
}

@Composable
private fun WarningLevel.containerColor(): Color = when (this) {
    WarningLevel.Error -> MaterialTheme.colorScheme.errorContainer
    WarningLevel.Notice -> MaterialTheme.colorScheme.tertiaryContainer
}

@Composable
private fun WarningLevel.contentColor(): Color = when (this) {
    WarningLevel.Error -> MaterialTheme.colorScheme.onErrorContainer
    WarningLevel.Notice -> MaterialTheme.colorScheme.onTertiaryContainer
}
