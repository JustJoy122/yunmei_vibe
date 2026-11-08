package com.yunmei.vibe.ui.screen.locks

import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.automirrored.rounded.Login
import com.yunmei.vibe.data.local.StoredUser
import com.yunmei.vibe.ui.theme.isInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.People
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.QrCodeScanner
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
                            state.locks.forEach { lock ->
                                LockCard(
                                    lock = lock,
                                    isDefault = lock.label == state.defaultLabel,
                                    actions = actions,
                                )
                            }
                        }

                        // 账号（登录/切换账号 + 已保存账号；原「设置 → 账号」分组整体迁入）
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                BasicComponent(
                                    title = stringResource(
                                        if (state.accounts.isEmpty()) R.string.locks_add_login
                                        else R.string.settings_account_login
                                    ),
                                    summary = stringResource(R.string.settings_account_hint),
                                    startAction = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.Login,
                                            tint = colorScheme.onSurface,
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = actions.onLogin,
                                )
                                state.accounts.forEach { user ->
                                    AccountRow(user, actions.onRemoveAccount)
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
                                    title = stringResource(R.string.scan_paste_link),
                                    summary = stringResource(R.string.scan_paste_link_hint),
                                    startAction = {
                                        Icon(
                                            imageVector = Icons.Rounded.ContentPaste,
                                            tint = colorScheme.onSurface,
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = actions.onPasteLink,
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
private fun LockCard(
    lock: Lock,
    isDefault: Boolean,
    actions: LocksActions,
) {
    val secondaryContainer = colorScheme.secondaryContainer.copy(alpha = 0.8f)
    // 与 KernelSU module 卡片一致：图标色取 onSurface 的透明度，而不是某个固定语义色。
    val actionIconTint = colorScheme.onSurface.copy(alpha = if (isInDarkTheme()) 0.7f else 0.9f)
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = lock.label.ifBlank { stringResource(R.string.home_status_empty_title) },
                fontSize = 17.sp,
                fontWeight = FontWeight(550),
                color = colorScheme.onSurface,
            )
            if (isDefault) {
                Spacer(Modifier.width(6.dp))
                DefaultTag()
            }
        }
        // 对应 ModuleItem(Miuix) 的版本/作者两行：12sp、字重 550、onSurfaceVariantSummary。
        Text(
            text = lock.schoolName,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp),
            fontWeight = FontWeight(550),
            color = colorScheme.onSurfaceVariantSummary,
        )
        // 门锁号对应 ModuleItem 的「模块说明」：14sp、默认字重，与上方单位名拉开间距。
        Text(
            text = lock.lockNo.ifBlank { lock.mac },
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 1.dp),
            color = colorScheme.onSurfaceVariantSummary,
        )
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            thickness = 0.5.dp,
            color = colorScheme.outline.copy(alpha = 0.5f),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // ModuleItem 的动作按钮是「图标 + 文字」的胶囊；我们只有图标，因此用 35dp 正圆。
                Row(
                    modifier = Modifier
                        .size(35.dp)
                        .clip(CircleShape)
                        .background(secondaryContainer)
                        .clickable { actions.onOpenDetail(lock) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        modifier = Modifier.size(24.dp),
                        imageVector = Icons.Rounded.Info,
                        tint = actionIconTint,
                        contentDescription = stringResource(R.string.locks),
                    )
                }
                Row(
                    modifier = Modifier
                        .size(35.dp)
                        .clip(CircleShape)
                        .background(secondaryContainer)
                        .clickable { actions.onOpenMembers(lock) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        modifier = Modifier.size(24.dp),
                        imageVector = Icons.Rounded.People,
                        tint = actionIconTint,
                        contentDescription = stringResource(R.string.dorm_members),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            // 与 KernelSU 的 miuix 删除按钮一致：IconButton 胶囊 + MiuixIcons.Delete + 文字。
            IconButton(
                backgroundColor = secondaryContainer,
                minHeight = 35.dp,
                minWidth = 35.dp,
                onClick = { actions.onDelete(lock) },
            ) {
                // 源码里这层 Row 带 animatedPadding（无更新时固定 10.dp），少了它文字会顶到胶囊边缘。
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        modifier = Modifier.size(20.dp),
                        imageVector = MiuixIcons.Delete,
                        tint = actionIconTint,
                        contentDescription = null,
                    )
                    Text(
                        modifier = Modifier.padding(start = 4.dp, end = 3.dp),
                        text = stringResource(R.string.locks_delete),
                        color = actionIconTint,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                    )
                }
            }
        }
    }
}

/** 「默认」标签：与 KernelSU Miuix 模块卡片的 META 标签同款。 */
@Composable
private fun DefaultTag() {
    Box(
        modifier = Modifier
            .background(colorScheme.tertiaryContainer.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            text = stringResource(R.string.locks_default_tag),
            color = colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
            fontSize = 12.sp,
            fontWeight = FontWeight(750),
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun AccountRow(
    user: StoredUser,
    onRemove: (StoredUser) -> Unit,
) {
    BasicComponent(
        title = user.username,
        summary = stringResource(R.string.settings_saved_accounts),
        startAction = {
            Icon(
                Icons.Rounded.Person,
                tint = colorScheme.onSurface,
                contentDescription = user.username,
            )
        },
        endActions = {
            Icon(
                Icons.Rounded.Delete,
                tint = colorScheme.onSurfaceVariantActions,
                contentDescription = stringResource(R.string.settings_remove_account),
                modifier = Modifier
                    .clickable { onRemove(user) }
                    .padding(8.dp),
            )
        },
    )
}