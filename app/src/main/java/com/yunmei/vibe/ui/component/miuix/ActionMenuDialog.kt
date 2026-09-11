package com.yunmei.vibe.ui.component.miuix

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.yunmei.vibe.ui.component.ActionMenuItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/**
 * 通用弹窗菜单（Miuix，OverlayDialog）。
 *
 * 「设置 → 发送日志」与「门锁 → 扫码添加门锁」共用同一组件；
 * 颜色全部取自 [MiuixTheme.colorScheme]，自动适配莫奈开/关两套色板。
 */
@Composable
fun ActionMenuDialog(
    show: Boolean,
    title: String,
    items: List<ActionMenuItem>,
    onDismissRequest: () -> Unit,
) {
    OverlayDialog(
        show = show,
        onDismissRequest = onDismissRequest,
        insideMargin = DpSize(0.dp, 0.dp),
        content = {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 12.dp),
                text = title,
                fontSize = MiuixTheme.textStyles.title4.fontSize,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                color = colorScheme.onSurface
            )
            items.forEach { item ->
                ArrowPreference(
                    title = item.label,
                    startAction = {
                        Icon(
                            item.icon,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 16.dp),
                            tint = colorScheme.onSurface
                        )
                    },
                    onClick = { item.onClick() },
                    insideMargin = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                )
            }
            TextButton(
                text = stringResource(id = android.R.string.cancel),
                onClick = { onDismissRequest() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 24.dp)
                    .padding(horizontal = 24.dp)
            )
        }
    )
}
