package com.yunmei.vibe.ui.screen.lockdetail

import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.saveable.rememberSaveable
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.MiuixIcons
import androidx.compose.foundation.layout.width
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import androidx.compose.foundation.layout.PaddingValues
import top.yukonga.miuix.kmp.basic.SmallTitle
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.twotone.ContentCopy
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Key
import androidx.compose.material.icons.twotone.QrCode2
import androidx.compose.material.icons.twotone.Share
import androidx.compose.material.icons.twotone.Star
import androidx.compose.material.icons.twotone.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.yunmei.vibe.R
import com.yunmei.vibe.data.model.Lock
import com.yunmei.vibe.data.model.UnitCodes
import com.yunmei.vibe.data.qrcode.QrCode
import com.yunmei.vibe.ui.LocalUiMode
import com.yunmei.vibe.ui.UiMode
import com.yunmei.vibe.ui.component.dialog.ConfirmResult
import com.yunmei.vibe.ui.component.dialog.rememberConfirmDialog
import com.yunmei.vibe.ui.component.material.SegmentedColumn
import com.yunmei.vibe.ui.component.material.SegmentedListItem
import com.yunmei.vibe.ui.component.material.SegmentedSwitchItem
import com.yunmei.vibe.ui.navigation.LocalNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.nav.gesture.WindowNavigationEventBridge

/** 二维码展开/折叠动画时长（200–300ms 区间，与 InstallerX Revived 的手风琴写法一致）。 */
private const val QR_ANIM_DURATION_MS = 250

@Composable
fun LockDetailScreen(label: String) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val confirmDialog = rememberConfirmDialog()
    val scope = rememberCoroutineScope()
    // 数据读取全部下沉到 LockDetailViewModel：页面只消费 UiState。
    val viewModel = viewModel<LockDetailViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showQr by rememberSaveable { mutableStateOf(false) }
    var showAesKey by rememberSaveable { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        viewModel.refresh(label)
        onPauseOrDispose { }
    }

    fun shareLock(current: Lock) {
        val shareUrl = current.toShareUrl()
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareUrl)
        }
        context.startActivity(Intent.createChooser(send, null))
    }

    fun copyLink(current: Lock) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("yunmei", current.toShareUrl()))
        Toast.makeText(context, R.string.lock_detail_copied, Toast.LENGTH_SHORT).show()
    }

    // 只有「已读取完成且确实找不到该门锁」时才返回，避免首帧误弹。
    val current = uiState.lock
    if (uiState.loaded && current == null) {
        navigator.pop()
        return
    }
    if (current == null) return

    val deleteTitle = stringResource(R.string.locks_delete)
    val labelPrefix = stringResource(R.string.lock_detail_label)
    val confirmText = stringResource(R.string.confirm)
    val cancelText = stringResource(R.string.cancel)
    val actions = LockDetailActions(
        onSetDefault = { value -> viewModel.setDefault(value) },
        onDelete = {
            scope.launch {
                val result = confirmDialog.awaitConfirm(
                    title = deleteTitle,
                    content = labelPrefix + "：" + current.label,
                    confirm = confirmText,
                    dismiss = cancelText,
                )
                if (result == ConfirmResult.Confirmed) {
                    viewModel.removeLock()
                    navigator.pop()
                }
            }
        },
        onShare = { shareLock(current) },
        onCopyLink = { copyLink(current) },
        onShowAesKey = { showAesKey = true },
        onSaveAesKey = { value -> viewModel.setCustomAesKey(value) },
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> LockDetailScreenMiuix(current, uiState.isDefault, uiState.accountSaved, showQr, onToggleQr = { showQr = !showQr }, actions)
        UiMode.Material -> LockDetailScreenMaterial(current, uiState.isDefault, uiState.accountSaved, showQr, onToggleQr = { showQr = !showQr }, actions)
    }

    if (showAesKey) {
        AesKeyDialog(
            initialValue = current.customAesKey.orEmpty(),
            onDismiss = { showAesKey = false },
            onConfirm = { value ->
                showAesKey = false
                actions.onSaveAesKey(value)
            },
        )
    }

}

/**
 * 「位置」行文案：areaName + buildName + dormName 直接拼接，三个名字都为空时整行隐藏。
 */
@Composable
private fun locationTextOf(lock: Lock): String =
    listOf(lock.areaName, lock.buildName, lock.dormName)
        .filter { it.isNotBlank() }
        .joinToString("")

/**
 * 「协议能力」行文案：`加密：是 · 密码：支持（6 位）`。
 *
 * 能力为 null 表示型号未知（没拉到 locktype/all，或型号不在表里），一律显示「未知」，
 * 不要显示成「否」——那会把「不知道」误报成「不支持」。
 */
@Composable
private fun protocolTextOf(lock: Lock): String {
    val aes = stringResource(
        when (lock.aesSupport) {
            true -> R.string.lock_detail_flag_yes
            false -> R.string.lock_detail_flag_no
            null -> R.string.lock_detail_flag_unknown
        }
    )
    val pwd = when (lock.pwdSupport) {
        true -> lock.pwdLength?.let { stringResource(R.string.lock_detail_flag_supported_n, it) }
            ?: stringResource(R.string.lock_detail_flag_yes)
        false -> stringResource(R.string.lock_detail_flag_no)
        null -> stringResource(R.string.lock_detail_flag_unknown)
    }
    return stringResource(R.string.lock_detail_protocol_aes, aes) +
        " · " + stringResource(R.string.lock_detail_protocol_pwd, pwd)
}

private data class LockDetailActions(
    val onSetDefault: (Boolean) -> Unit,
    val onDelete: () -> Unit,
    val onShare: () -> Unit,
    val onCopyLink: () -> Unit,
    /** 打开「设置 AES 密钥」弹窗（仅 AES 型号门锁显示该入口）。 */
    val onShowAesKey: () -> Unit,
    /** 提交 AES 密钥（32 位十六进制，空串表示清除）。 */
    val onSaveAesKey: (String) -> Unit,
)

@Composable
private fun LockDetailScreenMaterial(
    lock: Lock,
    isDefault: Boolean,
    accountSaved: Boolean,
    showQr: Boolean,
    onToggleQr: () -> Unit,
    actions: LockDetailActions,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val navigator = LocalNavigator.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(lock.label.ifBlank { stringResource(R.string.home_status_empty_title) }) },
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.lock_detail_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                scrollBehavior = scrollBehavior
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            val actionsTitle = stringResource(R.string.lock_detail_actions_group)
            val infoTitle = stringResource(R.string.lock_detail_info_group)
            val unknownText = stringResource(R.string.lock_label_unknown)
            val unitName = lock.schoolName.ifBlank { UnitCodes.format(lock.schoolNo) }
            val lockNoText = lock.mac.ifBlank { lock.lockNo }
            val locationText = locationTextOf(lock)
            val protocolText = protocolTextOf(lock)

            // 第一组「操作」：设为默认 / 删除；AES 型号额外多一个密钥入口。
            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                title = actionsTitle,
                content = buildList {
                    add {
                        SegmentedSwitchItem(
                            icon = if (isDefault) Icons.TwoTone.Star else Icons.TwoTone.StarBorder,
                            title = stringResource(R.string.locks_set_default),
                            checked = isDefault,
                            onCheckedChange = actions.onSetDefault,
                        )
                    }
                    if (lock.aesSupport == true) {
                        add {
                            SegmentedListItem(
                                onClick = actions.onShowAesKey,
                                headlineContent = { Text(stringResource(R.string.lock_detail_aes_key)) },
                                supportingContent = {
                                    Text(
                                        stringResource(
                                            if (lock.customAesKey.isNullOrBlank()) {
                                                R.string.lock_detail_aes_key_unset
                                            } else {
                                                R.string.lock_detail_aes_key_configured
                                            }
                                        )
                                    )
                                },
                                leadingContent = { Icon(Icons.TwoTone.Key, null) },
                                trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
                            )
                        }
                    }
                    add {
                        SegmentedListItem(
                            onClick = actions.onDelete,
                            headlineContent = { Text(stringResource(R.string.locks_delete)) },
                            leadingContent = {
                                Icon(Icons.TwoTone.Delete, null, tint = MaterialTheme.colorScheme.error)
                            },
                        )
                    }
                },
            )

            // 第二组「分享」：系统分享 / 复制链接 / 生成二维码，二维码面板紧随其后。
            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                title = stringResource(R.string.lock_detail_share_group),
                content = listOf(
                    {
                        SegmentedListItem(
                            onClick = actions.onShare,
                            headlineContent = { Text(stringResource(R.string.lock_detail_share)) },
                            leadingContent = { Icon(Icons.TwoTone.Share, null) },
                        )
                    },
                    {
                        SegmentedListItem(
                            onClick = actions.onCopyLink,
                            headlineContent = { Text(stringResource(R.string.lock_detail_copy_link)) },
                            leadingContent = { Icon(Icons.TwoTone.ContentCopy, null) },
                        )
                    },
                    {
                        SegmentedListItem(
                            onClick = onToggleQr,
                            headlineContent = { Text(stringResource(R.string.lock_detail_generate_qr)) },
                            leadingContent = { Icon(Icons.TwoTone.QrCode2, null) },
                            trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
                        )
                    },
                ),
            )

            // 展开/折叠动画：复用 InstallerX Revived 各页面通用的手风琴写法
            // （fadeIn + expandVertically 自顶向下展开，fadeOut + shrinkVertically 向上收起）。
            QrDialogMaterial(show = showQr, lock = lock, onToggle = onToggleQr, onShare = actions.onShare, onCopyLink = actions.onCopyLink)

            // 第三组「信息」：字段为空时整行不出现。
            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                title = infoTitle,
                content = buildList {
                    val battery = lock.lockElectricity
                    val updated = lock.updateTime
                    if (battery != null || !updated.isNullOrBlank()) {
                        add {
                            SegmentedListItem(
                                headlineContent = { Text(stringResource(R.string.lock_detail_battery)) },
                                supportingContent = {
                                    Text(
                                        text = (battery?.let { "$it%" } ?: unknownText) +
                                            if (updated.isNullOrBlank()) {
                                                ""
                                            } else {
                                                " · " + stringResource(R.string.unlock_battery_updated, updated)
                                            }
                                    )
                                },
                            )
                        }
                    }
                    if (unitName.isNotBlank()) {
                        add {
                            SegmentedListItem(
                                headlineContent = { Text(stringResource(R.string.lock_detail_school)) },
                                supportingContent = { Text(unitName) },
                            )
                        }
                    }
                    if (locationText.isNotBlank()) {
                        add {
                            SegmentedListItem(
                                headlineContent = { Text(stringResource(R.string.lock_detail_location)) },
                                supportingContent = { Text(locationText) },
                            )
                        }
                    }
                    add {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.lock_detail_protocol)) },
                            supportingContent = { Text(protocolText) },
                        )
                    }
                    if (lock.lockTypeName.isNotBlank()) {
                        add {
                            SegmentedListItem(
                                headlineContent = { Text(stringResource(R.string.lock_detail_type_name)) },
                                supportingContent = { Text(lock.lockTypeName) },
                            )
                        }
                    }
                    if (lockNoText.isNotBlank()) {
                        add {
                            SegmentedListItem(
                                headlineContent = { Text(stringResource(R.string.lock_detail_lock_no)) },
                                supportingContent = { Text(lockNoText) },
                            )
                        }
                    }
                    if (lock.serviceUuid.isNotBlank()) {
                        add {
                            SegmentedListItem(
                                headlineContent = { Text(stringResource(R.string.lock_detail_service_uuid)) },
                                supportingContent = { Text(lock.serviceUuid) },
                            )
                        }
                    }
                    if (lock.writeUuid.isNotBlank()) {
                        add {
                            SegmentedListItem(
                                headlineContent = { Text(stringResource(R.string.lock_detail_write_uuid)) },
                                supportingContent = { Text(lock.writeUuid) },
                            )
                        }
                    }
                    if (lock.usernameMd5.isNotBlank()) {
                        add {
                            SegmentedListItem(
                                headlineContent = { Text(stringResource(R.string.lock_detail_user_hash)) },
                                supportingContent = {
                                    Text(
                                        lock.usernameMd5 + " · " + stringResource(
                                            if (accountSaved) {
                                                R.string.lock_detail_account_saved
                                            } else {
                                                R.string.lock_detail_account_unsaved
                                            }
                                        )
                                    )
                                },
                            )
                        }
                    }
                },
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LockDetailScreenMiuix(
    lock: Lock,
    isDefault: Boolean,
    accountSaved: Boolean,
    showQr: Boolean,
    onToggleQr: () -> Unit,
    actions: LockDetailActions,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val navigator = LocalNavigator.current

    MiuixScaffold(
        topBar = {
            SmallTopAppBar(
                title = lock.label,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    MiuixIconButton(
                        onClick = { navigator.pop() },
                        modifier = Modifier.padding(start = 12.dp),
                    ) {
                        MiuixIcon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = stringResource(R.string.lock_detail_back),
                            tint = colorScheme.onSurface,
                        )
                    }
                },
            )
        },
        popupHost = { },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(innerPadding)
                    .padding(horizontal = 12.dp),
            ) {
                val actionsTitle = stringResource(R.string.lock_detail_actions_group)
                val infoTitle = stringResource(R.string.lock_detail_info_group)
                val shareTitle = stringResource(R.string.lock_detail_share_group)
                val unknownText = stringResource(R.string.lock_label_unknown)
                val unitName = lock.schoolName.ifBlank { UnitCodes.format(lock.schoolNo) }
                val lockNoText = lock.mac.ifBlank { lock.lockNo }
                val locationText = locationTextOf(lock)
                val protocolText = protocolTextOf(lock)

                // 第一组「操作」：设为默认 / 删除；AES 型号额外多一个密钥入口。
                SmallTitle(
                    text = actionsTitle,
                    insideMargin = PaddingValues(start = 16.dp, top = 12.dp, end = 28.dp, bottom = 0.dp),
                )
                Card(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth(),
                ) {
                    SwitchPreference(
                        title = stringResource(R.string.locks_set_default),
                        startAction = {
                            MiuixIcon(
                                imageVector = if (isDefault) Icons.Filled.Star else Icons.Filled.StarBorder,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = stringResource(R.string.locks_set_default),
                                tint = colorScheme.onBackground,
                            )
                        },
                        checked = isDefault,
                        onCheckedChange = actions.onSetDefault,
                    )
                    if (lock.aesSupport == true) {
                        ArrowPreference(
                            title = stringResource(R.string.lock_detail_aes_key),
                            summary = stringResource(
                                if (lock.customAesKey.isNullOrBlank()) R.string.lock_detail_aes_key_unset
                                else R.string.lock_detail_aes_key_configured
                            ),
                            startAction = {
                                MiuixIcon(
                                    imageVector = Icons.Filled.Key,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = stringResource(R.string.lock_detail_aes_key),
                                    tint = colorScheme.onBackground,
                                )
                            },
                            onClick = actions.onShowAesKey,
                        )
                    }
                    BasicComponent(
                        title = stringResource(R.string.locks_delete),
                        startAction = {
                            MiuixIcon(
                                imageVector = Icons.Filled.Delete,
                                tint = colorScheme.error,
                                contentDescription = null,
                            )
                        },
                        onClick = actions.onDelete,
                    )
                }

                // 第二组「分享」：系统分享 / 复制链接 / 生成二维码。
                SmallTitle(
                    text = shareTitle,
                    insideMargin = PaddingValues(start = 16.dp, top = 12.dp, end = 28.dp, bottom = 0.dp),
                )
                Card(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth(),
                ) {
                    BasicComponent(
                        title = stringResource(R.string.lock_detail_share),
                        startAction = {
                            MiuixIcon(
                                imageVector = Icons.Filled.Share,
                                tint = colorScheme.onSurface,
                                contentDescription = null,
                            )
                        },
                        onClick = actions.onShare,
                    )
                    BasicComponent(
                        title = stringResource(R.string.lock_detail_copy_link),
                        startAction = {
                            MiuixIcon(
                                imageVector = Icons.Filled.ContentCopy,
                                tint = colorScheme.onSurface,
                                contentDescription = null,
                            )
                        },
                        onClick = actions.onCopyLink,
                    )
                    ArrowPreference(
                        title = stringResource(R.string.lock_detail_generate_qr),
                        startAction = {
                            MiuixIcon(
                                imageVector = Icons.Filled.QrCode2,
                                tint = colorScheme.onSurface,
                                contentDescription = null,
                            )
                        },
                        onClick = onToggleQr,
                    )
                }

                // 与 Material 分支完全相同的展开/折叠动画；卡片圆角、底色、内边距由 Miuix 主题 Card 提供。
                QrDialogMiuix(show = showQr, lock = lock, onToggle = onToggleQr, onShare = actions.onShare, onCopyLink = actions.onCopyLink)

                // 第三组「信息」：电量置顶突出，字段为空时整行不出现。
                SmallTitle(
                    text = infoTitle,
                    insideMargin = PaddingValues(start = 16.dp, top = 12.dp, end = 28.dp, bottom = 0.dp),
                )
                Card(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth(),
                ) {
                    val battery = lock.lockElectricity
                    val updated = lock.updateTime
                    if (battery != null || !updated.isNullOrBlank()) {
                        BasicComponent(
                            title = stringResource(R.string.lock_detail_battery),
                            summary = (battery?.let { "$it%" } ?: unknownText) +
                                if (updated.isNullOrBlank()) {
                                    ""
                                } else {
                                    " · " + stringResource(R.string.unlock_battery_updated, updated)
                                },
                        )
                    }
                    if (unitName.isNotBlank()) {
                        BasicComponent(
                            title = stringResource(R.string.lock_detail_school),
                            summary = unitName,
                        )
                    }
                    if (locationText.isNotBlank()) {
                        BasicComponent(
                            title = stringResource(R.string.lock_detail_location),
                            summary = locationText,
                        )
                    }
                    BasicComponent(
                        title = stringResource(R.string.lock_detail_protocol),
                        summary = protocolText,
                    )
                    if (lock.lockTypeName.isNotBlank()) {
                        BasicComponent(
                            title = stringResource(R.string.lock_detail_type_name),
                            summary = lock.lockTypeName,
                        )
                    }
                    if (lockNoText.isNotBlank()) {
                        BasicComponent(
                            title = stringResource(R.string.lock_detail_lock_no),
                            summary = lockNoText,
                        )
                    }
                    if (lock.serviceUuid.isNotBlank()) {
                        BasicComponent(
                            title = stringResource(R.string.lock_detail_service_uuid),
                            summary = lock.serviceUuid,
                        )
                    }
                    if (lock.writeUuid.isNotBlank()) {
                        BasicComponent(
                            title = stringResource(R.string.lock_detail_write_uuid),
                            summary = lock.writeUuid,
                        )
                    }
                    if (lock.usernameMd5.isNotBlank()) {
                        BasicComponent(
                            title = stringResource(R.string.lock_detail_user_hash),
                            summary = lock.usernameMd5 + " · " + stringResource(
                                if (accountSaved) {
                                    R.string.lock_detail_account_saved
                                } else {
                                    R.string.lock_detail_account_unsaved
                                }
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/**
 * 分享二维码图片（异步生成）。
 *
 * 二维码位图（ZXing 编码 + 像素绘制）不能在组合期同步生成：原先
 * `remember(lock) { QrCode.bitmap(lock.toShareUrl()) }` 会在首次展开二维码卡片时阻塞主线程。
 * 这里用 produceState 放到 Default 线程计算，并按 lock 缓存结果。
 */
@Composable
private fun QrDialogMaterial(
    show: Boolean,
    lock: Lock,
    onToggle: () -> Unit,
    onShare: () -> Unit,
    onCopyLink: () -> Unit,
) {
    if (!show) return
    val qrImage = rememberShareQrImage(lock)
    val qrAlpha by animateFloatAsState(
        targetValue = if (qrImage != null) 1f else 0f,
        label = "qrFade",
    )
    AlertDialog(
        onDismissRequest = onToggle,
        title = { Text(stringResource(R.string.lock_detail_share_qr)) },
        text = {
            WindowNavigationEventBridge()
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier.size(220.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    qrImage?.let { image ->
                        Image(
                            bitmap = image,
                            contentDescription = stringResource(R.string.lock_detail_share_qr),
                            modifier = Modifier
                                .size(220.dp)
                                .alpha(qrAlpha),
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.lock_detail_share_qr_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onShare(); onToggle() }) {
                Text(stringResource(R.string.lock_detail_share))
            }
        },
        dismissButton = {
            TextButton(onClick = { onCopyLink(); onToggle() }) {
                Text(stringResource(R.string.lock_detail_copy_link))
            }
        },
    )
}

@Composable
private fun QrDialogMiuix(
    show: Boolean,
    lock: Lock,
    onToggle: () -> Unit,
    onShare: () -> Unit,
    onCopyLink: () -> Unit,
) {
    if (!show) return
    val qrImage = rememberShareQrImage(lock)
    val qrAlpha by animateFloatAsState(
        targetValue = if (qrImage != null) 1f else 0f,
        label = "qrFade",
    )
    OverlayDialog(
        show = show,
        title = stringResource(R.string.lock_detail_share_qr),
        summary = stringResource(R.string.lock_detail_share_qr_hint),
        onDismissRequest = onToggle,
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier.size(220.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    qrImage?.let { image ->
                        Image(
                            bitmap = image,
                            contentDescription = stringResource(R.string.lock_detail_share_qr),
                            modifier = Modifier
                                .size(220.dp)
                                .alpha(qrAlpha),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.SpaceBetween) {
                    MiuixTextButton(
                        text = stringResource(R.string.lock_detail_share),
                        onClick = { onShare(); onToggle() },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(20.dp))
                    MiuixTextButton(
                        text = stringResource(R.string.lock_detail_copy_link),
                        onClick = { onCopyLink(); onToggle() },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
    )
}

@Composable
private fun rememberShareQrImage(lock: Lock): ImageBitmap? {
    val image by produceState<ImageBitmap?>(initialValue = null, key1 = lock) {
        value = withContext(Dispatchers.Default) {
            QrCode.bitmap(lock.toShareUrl()).asImageBitmap()
        }
    }
    return image
}

/**
 * AES 密钥输入弹窗（双主题）：沿用备份页 `PasswordDialog` 的同一套组件
 * （Miuix = OverlayDialog + TextField，Material = AlertDialog + OutlinedTextField）。
 *
 * 只做格式提示，不做十六进制校验阻断——非法值保存后开门时会被拦下并提示重新设置，
 * 这样用户可以先粘贴再修改，不会在弹窗里卡住。
 */
@Composable
private fun AesKeyDialog(
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    val title = stringResource(R.string.lock_detail_aes_key)
    val hint = stringResource(R.string.lock_detail_aes_key_hint)
    if (LocalUiMode.current == UiMode.Miuix) {
        OverlayDialog(
            show = true,
            title = title,
            summary = hint,
            onDismissRequest = onDismiss,
            content = {
                MiuixTextField(
                    modifier = Modifier.padding(bottom = 16.dp),
                    value = value,
                    maxLines = 1,
                    onValueChange = { value = it },
                )
                Row(horizontalArrangement = Arrangement.SpaceBetween) {
                    MiuixTextButton(
                        text = stringResource(R.string.cancel),
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(20.dp))
                    MiuixTextButton(
                        text = stringResource(R.string.confirm),
                        onClick = { onConfirm(value.trim()) },
                        modifier = Modifier.weight(1f),
                    )
                }
            },
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = {
                WindowNavigationEventBridge()
                Column {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        singleLine = true,
                        label = { Text(hint) },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { onConfirm(value.trim()) }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}