package com.yunmei.vibe.ui.component.bottombar

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.material3.FlexibleBottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextOverflow
import com.yunmei.vibe.ui.LocalMainPagerState

@Composable
fun BottomBarMaterial() {
    val mainPagerState = LocalMainPagerState.current

    FlexibleBottomAppBar(
        // 让 testTag 以 resource-id 的形式暴露给 UiAutomator（截图测试用 By.res 定位 Tab）。
        // 只作用于底栏这一棵子树，不影响其它节点的无障碍语义。
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        windowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout).only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
        )
    ) {
        // Material 使用自己的图标集（TwoTone，对齐 InstallerX），不再复用 Miuix 的三态图标。
        BottomBarDestinationMaterial.entries.forEachIndexed { index, destination ->
            val selected = mainPagerState.selectedPage == index
            val label = stringResource(destination.label)
            NavigationBarItem(
                modifier = Modifier.testTag(destination.testTag),
                selected = selected,
                onClick = {
                    if (!selected) {
                        mainPagerState.animateToPage(index)
                    }
                },
                icon = {
                    Icon(destination.icon, label)
                },
                label = {
                    Text(
                        label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}
