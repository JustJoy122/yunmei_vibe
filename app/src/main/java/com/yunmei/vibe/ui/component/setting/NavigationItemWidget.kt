// SPDX-License-Identifier: GPL-3.0-only
// Reused verbatim from InstallerX Revived (ui/page/main/widget/setting/NavigationItemWidget.kt) per project UI reference policy.
package com.yunmei.vibe.ui.component.setting

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * A setting pkg that navigates to a secondary page, built upon [BaseWidget].
 * It includes an icon, title, description, and a trailing arrow.
 */
@Composable
fun NavigationItemWidget(
    icon: ImageVector? = null,
    iconPlaceholder: Boolean = true,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    BaseWidget(
        icon = icon,
        iconPlaceholder = iconPlaceholder,
        title = title,
        description = description,
        onClick = onClick,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
        )
    }
}
