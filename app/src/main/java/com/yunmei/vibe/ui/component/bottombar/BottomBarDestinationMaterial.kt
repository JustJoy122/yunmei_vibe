package com.yunmei.vibe.ui.component.bottombar

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Home
import androidx.compose.material.icons.twotone.Lock
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.yunmei.vibe.R

/**
 * Material 主题底部导航栏 / 侧边栏的目的地。
 *
 * 图标统一采用 InstallerX Revived 的图标体系（`AppIcons.kt`：除少数状态标记外全部为 TwoTone），
 * 与 Material 侧其余页面保持一致；Miuix 主题继续使用 [BottomBarDestination] 的
 * Rounded / Filled / Outlined 三态图标，两套图标体系互不影响。
 */
internal enum class BottomBarDestinationMaterial(
    val label: Int,
    val icon: ImageVector,
    /**
     * UI 测试用的稳定标识。
     *
     * 底栏的文字/图标在自动化点击时可能因为渲染时机、系统弹窗遮挡而找不到，
     * 这里给每个 Tab 一个与文案无关的 tag，测试侧用 `By.res("tab_locks")` 定位
     * （底栏根节点设置了 `testTagsAsResourceId = true`，见 [BottomBarMaterial]）。
     */
    val testTag: String,
) {
    Home(R.string.home, Icons.TwoTone.Home, "tab_home"),
    Locks(R.string.locks, Icons.TwoTone.Lock, "tab_locks"),
    Settings(R.string.settings, Icons.TwoTone.Settings, "tab_settings"),
}
