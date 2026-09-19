package com.yunmei.vibe.ui.component.bottombar

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.yunmei.vibe.ui.LocalMainPagerState

@Composable
fun NavigationRailMaterial(
    modifier: Modifier = Modifier,
) {
    val mainPagerState = LocalMainPagerState.current

    // Material 使用自己的图标集（TwoTone，对齐 InstallerX），不再复用 Miuix 的三态图标。
    val items = BottomBarDestinationMaterial.entries.map { destination ->
        Triple(destination.label, destination.icon, destination.icon)
    }

    NavigationRail(
        modifier = modifier.fillMaxHeight(),
        windowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout).only(
            WindowInsetsSides.Start + WindowInsetsSides.Vertical
        )
    ) {
        Spacer(Modifier.weight(1f))
        items.forEachIndexed { index, (label, selectedIcon, unselectedIcon) ->
            val selected = mainPagerState.selectedPage == index
            NavigationRailItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        mainPagerState.animateToPage(index)
                    }
                },
                icon = {
                    Icon(
                        if (selected) selectedIcon else unselectedIcon,
                        stringResource(label)
                    )
                },
                label = { Text(stringResource(label)) }
            )
        }
        Spacer(Modifier.weight(1f))
    }
}
