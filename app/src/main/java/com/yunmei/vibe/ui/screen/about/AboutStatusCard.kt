// SPDX-License-Identifier: GPL-3.0-only
// Reused from InstallerX Revived: StatusWidget / CardWidget structure
// (ui/page/main/widget/card/StatusCard.kt) + AnimatedFluidBackground.
package com.yunmei.vibe.ui.screen.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.yunmei.vibe.R
import com.yunmei.vibe.ui.component.material.AnimatedFluidBackground

/**
 * Material 关于页顶部状态卡（InstallerX Revived `CardWidget` 复刻）。
 *
 * 几何与分支逻辑 1:1 对齐 InstallerX `StatusCard.kt` 的 `CardWidget`：
 * - 容器：ElevatedCard + 内层 `Box(fillMaxWidth) { 渐变底层 + Column(padding(vertical = 24.dp), spacedBy(4.dp)) }`；
 * - `useBlur = false`（默认）：实色容器 + `CardDefaults.elevatedCardElevation()` 默认阴影；
 * - `useBlur = true`：容器 `copy(alpha = 0.15f)` + 阴影全 0（避免半透明容器下阴影核心透出）
 *   + 铺满卡片的 `AnimatedFluidBackground` 动态渐变；
 * - 图标槽沿用 InstallerX 图标槽（`LocalContentColor provides secondary`），
 *   位图图标替换为 `ic_launcher_monochrome` + `tint = primary`，使 Logo 跟随 Monet 动态取色；
 * - 排版：图标 56dp 居中 → 标题（`ProvideTextStyle(titleLarge)` + 显式 titleMedium）→ 版本号 bodyMedium。
 *
 * 颜色 token：卡片取 `surfaceContainerHigh`（页面底色为 surface），
 * 内容色由 `contentColorFor` 推导（即 `onSurface`），保证深色模式下层级清晰、文本可读。
 *
 * @param useBlur 对应 InstallerX 的 `useBlur`（本项目取「模糊」开关）。关闭时卡片保持实色，
 * 因为本页没有 InstallerX 的 layer backdrop 模糊底板，半透明容器会失去对比度。
 */
@Composable
fun AboutStatusCard(
    appName: String,
    versionInfo: String,
    useBlur: Boolean = false,
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val contentColor = MaterialTheme.colorScheme.contentColorFor(containerColor)

    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (useBlur) containerColor.copy(alpha = 0.15f) else containerColor,
            contentColor = contentColor,
        ),
        // 半透明容器下禁用阴影（防止阴影核心透出），与 InstallerX blur 分支一致。
        elevation = if (useBlur) {
            CardDefaults.elevatedCardElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                focusedElevation = 0.dp,
                hoveredElevation = 0.dp,
                draggedElevation = 0.dp,
            )
        } else {
            CardDefaults.elevatedCardElevation()
        },
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
        ) {
            // 浅色动态渐变背景，位于卡片最底层（InstallerX AnimatedFluidBackground 原样复用）。
            AnimatedFluidBackground(
                baseColor = containerColor,
                enabled = useBlur,
                modifier = Modifier.matchParentSize(),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // 图标槽：Logo 跟随 Material 主题（Monet）动态取色。
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.secondary) {
                    Box(modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_launcher_monochrome),
                            contentDescription = appName,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                ProvideTextStyle(value = MaterialTheme.typography.titleLarge) {
                    Box(modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text(
                            text = appName,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                Box {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = versionInfo,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}
