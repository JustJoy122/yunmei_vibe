package com.yunmei.vibe.ui.component

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector

/** 弹窗菜单条目：图标 + 文案 + 点击行为（Material / Miuix 共用同一模型）。 */
@Immutable
data class ActionMenuItem(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
)
