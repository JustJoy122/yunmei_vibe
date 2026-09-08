package com.yunmei.vibe.ui.component.markdown

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yunmei.vibe.ui.LocalUiMode
import com.yunmei.vibe.ui.UiMode

/**
 * 模板对话框中正文渲染的本地实现（保持与 KernelSU-Style-UI-Kit 相同的签名）。
 * 本项目弹窗只使用纯文本正文，因此直接渲染 Text；
 * 如后续需要 Markdown/HTML 正文，可替换回模板原版（commonmark + WebView）。
 */
@Composable
fun MarkdownContent(
    content: String,
    isMarkdown: Boolean,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> top.yukonga.miuix.kmp.basic.Text(
            text = content,
            color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        )

        UiMode.Material -> Text(
            text = content,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        )
    }
}
