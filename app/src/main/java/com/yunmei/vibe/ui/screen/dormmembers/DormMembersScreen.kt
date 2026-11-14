package com.yunmei.vibe.ui.screen.dormmembers

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yunmei.vibe.R
import com.yunmei.vibe.data.model.DormMember
import com.yunmei.vibe.ui.LocalUiMode
import com.yunmei.vibe.ui.UiMode
import com.yunmei.vibe.ui.component.material.TonalCard
import com.yunmei.vibe.ui.navigation.LocalNavigator
import com.yunmei.vibe.ui.navigation.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.ui.platform.LocalContext
import com.yunmei.vibe.ui.component.ActionMenuItem
import com.yunmei.vibe.ui.component.material.ActionMenuBottomSheet
import com.yunmei.vibe.ui.component.miuix.ActionMenuDialog

/**
 * 群组成员页。
 *
 * 接口 `dormuser/getbyloginuser` 按宿舍取人，页面只负责展示：左侧圆形头像、右侧姓名/手机号/有效期，
 * 点击卡片弹出宿舍与身份的完整信息。头像地址是官方硬编码的
 * `https://base.yunmeitech.com/headimg/<userId>.png`，项目里没有引入图片库（coil 只在版本目录里
 * 声明、没有加进依赖），所以这里用项目已有的 OkHttp 直接取字节解码，失败就退回姓名首字。
 */
@Composable
fun DormMembersScreen(label: String) {
    val viewModel = viewModel<DormMembersViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(label) { viewModel.load(label) }
    val navigator = LocalNavigator.current
    // 只在「当前就是本页」且 Activity 处于 RESUMED 时返回：避免生命周期切换或重复点击
    // 把栈底的主界面也弹掉（那种情况会表现为页面一闪而过）。
    val onBack: () -> Unit = dropUnlessResumed {
        if (navigator.current() is Route.DormMembers) navigator.pop()
    }
    when (LocalUiMode.current) {
        UiMode.Material -> DormMembersMaterial(uiState, onBack, viewModel::retry)
        UiMode.Miuix -> DormMembersMiuix(uiState, onBack, viewModel::retry)
    }
}

@Composable
private fun DormMembersMaterial(
    state: DormMembersUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    var menuMember by remember { mutableStateOf<DormMember?>(null) }

    Scaffold(
        containerColor = colorScheme.surfaceContainer,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.dorm_members)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.close),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.surfaceContainer,
                    scrolledContainerColor = colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        when (state) {
            DormMembersUiState.Loading -> CenterHint(Modifier.padding(innerPadding)) {
                CircularProgressIndicator()
            }

            is DormMembersUiState.Error -> CenterHint(Modifier.padding(innerPadding)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(8.dp))
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.dorm_members_retry)) }
                }
            }

            is DormMembersUiState.Success -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                if (state.stale) {
                    item {
                        Text(
                            text = stringResource(R.string.dorm_members_stale),
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
                if (state.members.isEmpty()) {
                    item {
                        CenterHint(Modifier.fillMaxWidth().padding(32.dp)) {
                            Text(
                                text = stringResource(R.string.dorm_members_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    items(state.members, key = { it.userId + "#" + it.roleType }) { member ->
                        MemberCardMaterial(member) { menuMember = member }
                    }
                }
            }
        }
    }

    menuMember?.let { target ->
        ActionMenuBottomSheet(
            items = memberMenuItems(target, context) { menuMember = null },
            onDismiss = { menuMember = null },
        )
    }
}

@Composable
private fun MemberCardMaterial(member: DormMember, onClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val phone = member.userTel
    TonalCard(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            MemberAvatar(
                member = member,
                size = 44.dp,
                containerColor = colorScheme.primaryContainer,
                contentColor = colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // 「姓名 · 电话」同一行；电话为空时只留姓名，也不挂点击（没有可拨的号码）。
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (phone.isBlank()) Modifier else Modifier.clickable(onClick = onClick)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = member.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (phone.isNotBlank()) {
                        // 分隔符与号码分开渲染：下划线只属于号码本身，不会串到「·」上。
                        Text(
                            text = " · ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                        Text(
                            text = phone,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            // 下划线作为「可点」的视觉提示，避免用户以为电话只是文本。
                            textDecoration = TextDecoration.Underline,
                        )
                    }
                    if (member.isAdmin) {
                        Spacer(Modifier.width(6.dp))
                        MemberTag(
                            text = stringResource(R.string.dorm_members_admin),
                            containerColor = colorScheme.primary,
                            contentColor = colorScheme.onPrimary,
                        )
                    }
                    if (!member.isEnabled) {
                        Spacer(Modifier.width(6.dp))
                        MemberTag(
                            text = stringResource(R.string.dorm_members_disabled),
                            containerColor = colorScheme.errorContainer,
                            contentColor = colorScheme.onErrorContainer,
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.dorm_members_location) + "：" +
                        member.location.ifBlank { stringResource(R.string.lock_label_unknown) },
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.dorm_members_valid) + "：" + validityText(member),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
                val education = educationText(member)
                if (education.isNotBlank()) {
                    Text(
                        text = education,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DormMembersMiuix(
    state: DormMembersUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current
    var menuMember by remember { mutableStateOf<DormMember?>(null) }

    MiuixScaffold(
        topBar = {
            MiuixTopAppBar(
                title = stringResource(R.string.dorm_members),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    MiuixIconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(start = 4.dp),
                    ) {
                        MiuixIcon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = stringResource(R.string.close),
                            tint = colorScheme.onSurface,
                        )
                    }
                },
            )
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        when (state) {
            DormMembersUiState.Loading -> CenterHint(Modifier.padding(innerPadding)) {
                CircularProgressIndicator(color = colorScheme.primary)
            }

            is DormMembersUiState.Error -> CenterHint(Modifier.padding(innerPadding)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MiuixText(
                        text = state.message,
                        fontSize = 14.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(Modifier.size(8.dp))
                    MiuixTextButton(
                        text = stringResource(R.string.dorm_members_retry),
                        onClick = onRetry,
                    )
                }
            }

            is DormMembersUiState.Success -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(bottom = 24.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                overscrollEffect = null,
            ) {
                if (state.stale) {
                    item {
                        MiuixText(
                            text = stringResource(R.string.dorm_members_stale),
                            fontSize = 12.sp,
                            color = colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
                if (state.members.isEmpty()) {
                    item {
                        CenterHint(Modifier.fillMaxWidth().padding(32.dp)) {
                            MiuixText(
                                text = stringResource(R.string.dorm_members_empty),
                                fontSize = 14.sp,
                                color = colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                } else {
                    items(state.members, key = { it.userId + "#" + it.roleType }) { member ->
                        MemberCardMiuix(member) { menuMember = member }
                    }
                }
            }
        }
    }

    menuMember?.let { target ->
        ActionMenuDialog(
            show = true,
            title = target.displayName,
            items = memberMenuItems(target, context) { menuMember = null },
            onDismissRequest = { menuMember = null },
        )
    }
}

@Composable
private fun MemberCardMiuix(member: DormMember, onClick: () -> Unit) {
    val phone = member.userTel
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            MemberAvatar(
                member = member,
                size = 44.dp,
                containerColor = colorScheme.primaryContainer,
                contentColor = colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (phone.isBlank()) Modifier else Modifier.clickable(onClick = onClick)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MiuixText(
                        text = member.displayName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight(550),
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (phone.isNotBlank()) {
                        // 分隔符与号码分开渲染：下划线只属于号码本身。
                        MiuixText(
                            text = " · ",
                            fontSize = 14.sp,
                            color = colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                        )
                        MiuixText(
                            text = phone,
                            fontSize = 14.sp,
                            color = colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textDecoration = TextDecoration.Underline,
                        )
                    }
                    if (member.isAdmin) {
                        Spacer(Modifier.width(6.dp))
                        MemberTag(
                            text = stringResource(R.string.dorm_members_admin),
                            containerColor = colorScheme.primary,
                            contentColor = colorScheme.onPrimary,
                        )
                    }
                    if (!member.isEnabled) {
                        Spacer(Modifier.width(6.dp))
                        MemberTag(
                            text = stringResource(R.string.dorm_members_disabled),
                            containerColor = colorScheme.errorContainer,
                            contentColor = colorScheme.onErrorContainer,
                        )
                    }
                }
                MiuixText(
                    text = stringResource(R.string.dorm_members_location) + "：" +
                        member.location.ifBlank { stringResource(R.string.lock_label_unknown) },
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                )
                MiuixText(
                    text = stringResource(R.string.dorm_members_valid) + "：" + validityText(member),
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary,
                )
                val education = educationText(member)
                if (education.isNotBlank()) {
                    MiuixText(
                        text = education,
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}

@Composable
private fun CenterHint(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        content()
    }
}

/** 圆形小标签：「管理员」「已停用」。 */
@Composable
private fun MemberTag(text: String, containerColor: Color, contentColor: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(containerColor)
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            color = contentColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * 头像：优先用官方 headimg 图片，加载失败/没有 userId 时退回姓名首字。
 *
 * 项目没有引入图片库（coil 只在版本目录里声明），这里用 OkHttp 取字节后按 128px 采样解码，
 * 结果放进进程内缓存，列表滚动时不会反复请求。
 */
@Composable
private fun MemberAvatar(
    member: DormMember,
    size: Dp,
    containerColor: Color,
    contentColor: Color,
) {
    val bitmap = rememberAvatarImage(member.avatarUrl)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (bitmap == null) containerColor else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = member.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(CircleShape),
            )
        } else {
            Text(
                text = member.initial,
                color = contentColor,
                fontSize = (size.value * 0.4f).sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun rememberAvatarImage(url: String): ImageBitmap? {
    var bitmap by remember(url) { mutableStateOf(avatarCache[url]) }
    LaunchedEffect(url) {
        if (url.isBlank()) return@LaunchedEffect
        avatarCache[url]?.let { cached ->
            bitmap = cached
            return@LaunchedEffect
        }
        // 已经失败过的地址不再重试，避免列表每次重组都打一遍超时请求。
        if (url in avatarFailures) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) { downloadAvatar(url) }
        if (loaded == null) {
            avatarFailures.add(url)
        } else {
            avatarCache[url] = loaded
            bitmap = loaded
        }
    }
    return bitmap
}

private val avatarClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
}

/**
 * 进程内头像缓存，外加一个「已失败地址」集合。
 *
 * 失败的地址不能以 null 值塞进 [avatarCache]：`ConcurrentHashMap` 不允许 null 值，
 * `put(url, null)` 会抛 NPE，而这里跑在 Compose 的协程里，异常直接带崩整个进程
 * （表现为进入群组成员页立刻退回桌面）。所以成功与失败分开存。
 */
private val avatarCache = ConcurrentHashMap<String, ImageBitmap>()
private val avatarFailures: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

private fun downloadAvatar(url: String): ImageBitmap? = runCatching {
    val request = Request.Builder().url(url).build()
    avatarClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) return@use null
        val bytes = response.body.bytes()
        if (bytes.isEmpty()) return@use null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@use null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= AVATAR_TARGET_PX) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
    }
}.getOrNull()

private const val AVATAR_TARGET_PX = 128

/** 服务端日期形如 `2021-08-17 12:26:21`，卡片上只保留日期。 */
private fun formatDate(raw: String): String {
    if (raw.isBlank()) return ""
    val date = raw.substringBefore(' ')
    return if (date.length >= 10) date else raw
}

/**
 * 「学院：X · 班级：Y」一行；两项都没有时返回空串，调用方不渲染。
 *
 * 成员接口本身不含学籍字段，目前只有当前登录账号自己那一条能从个人资料接口补到值。
 */
@Composable
private fun educationText(member: DormMember): String = listOfNotNull(
    member.college?.takeIf { it.isNotBlank() }
        ?.let { stringResource(R.string.dorm_members_college) + "：" + it },
    member.className?.takeIf { it.isNotBlank() }
        ?.let { stringResource(R.string.dorm_members_class) + "：" + it },
).joinToString(" · ")

@Composable
private fun validityText(member: DormMember): String {
    val start = formatDate(member.startTime)
    val end = formatDate(member.endTime)
    return when {
        start.isBlank() && end.isBlank() -> stringResource(R.string.lock_label_unknown)
        end.isBlank() -> start
        start.isBlank() -> end
        else -> stringResource(R.string.dorm_members_valid_range, start, end)
    }
}

/** 成员操作菜单：拨号与保存到通讯录，两端主题共用同一份条目。 */
@Composable
private fun memberMenuItems(
    member: DormMember,
    context: Context,
    onDone: () -> Unit,
): List<ActionMenuItem> = listOf(
    ActionMenuItem(
        icon = Icons.Rounded.Call,
        label = stringResource(R.string.dorm_members_dial),
        onClick = {
            onDone()
            context.dialPhone(member.userTel)
        },
    ),
    ActionMenuItem(
        icon = Icons.Rounded.PersonAdd,
        label = stringResource(R.string.dorm_members_save_contact),
        onClick = {
            onDone()
            context.saveContact(member.displayName, member.userTel)
        },
    ),
)

/** 拨号只填号码不直接拨出，用户仍可在拨号盘里取消。 */
private fun Context.dialPhone(phone: String) {
    launchOrToast(Intent(Intent.ACTION_DIAL, "tel:$phone".toUri()))
}

/** 拉起系统「新建联系人」界面并预填姓名与号码。 */
private fun Context.saveContact(name: String, phone: String) {
    val intent = Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI).apply {
        putExtra(ContactsContract.Intents.Insert.NAME, name)
        putExtra(ContactsContract.Intents.Insert.PHONE, phone)
    }
    launchOrToast(intent)
}

/**
 * 隐式 Intent 兜底：设备没有拨号/联系人应用时 `startActivity` 会抛
 * ActivityNotFoundException，这里统一转成一句提示，避免直接崩溃。
 */
private fun Context.launchOrToast(intent: Intent) {
    val launched = runCatching {
        startActivity(intent)
        true
    }.getOrDefault(false)
    if (!launched) {
        Toast.makeText(this, getString(R.string.dorm_members_action_failed), Toast.LENGTH_SHORT).show()
    }
}
