package com.yunmei.vibe.ui.screen.lockdetail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.yunmei.vibe.R
import com.yunmei.vibe.YunMeiApp
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
import com.yunmei.vibe.ui.component.material.TonalCard
import com.yunmei.vibe.ui.navigation3.LocalNavigator
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

@Composable
fun LockDetailScreen(label: String) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val confirmDialog = rememberConfirmDialog()
    val scope = rememberCoroutineScope()
    val container = YunMeiApp.app.container

    var lock by remember { mutableStateOf(container.lockStore.getAll().firstOrNull { it.label == label }) }
    var isDefault by remember { mutableStateOf(container.lockStore.getDefault()?.label == lock?.label) }
    var showQr by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        lock = container.lockStore.getAll().firstOrNull { it.label == label }
        isDefault = container.lockStore.getDefault()?.label == lock?.label
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

    val current = lock
    if (current == null) {
        navigator.pop()
        return
    }

    val deleteTitle = stringResource(R.string.locks_delete)
    val labelPrefix = stringResource(R.string.lock_detail_label)
    val confirmText = stringResource(R.string.confirm)
    val cancelText = stringResource(R.string.cancel)
    val actions = LockDetailActions(
        onSetDefault = { value ->
            container.lockStore.setDefault(if (value) current else null)
            isDefault = container.lockStore.getDefault()?.label == current.label
        },
        onDelete = {
            scope.launch {
                val result = confirmDialog.awaitConfirm(
                    title = deleteTitle,
                    content = labelPrefix + "：" + current.label,
                    confirm = confirmText,
                    dismiss = cancelText,
                )
                if (result == ConfirmResult.Confirmed) {
                    container.lockStore.remove(current.label)
                    navigator.pop()
                }
            }
        },
        onShare = { shareLock(current) },
        onCopyLink = { copyLink(current) },
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> LockDetailScreenMiuix(current, isDefault, showQr, onToggleQr = { showQr = !showQr }, actions)
        UiMode.Material -> LockDetailScreenMaterial(current, isDefault, showQr, onToggleQr = { showQr = !showQr }, actions)
    }
}

private data class LockDetailActions(
    val onSetDefault: (Boolean) -> Unit,
    val onDelete: () -> Unit,
    val onShare: () -> Unit,
    val onCopyLink: () -> Unit,
)

@Composable
private fun LockDetailScreenMaterial(
    lock: Lock,
    isDefault: Boolean,
    showQr: Boolean,
    onToggleQr: () -> Unit,
    actions: LockDetailActions,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val navigator = LocalNavigator.current

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(lock.label) },
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.lock_detail_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
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
            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                content = listOf(
                    {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.lock_detail_mac)) },
                            supportingContent = { Text(lock.mac) },
                        )
                    },
                    {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.lock_detail_service_uuid)) },
                            supportingContent = { Text(lock.serviceUuid) },
                        )
                    },
                    {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.lock_detail_write_uuid)) },
                            supportingContent = { Text(lock.writeUuid) },
                        )
                    },
                    {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.lock_detail_school)) },
                            supportingContent = { Text(UnitCodes.format(lock.schoolNo)) },
                        )
                    },
                    {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.lock_detail_lock_no)) },
                            supportingContent = { Text(lock.lockNo) },
                        )
                    },
                    {
                        SegmentedListItem(
                            headlineContent = { Text(stringResource(R.string.lock_detail_user_hash)) },
                            supportingContent = {
                                Text(
                                    lock.usernameMd5 +
                                        if (lock.usernameMd5.isBlank()) {
                                            ""
                                        } else {
                                            " · " + stringResource(
                                                if (YunMeiApp.app.container.accountStore.getByUsernameMd5(lock.usernameMd5) != null) {
                                                    R.string.lock_detail_account_saved
                                                } else {
                                                    R.string.lock_detail_account_unsaved
                                                }
                                            )
                                        }
                                )
                            },
                        )
                    },
                )
            )

            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                content = listOf {
                    SegmentedSwitchItem(
                        icon = if (isDefault) Icons.Filled.Star else Icons.Filled.StarBorder,
                        title = stringResource(R.string.locks_set_default),
                        checked = isDefault,
                        onCheckedChange = actions.onSetDefault,
                    )
                }
            )

            if (showQr) {
                TonalCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.lock_detail_share_qr),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        val bitmap = remember(lock) { QrCode.bitmap(lock.toShareUrl()) }
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.lock_detail_share_qr),
                            modifier = Modifier.size(220.dp),
                        )
                        Text(
                            text = stringResource(R.string.lock_detail_share_qr_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }

            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                content = listOf(
                    {
                        SegmentedListItem(
                            onClick = onToggleQr,
                            headlineContent = { Text(stringResource(R.string.lock_detail_share_qr)) },
                            leadingContent = { Icon(Icons.Filled.QrCode2, null) },
                        )
                    },
                    {
                        SegmentedListItem(
                            onClick = actions.onShare,
                            headlineContent = { Text(stringResource(R.string.lock_detail_share)) },
                            leadingContent = { Icon(Icons.Filled.Share, null) },
                        )
                    },
                    {
                        SegmentedListItem(
                            onClick = actions.onCopyLink,
                            headlineContent = { Text(stringResource(R.string.lock_detail_copy_link)) },
                            leadingContent = { Icon(Icons.Filled.ContentCopy, null) },
                        )
                    },
                    {
                        SegmentedListItem(
                            onClick = actions.onDelete,
                            headlineContent = { Text(stringResource(R.string.locks_delete)) },
                            leadingContent = {
                                Icon(
                                    Icons.Filled.Delete,
                                    null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                        )
                    },
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LockDetailScreenMiuix(
    lock: Lock,
    isDefault: Boolean,
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
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
                Card(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth(),
                ) {
                    BasicComponent(
                        title = stringResource(R.string.lock_detail_mac),
                        summary = lock.mac,
                    )
                    BasicComponent(
                        title = stringResource(R.string.lock_detail_service_uuid),
                        summary = lock.serviceUuid,
                    )
                    BasicComponent(
                        title = stringResource(R.string.lock_detail_write_uuid),
                        summary = lock.writeUuid,
                    )
                    BasicComponent(
                        title = stringResource(R.string.lock_detail_school),
                        summary = UnitCodes.format(lock.schoolNo),
                    )
                    BasicComponent(
                        title = stringResource(R.string.lock_detail_lock_no),
                        summary = lock.lockNo,
                    )
                    BasicComponent(
                        title = stringResource(R.string.lock_detail_user_hash),
                        summary = lock.usernameMd5 +
                            if (lock.usernameMd5.isBlank()) {
                                ""
                            } else {
                                " · " + stringResource(
                                    if (YunMeiApp.app.container.accountStore.getByUsernameMd5(lock.usernameMd5) != null) {
                                        R.string.lock_detail_account_saved
                                    } else {
                                        R.string.lock_detail_account_unsaved
                                    }
                                )
                            },
                    )
                }

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
                }

                if (showQr) {
                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            MiuixText(
                                text = stringResource(R.string.lock_detail_share_qr),
                                color = colorScheme.onSurface,
                            )
                            val bitmap = remember(lock) { QrCode.bitmap(lock.toShareUrl()) }
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = stringResource(R.string.lock_detail_share_qr),
                                modifier = Modifier.size(220.dp),
                            )
                            MiuixText(
                                text = stringResource(R.string.lock_detail_share_qr_hint),
                                color = colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }

                Card(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth(),
                ) {
                    BasicComponent(
                        title = stringResource(R.string.lock_detail_share_qr),
                        startAction = {
                            MiuixIcon(
                                imageVector = Icons.Filled.QrCode2,
                                tint = colorScheme.onSurface,
                                contentDescription = null,
                            )
                        },
                        onClick = onToggleQr,
                    )
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
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
