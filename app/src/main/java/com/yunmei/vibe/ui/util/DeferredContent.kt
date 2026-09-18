package com.yunmei.vibe.ui.util

import androidx.compose.runtime.Composable

/**
 * 导航切换后，页面重内容是否已经可以组合。
 *
 * 迁移前这里依赖 navigation3 的 `LocalNavAnimatedContentScope`：动画未结束时先渲染轻量占位，
 * 动画结束再组合重内容，用来掩盖进入动画期间的卡顿。
 *
 * 导航层现已整体切换到 miuix-nav（与 InstallerX Revived 一致），miuix-nav 的过渡是声明式
 * `NavTransition`，不再暴露等价的「动画进行中」作用域；上游页面同样不做这种延迟组合
 * （内容由 ViewModel 驱动）。因此这里直接放行，行为与上游对齐，不再自研过渡状态判断。
 */
@Composable
fun rememberContentReady(): Boolean = true
