package com.yunmei.vibe.ui.component.material

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yunmei.vibe.R

/**
 * 「页面缩放」的自定义输入对话框（Material 版）。
 *
 * 交互逻辑与 miuix 版 `ui/component/miuix/ScaleDialog.kt` 完全一致：
 * 纯数字校验（空串暂存）→ 点「确定」时 `coerceIn(80, 110)` 回写 → 取消不写 → 每次打开按当前值重置；
 * 取值范围与滑块一致（80%–110%，滑块 `valueRange = 0.8f..1.1f`）。
 *
 * 为什么不直接复用 miuix 版：`ui/theme/Theme.kt:122-127` 按模式二选一提供主题，
 * Material 模式走 `MaterialTemplateTheme`，不提供 miuix 组件依赖的 `MiuixTheme.colorScheme` 等组合局部；
 * 且 `OverlayDialog` 的观感与 Material 页面不一致。故按同一交互逻辑实现 Material 版。
 */
@Composable
fun ScaleDialogMaterial(
    show: Boolean,
    onDismissRequest: () -> Unit,
    volumeState: () -> Float,
    onVolumeChange: (Float) -> Unit,
) {
    if (!show) return

    var text by remember(show) { mutableStateOf((volumeState() * 100).toInt().toString()) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.settings_page_scale)) },
        text = {
            Column {
                Text(
                    text = "80% - 110%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { newValue ->
                        // 与 miuix 版一致：只接受数字；空串允许暂存（确定时回落到当前值）。
                        if (newValue.isEmpty()) {
                            text = ""
                        } else if (newValue.all { it.isDigit() }) {
                            text = newValue
                        }
                    },
                    singleLine = true,
                    trailingIcon = {
                        Text(
                            text = "%",
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsed = text.toIntOrNull()
                    val clamped = parsed?.coerceIn(80, 110) ?: (volumeState() * 100).toInt()
                    onVolumeChange(clamped / 100f)
                    onDismissRequest()
                }
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
