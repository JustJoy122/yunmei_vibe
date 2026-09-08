// SPDX-License-Identifier: GPL-3.0-only
// Reused verbatim from KernelSU (ui/component/statustag/StatusTag.kt) per project UI reference policy.
package com.yunmei.vibe.ui.component.statustag

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.yunmei.vibe.ui.LocalUiMode
import com.yunmei.vibe.ui.UiMode

@Composable
fun StatusTag(
    label: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color,
    contentColor: Color
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> StatusTagMiuix(label, modifier, backgroundColor, contentColor)
        UiMode.Material -> StatusTagMaterial(label, modifier, backgroundColor, contentColor)
    }
}
