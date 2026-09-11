package com.yunmei.vibe.ui.component.material

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yunmei.vibe.ui.component.ActionMenuItem

/**
 * 通用弹窗菜单（Material，底部弹窗）。
 *
 * 「设置 → 发送日志」与「门锁 → 扫码添加门锁」共用同一组件，
 * 由调用方传入 [ActionMenuItem] 列表即可。
 */
@Composable
fun ActionMenuBottomSheet(
    items: List<ActionMenuItem>,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        content = {
            Row(
                modifier = Modifier
                    .padding(top = 16.dp, bottom = 32.dp)
                    .align(Alignment.CenterHorizontally)
            ) {
                items.forEachIndexed { index, item ->
                    if (index > 0) Spacer(Modifier.width(16.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FilledTonalIconButton(
                            modifier = Modifier.size(64.dp),
                            onClick = { item.onClick() },
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = item.label,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        },
    )
}
