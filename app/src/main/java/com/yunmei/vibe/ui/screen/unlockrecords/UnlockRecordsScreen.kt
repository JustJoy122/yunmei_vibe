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
import androidx.compose.material3.CircularProgressIndicator
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
                // 提示条：只陈述数据来源，用 tertiaryContainer（提示而非错误）区分于真正的失败态。
                TonalCard(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Text(
                        text = stringResource(R.string.unlock_records_source),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
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
                state.errorMessage != null -> item {
                    RecordErrorCard(state.errorMessage)
                }
                state.isLoading -> item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
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

/**
 * 请求失败的显性反馈：用错误色卡片贴在列表位置，而不是让页面只剩空白。
 *
 * 内容来自 ViewModel 翻译过的异常（HTTP 码 / 服务端 msg），用户不必连电脑抓日志也能看到原因。
 */
@Composable
private fun RecordErrorCard(message: String) {
    TonalCard(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        containerColor = MaterialTheme.colorScheme.errorContainer,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
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
                // 提示条：与 Material 侧同色（tertiaryContainer），语义是「数据来源提示」。
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.defaultColors(
                        color = colorScheme.tertiaryContainer,
                        contentColor = colorScheme.onTertiaryContainer,
                    ),
                ) {
                    MiuixText(
                        text = stringResource(R.string.unlock_records_source),
                        fontSize = 14.sp,
                        color = colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    SwitchPreference(
                        title = stringResource(R.string.unlock_records_upload),
                        summary = stringResource(R.string.unlock_records_upload_summary),
                        // 与 Material 侧同图标，保持两套主题视觉一致。
                        startAction = {
                            Icon(
                                Icons.Rounded.CloudUpload,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = stringResource(R.string.unlock_records_upload),
                                tint = colorScheme.onBackground,
                            )
                        },
                        checked = state.recordUpload,
                        onCheckedChange = actions.onSetRecordUpload,
                    )
                }
            }
            when {
                state.errorMessage != null -> item { MiuixErrorCard(state.errorMessage) }
                state.isLoading -> item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = colorScheme.primary)
                    }
                }
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
private fun MiuixErrorCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = colorScheme.errorContainer,
            contentColor = colorScheme.onErrorContainer,
        ),
    ) {
        MiuixText(
            text = message,
            fontSize = 14.sp,
            color = colorScheme.onErrorContainer,
            modifier = Modifier.padding(16.dp),
        )
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
