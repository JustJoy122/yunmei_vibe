package com.yunmei.vibe.ui.component.material

import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

@Composable
fun TonalCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceBright,
    disabledContainerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    shape: Shape = MaterialTheme.shapes.large,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    // 显式指定禁用态配色：Material3 默认把容器色压到 38% 透明，
    // 叠在 surfaceContainer 页面上会发灰发脏，与页面里其它卡片的层级对不上。
    Card(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = disabledContainerColor,
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        ),
        shape = shape
    ) {
        content()
    }
}

@Composable
fun TonalCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceBright,
    shape: Shape = MaterialTheme.shapes.large,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = shape
    ) {
        content()
    }
}
