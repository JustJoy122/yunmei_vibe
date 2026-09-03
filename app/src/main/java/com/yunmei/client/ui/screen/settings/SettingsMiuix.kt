package com.yunmei.client.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ContactPage
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyOff
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SupervisorAccount
import androidx.compose.material.icons.rounded.Update
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yunmei.client.R
import com.yunmei.client.data.local.StoredUser
import com.yunmei.client.ui.component.dialog.rememberLoadingDialog
import com.yunmei.client.ui.component.miuix.SendLogDialog
import com.yunmei.client.ui.theme.LocalEnableBlur
import com.yunmei.client.ui.util.BlurredBar
import com.yunmei.client.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 主设置页：与模板 KernelSU-Style-UI-Kit 一致的精简布局。
 */
@Composable
fun SettingPagerMiuix(
    state: SettingsScreenState,
    actions: SettingsScreenActions,
    bottomInnerPadding: Dp,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface
    val loadingDialog = rememberLoadingDialog()
    val showSendLogDialog = rememberSaveable { mutableStateOf(false) }
    val signLocationItems = listOf(
        stringResource(R.string.settings_sign_location_ask),
        stringResource(R.string.settings_sign_location_rel),
        stringResource(R.string.settings_sign_location_lst),
    )

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                TopAppBar(
                    color = barColor,
                    title = stringResource(R.string.settings),
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
                    Spacer(Modifier.height(12.dp))
                    SectionTitle(stringResource(R.string.settings_group_general))
                    Card(
                        modifier = Modifier
                            .padding(bottom = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        SwitchPreference(
                            title = stringResource(id = R.string.settings_check_update),
                            summary = stringResource(id = R.string.settings_check_update_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.Update,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(id = R.string.settings_check_update),
                                    tint = colorScheme.onBackground
                                )
                            },
                            checked = state.checkUpdate,
                            onCheckedChange = actions.onSetCheckUpdate
                        )
                    }

                    SectionTitle(stringResource(R.string.settings_group_account))
                    Card(
                        modifier = Modifier
                            .padding(bottom = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        ArrowPreference(
                            title = stringResource(R.string.settings_account_login),
                            summary = stringResource(R.string.settings_account_hint),
                            startAction = {
                                Icon(
                                    Icons.AutoMirrored.Rounded.Login,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(R.string.settings_account_login),
                                    tint = colorScheme.onBackground,
                                )
                            },
                            onClick = actions.onLogin,
                        )
                        state.accounts.forEach { user ->
                            AccountRow(user, actions)
                        }
                    }

                    SectionTitle(stringResource(R.string.settings_group_personalization))
                    Card(
                        modifier = Modifier
                            .padding(bottom = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        OverlayDropdownPreference(
                            title = stringResource(id = R.string.settings_ui_mode),
                            summary = stringResource(id = R.string.settings_ui_mode_summary),
                            items = listOf("Miuix", "Material"),
                            startAction = {
                                Icon(
                                    Icons.Rounded.Dashboard,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(id = R.string.settings_ui_mode),
                                    tint = colorScheme.onBackground
                                )
                            },
                            selectedIndex = if (state.uiMode == "material") 1 else 0,
                            onSelectedIndexChange = actions.onSetUiModeIndex
                        )
                        ArrowPreference(
                            title = stringResource(id = R.string.settings_theme),
                            summary = stringResource(id = R.string.settings_theme_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.Palette,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(id = R.string.settings_theme),
                                    tint = colorScheme.onBackground
                                )
                            },
                            onClick = actions.onOpenTheme
                        )
                    }

                    // 开门行为（取码与打卡；快速连接/自动开门/自动退出/自动获取密码已迁至首页）
                    SectionTitle(stringResource(R.string.settings_group_door))
                    Card(
                        modifier = Modifier
                            .padding(bottom = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        SwitchPreference(
                            title = stringResource(R.string.settings_always_code),
                            summary = stringResource(R.string.settings_always_code_summary),
                            startAction = {
                                Icon(
                                    Icons.Rounded.SupervisorAccount,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(R.string.settings_always_code),
                                    tint = colorScheme.onBackground,
                                )
                            },
                            checked = state.settings.alwaysCode,
                            onCheckedChange = actions.onSetAlwaysCode,
                        )
                        OverlayDropdownPreference(
                            title = stringResource(R.string.settings_sign_location_mode),
                            items = signLocationItems,
                            startAction = {
                                Icon(
                                    Icons.Rounded.LocationOn,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(R.string.settings_sign_location_mode),
                                    tint = colorScheme.onBackground,
                                )
                            },
                            selectedIndex = when (state.settings.signLocationMode) {
                                "rel" -> 1
                                "lst" -> 2
                                else -> 0
                            },
                            onSelectedIndexChange = actions.onSetSignLocationMode,
                        )
                    }

                    // 界面显示
                    SectionTitle(stringResource(R.string.settings_group_display))
                    Card(
                        modifier = Modifier
                            .padding(bottom = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        SwitchPreference(
                            title = stringResource(R.string.settings_hide_sign),
                            startAction = {
                                Icon(
                                    Icons.Rounded.LocationOff,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(R.string.settings_hide_sign),
                                    tint = colorScheme.onBackground,
                                )
                            },
                            checked = state.settings.hideSign,
                            onCheckedChange = actions.onSetHideSign,
                        )
                        SwitchPreference(
                            title = stringResource(R.string.settings_hide_code),
                            startAction = {
                                Icon(
                                    Icons.Rounded.KeyOff,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(R.string.settings_hide_code),
                                    tint = colorScheme.onBackground,
                                )
                            },
                            checked = state.settings.hideCode,
                            onCheckedChange = actions.onSetHideCode,
                        )
                    }

                    SectionTitle(stringResource(R.string.settings_group_other))
                    Card(
                        modifier = Modifier
                            .padding(bottom = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        ArrowPreference(
                            title = stringResource(id = R.string.send_log),
                            startAction = {
                                Icon(
                                    Icons.Rounded.BugReport,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(id = R.string.send_log),
                                    tint = colorScheme.onBackground
                                )
                            },
                            onClick = { showSendLogDialog.value = true },
                        )
                        SendLogDialog(
                            show = showSendLogDialog.value,
                            onDismissRequest = { showSendLogDialog.value = false },
                            loadingDialog = loadingDialog
                        )
                        val about = stringResource(id = R.string.settings_about)
                        ArrowPreference(
                            title = about,
                            startAction = {
                                Icon(
                                    Icons.Rounded.ContactPage,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = about,
                                    tint = colorScheme.onBackground
                                )
                            },
                            onClick = actions.onOpenAbout,
                        )
                    }
                    Spacer(Modifier.height(bottomInnerPadding))
                }
            }
        }
    }
}

@Composable
private fun AccountRow(
    user: StoredUser,
    actions: SettingsScreenActions,
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
                    .clickable { actions.onRemoveAccount(user) }
                    .padding(8.dp),
            )
        },
    )
}

@Composable
private fun SectionTitle(title: String) {
    // InstallerX Revived 同款分组小标题：miuix 官方 SmallTitle
    // （subtitle 14sp Bold + onBackgroundVariant 灰蓝色）。
    // 间距规则：卡片组统一 bottom=12、顶 0；标题 top=0、bottom=12 ——
    // 标题与上方卡片组 = 12（卡片 bottom）+ 0，与下方卡片组 = 12（标题 bottom）+ 0，视觉间距均匀。
    // 本页 LazyColumn 已有 12dp 水平边距，左内边距取 16dp 使标题相对页面边缘 28dp，
    // 与 InstallerX（页面无外边距 + SmallTitle 默认 28dp）视觉完全一致。
    SmallTitle(
        text = title,
        insideMargin = PaddingValues(start = 16.dp, top = 0.dp, end = 28.dp, bottom = 12.dp),
    )
}
