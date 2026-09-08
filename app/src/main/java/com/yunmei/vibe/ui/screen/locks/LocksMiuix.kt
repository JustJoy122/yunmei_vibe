package com.yunmei.vibe.ui.screen.locks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yunmei.vibe.R
import com.yunmei.vibe.data.model.Lock
import com.yunmei.vibe.ui.theme.LocalEnableBlur
import com.yunmei.vibe.ui.util.BlurredBar
import com.yunmei.vibe.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun LocksPagerMiuix(
    state: LocksUiState,
    actions: LocksActions,
    bottomInnerPadding: Dp,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface
    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.locks),
                    scrollBehavior = scrollBehavior
                )
            }
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = 12.dp),
                contentPadding = innerPadding,
                overscrollEffect = null,
            ) {
                item {
                    Column(
                        modifier = Modifier.padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (state.locks.isEmpty()) {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                BasicComponent(
                                    title = stringResource(R.string.locks_empty),
                                    summary = stringResource(R.string.locks_empty_hint),
                                    startAction = {
                                        Icon(
                                            imageVector = Icons.Rounded.Lock,
                                            tint = colorScheme.onSurface,
                                            contentDescription = null,
                                        )
                                    },
                                )
                            }
                        } else {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column {
                                    state.locks.forEach { lock ->
                                        LockRow(lock, state.defaultLabel, actions)
                                    }
                                }
                            }
                        }

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                BasicComponent(
                                    title = stringResource(R.string.locks_add_scan),
                                    summary = stringResource(R.string.locks_add_scan_hint),
                                    startAction = {
                                        Icon(
                                            imageVector = Icons.Rounded.QrCodeScanner,
                                            tint = colorScheme.onSurface,
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = actions.onAddScan,
                                )
                                BasicComponent(
                                    title = stringResource(R.string.locks_add_login),
                                    startAction = {
                                        Icon(
                                            imageVector = Icons.Rounded.PersonAdd,
                                            tint = colorScheme.onSurface,
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = actions.onAddLogin,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(bottomInnerPadding))
                }
            }
        }
    }
}

@Composable
private fun LockRow(
    lock: Lock,
    defaultLabel: String?,
    actions: LocksActions,
) {
    val isDefault = lock.label == defaultLabel
    BasicComponent(
        title = lock.label,
        summary = stringResource(R.string.home_mac) + "：" + lock.mac,
        startAction = {
            Icon(
                imageVector = if (isDefault) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                tint = if (isDefault) colorScheme.primary else colorScheme.onSurfaceVariantActions,
                contentDescription = stringResource(R.string.locks_set_default),
                modifier = Modifier
                    .clickable { actions.onSetDefault(lock) }
                    .padding(4.dp),
            )
        },
        endActions = {
            Icon(
                imageVector = Icons.Rounded.Delete,
                tint = colorScheme.onSurfaceVariantActions,
                contentDescription = stringResource(R.string.locks_delete),
                modifier = Modifier
                    .clickable { actions.onDelete(lock) }
                    .padding(8.dp),
            )
        },
        onClick = { actions.onOpenDetail(lock) },
    )
}
