package com.yunmei.vibe.ui.screen.unlockrecords

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudUpload
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yunmei.vibe.R
import com.yunmei.vibe.data.model.UnlockRecordItem
import com.yunmei.vibe.ui.LocalUiMode
import com.yunmei.vibe.ui.UiMode
import com.yunmei.vibe.ui.component.material.SegmentedColumn
import com.yunmei.vibe.ui.component.material.SegmentedItem
import com.yunmei.vibe.ui.component.material.SegmentedListItem
import com.yunmei.vibe.ui.component.material.SegmentedSwitchItem
import com.yunmei.vibe.ui.component.material.TonalCard
import com.yunmei.vibe.ui.navigation.LocalNavigator
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/**
 * 开门记录页。
 *
 * 数据来自学校服务的 `lockrecord/getrecordbyschool`：服务端按登录账号聚合该账号名下
 * 所有门锁的记录，接口不接受门锁参数，所以页面不提供「按锁筛选」——这一点与官方一致。
 *
 * 页面骨架参考 KernelSU 的 SU 日志页，但去掉了搜索框、级别筛选、文件选择和右上角清空按钮，
 * 只保留「提示条 + 上传开关 + 记录列表」。
 */
@Composable
fun UnlockRecordsScreen() {
    val viewModel = viewModel<UnlockRecordsViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val actions = UnlockRecordsActions(
        onBack = { navigator.pop() },
        onRefresh = viewModel::refresh,
        onSetRecordUpload = viewModel::setRecordUpload,
    )
    when (LocalUiMode.current) {
        UiMode.Material -> UnlockRecordsMaterial(uiState, actions)
        UiMode.Miuix -> UnlockRecordsMiuix(uiState, actions)
    }
}

@Composable
private fun UnlockRecordsMaterial(
    state: UnlockRecordsUiState,
    actions: UnlockRecordsActions,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.unlock_records)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.unlock_records_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                // 提示条：照 KernelSU 的 WarningCard（errorContainer 底 + 16dp 内边距）。
                TonalCard(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        text = stringResource(R.string.unlock_records_source),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
            item {
                SegmentedColumn(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    content = listOf {
                        SegmentedSwitchItem(
                            icon = Icons.Rounded.CloudUpload,
                            title = stringResource(R.string.unlock_records_upload),
                            summary = stringResource(R.string.unlock_records_upload_summary),
                            checked = state.recordUpload,
                            onCheckedChange = actions.onSetRecordUpload,
                        )
                    },
                )
            }
            when {
                state.error != null -> item {
                    RecordHint(state.error)
                }
                state.records.isEmpty() && !state.isLoading -> item {
                    RecordHint(stringResource(R.string.unlock_records_empty))
                }
                else -> itemsIndexed(state.records) { index, record ->
                    SegmentedItem(index = index, count = state.records.size) {
                        SegmentedListItem(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            headlineContent = { Text(record.headline()) },
                            supportingContent = {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = record.realName.ifBlank { stringResource(R.string.lock_label_unknown) },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = record.lockTime,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun UnlockRecordsMiuix(
    state: UnlockRecordsUiState,
    actions: UnlockRecordsActions,
) {
    val scrollBehavior = MiuixScrollBehavior()

    MiuixScaffold(
        topBar = {
            MiuixTopAppBar(
                title = stringResource(R.string.unlock_records),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    MiuixIconButton(
                        onClick = actions.onBack,
                        modifier = Modifier.padding(start = 4.dp),
                    ) {
                        MiuixIcon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = stringResource(R.string.unlock_records_back),
                            tint = colorScheme.onSurface,
                        )
                    }
                },
            )
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            overscrollEffect = null,
        ) {
            item {
                // 提示条：对齐 KernelSU 的 WarningCard（默认卡片 + errorContainer 底色）。
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.defaultColors(
                        color = colorScheme.errorContainer,
                        contentColor = colorScheme.onErrorContainer,
                    ),
                ) {
                    MiuixText(
                        text = stringResource(R.string.unlock_records_source),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    SwitchPreference(
                        title = stringResource(R.string.unlock_records_upload),
                        summary = stringResource(R.string.unlock_records_upload_summary),
                        checked = state.recordUpload,
                        onCheckedChange = actions.onSetRecordUpload,
                    )
                }
            }
            when {
                state.error != null -> item { MiuixHint(state.error) }
                state.records.isEmpty() && !state.isLoading -> item {
                    MiuixHint(stringResource(R.string.unlock_records_empty))
                }
                else -> items(state.records) { record ->
                    MiuixRecordCard(record)
                }
            }
        }
    }
}

@Composable
private fun MiuixHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        MiuixText(
            text = text,
            color = colorScheme.onSurfaceVariantSummary,
        )
    }
}

/** 单条记录：楼栋房间一行、姓名与时间两行小字，与 SU 日志页的条目层级一致。 */
@Composable
private fun MiuixRecordCard(record: UnlockRecordItem) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            MiuixText(
                text = record.headline(),
                fontWeight = FontWeight(550),
                color = colorScheme.onSurface,
            )
            MiuixText(
                text = record.realName.ifBlank { stringResource(R.string.lock_label_unknown) },
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariantSummary,
            )
            MiuixText(
                text = record.lockTime,
                fontSize = 12.sp,
                fontWeight = FontWeight(550),
                color = colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

/** 主标题：楼栋 + 房间；都为空时退化为占位，避免出现空行。 */
@Composable
private fun UnlockRecordItem.headline(): String {
    val location = location
    return location.ifBlank { stringResource(R.string.lock_label_unknown) }
}
