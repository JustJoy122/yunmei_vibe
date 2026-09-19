package com.yunmei.vibe.ui.screen.lockdetail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.twotone.ContentCopy
import androidx.compose.material.icons.twotone.Delete
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
import androidx.compose.ui.graphics.ImageBitmap
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
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/** 二维码展开/折叠动画时长（200–300ms 区间，与 InstallerX Revived 的手风琴写法一致）。 */
private const val QR_ANIM_DURATION_MS = 250

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
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
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
                                                if (rememberAccountSaved(lock.usernameMd5)) {
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
                        icon = if (isDefault) Icons.TwoTone.Star else Icons.TwoTone.StarBorder,
                        title = stringResource(R.string.locks_set_default),
                        checked = isDefault,
                        onCheckedChange = actions.onSetDefault,
                    )
                }
            )

            // 「分享二维码」开关行单独成组：二维码紧贴其下方展开（手风琴式），不再出现在按钮上方。
            SegmentedColumn(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                content = listOf {
                    SegmentedListItem(
                        onClick = onToggleQr,
                        headlineContent = { Text(stringResource(R.string.lock_detail_share_qr)) },
                        leadingContent = { Icon(Icons.TwoTone.QrCode2, null) },
                    )
                }
            )

            // 展开/折叠动画：复用 InstallerX Revived 各页面通用的手风琴写法
            // （fadeIn + expandVertically 自顶向下展开，fadeOut + shrinkVertically 向上收起）。
            // 卡片圆角、底色、内边距仍由主题组件 TonalCard 提供，不硬编码。
            AnimatedVisibility(
                visible = showQr,
                enter = fadeIn(tween(QR_ANIM_DURATION_MS, easing = FastOutSlowInEasing)) +
                    expandVertically(
                        animationSpec = tween(QR_ANIM_DURATION_MS, easing = FastOutSlowInEasing),
                        expandFrom = Alignment.Top,
                    ),
                exit = fadeOut(tween(QR_ANIM_DURATION_MS, easing = FastOutSlowInEasing)) +
                    shrinkVertically(
                        animationSpec = tween(QR_ANIM_DURATION_MS, easing = FastOutSlowInEasing),
                        shrinkTowards = Alignment.Top,
                    ),
            ) {
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
                        // 二维码异步生成：外层固定 220dp 容器预留高度，展开动画期间不会二次跳变；
                        // 图片就绪后淡入（未就绪时容器为空但高度不变）。
                        val qrImage = rememberShareQrImage(lock)
                        Box(
                            modifier = Modifier.size(220.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            AnimatedVisibility(
                                visible = qrImage != null,
                                enter = fadeIn(),
                                exit = fadeOut(),
                            ) {
                                qrImage?.let { image ->
                                    Image(
                                        bitmap = image,
                                        contentDescription = stringResource(R.string.lock_detail_share_qr),
                                        modifier = Modifier.size(220.dp),
                                    )
                                }
                            }
                        }
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
                            onClick = actions.onDelete,
                            headlineContent = { Text(stringResource(R.string.locks_delete)) },
                            leadingContent = {
                                Icon(
                                    Icons.TwoTone.Delete,
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
                                    if (rememberAccountSaved(lock.usernameMd5)) {
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

                // 「分享二维码」开关行单独成卡：二维码紧贴其下方展开（手风琴式），不再出现在按钮上方。
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
                }

                // 与 Material 分支完全相同的展开/折叠动画；卡片圆角、底色、内边距由 Miuix 主题 Card 提供。
                AnimatedVisibility(
                    visible = showQr,
                    enter = fadeIn(tween(QR_ANIM_DURATION_MS, easing = FastOutSlowInEasing)) +
                        expandVertically(
                            animationSpec = tween(QR_ANIM_DURATION_MS, easing = FastOutSlowInEasing),
                            expandFrom = Alignment.Top,
                        ),
                    exit = fadeOut(tween(QR_ANIM_DURATION_MS, easing = FastOutSlowInEasing)) +
                        shrinkVertically(
                            animationSpec = tween(QR_ANIM_DURATION_MS, easing = FastOutSlowInEasing),
                            shrinkTowards = Alignment.Top,
                        ),
                ) {
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
                            // 二维码异步生成：外层固定 220dp 容器预留高度，展开动画期间不会二次跳变；
                            // 图片就绪后淡入（未就绪时容器为空但高度不变）。
                            val qrImage = rememberShareQrImage(lock)
                            Box(
                                modifier = Modifier.size(220.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                AnimatedVisibility(
                                    visible = qrImage != null,
                                    enter = fadeIn(),
                                    exit = fadeOut(),
                                ) {
                                    qrImage?.let { image ->
                                        Image(
                                            bitmap = image,
                                            contentDescription = stringResource(R.string.lock_detail_share_qr),
                                            modifier = Modifier.size(220.dp),
                                        )
                                    }
                                }
                            }
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

/**
 * 该门锁账号是否已保存。
 *
 * 账号存放在加密存储（EncryptedSharedPreferences + JSON 解码）里，属于 IO 操作，
 * 不能在组合期直接读取（原先在组合中同步读取会阻塞主线程、且每次重组都执行一次）。
 * 这里用 produceState 放到 IO 线程计算，并按 usernameMd5 缓存结果。
 */
@Composable
private fun rememberAccountSaved(usernameMd5: String): Boolean {
    val saved by produceState(initialValue = false, key1 = usernameMd5) {
        value = withContext(Dispatchers.IO) {
            YunMeiApp.app.container.accountStore.getByUsernameMd5(usernameMd5) != null
        }
    }
    return saved
}

/**
 * 分享二维码图片（异步生成）。
 *
 * 二维码位图（ZXing 编码 + 像素绘制）不能在组合期同步生成：原先
 * `remember(lock) { QrCode.bitmap(lock.toShareUrl()) }` 会在首次展开二维码卡片时阻塞主线程。
 * 这里用 produceState 放到 Default 线程计算，并按 lock 缓存结果。
 */
@Composable
private fun rememberShareQrImage(lock: Lock): ImageBitmap? {
    val image by produceState<ImageBitmap?>(initialValue = null, key1 = lock) {
        value = withContext(Dispatchers.Default) {
            QrCode.bitmap(lock.toShareUrl()).asImageBitmap()
        }
    }
    return image
}
