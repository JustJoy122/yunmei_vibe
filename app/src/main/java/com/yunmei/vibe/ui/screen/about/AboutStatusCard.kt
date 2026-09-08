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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.yunmei.vibe.R
import com.yunmei.vibe.ui.component.material.AnimatedFluidBackground

/**
 * Material 关于页顶部状态卡（InstallerX Revived StatusWidget/CardWidget 复刻）：
 * - 卡片：ElevatedCard，primaryContainer 色阶；容器色取 blur 分支的「更淡」值
 *   containerColor.copy(alpha = 0.15f)，内容色 onPrimaryContainer；
 * - 阴影：半透明容器（blur 分支）禁用阴影（elevation 全 0），与 InstallerX 一致，
 *   避免半透明卡片下阴影核心透出；
 * - 动态渐变：blur 分支 enabled 的 AnimatedFluidBackground，以容器色为基色铺满卡片底层；
 * - 图标槽（新增逻辑）：沿用 InstallerX 图标槽（LocalContentColor provides secondary），
 *   但将位图图标替换为 ic_launcher_monochrome + tint = MaterialTheme.colorScheme.primary，
 *   使 Logo 跟随当前 Material 主题（Monet）动态取色；
 * - 排版与 InstallerX 一致：图标 56dp 居中 → 标题（ProvideTextStyle(titleLarge) 提供默认
 *   + 显式 titleMedium）→ 版本号 bodyMedium，Column padding(vertical=24.dp)、spacedBy(4.dp)。
 */
@Composable
fun AboutStatusCard(
    appName: String,
    versionInfo: String,
) {
    val containerColor = MaterialTheme.colorScheme.primaryContainer
    val onContainerColor = MaterialTheme.colorScheme.onPrimaryContainer

    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = containerColor.copy(alpha = 0.15f),
            contentColor = onContainerColor,
        ),
        // 半透明容器下禁用阴影（防止阴影核心透出），与 InstallerX blur 分支一致。
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            draggedElevation = 0.dp,
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
        ) {
            // 浅色动态渐变背景，位于卡片最底层（InstallerX AnimatedFluidBackground 原样复用）。
            AnimatedFluidBackground(
                baseColor = containerColor,
                enabled = true,
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
