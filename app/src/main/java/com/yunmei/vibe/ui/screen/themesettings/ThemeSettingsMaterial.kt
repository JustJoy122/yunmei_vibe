package com.yunmei.vibe.ui.screen.themesettings

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.twotone.MenuOpen
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.twotone.Animation
import androidx.compose.material.icons.twotone.AspectRatio
import androidx.compose.material.icons.twotone.Brightness1
import androidx.compose.material.icons.twotone.Brightness3
import androidx.compose.material.icons.twotone.Brightness4
import androidx.compose.material.icons.twotone.Brightness7
import androidx.compose.material.icons.twotone.DesignServices
import androidx.compose.material.icons.twotone.Home
import androidx.compose.material.icons.twotone.Lock
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material.icons.twotone.Style
import androidx.compose.material.icons.twotone.SwapHoriz
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec
import com.yunmei.vibe.R
import com.yunmei.vibe.ui.animation.predictiveback.PredictiveBackAnimation
import com.yunmei.vibe.ui.animation.predictiveback.PredictiveBackExitDirection
import com.yunmei.vibe.ui.component.bottombar.BottomBarDestinationMaterial
import com.yunmei.vibe.ui.component.material.SegmentedColumn
import com.yunmei.vibe.ui.component.material.SegmentedDropdownItem
import com.yunmei.vibe.ui.component.material.SegmentedSwitchItem
import com.yunmei.vibe.ui.component.material.TonalCard
import com.yunmei.vibe.ui.theme.ColorMode
import com.yunmei.vibe.ui.theme.keyColorOptions
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 固定色色板的进程级缓存（与上游 InstallerX `ColorPalatteCard.kt` 的 colorSchemeCache 同款做法）：
 * material-kolor 生成一套色板要跑完整的 HCT 推导，比较重。缓存后重新进入页面、或横向滚动让
 * 已算过的色板重新可见时都能立刻出结果，不必再算一遍。
 */
private val colorSchemeCache = ConcurrentHashMap<String, ColorScheme>()

/** 预热时预先计算的色板档数：覆盖首屏可见的预览 + 前若干格，其余在滚动可见时按需计算。 */
private const val PREWARM_COLOR_COUNT = 6

/**
 * 预热主题设置的色板缓存（后台线程，应用启动首帧之后调用）。
 *
 * 为什么需要：主题设置页的预览卡与 16 格色板都是异步推导，若等**进入页面时**才开始算，
 * 这批 CPU 重活会与页面进入动画的合成抢资源（`Dispatchers.Default` 多核并发，冷缓存时
 * 首屏多格同时开算），表现为"进入主题设置卡顿"；也会让色板出现"先占位、后换色"的观感。
 * 提前在启动后空闲时算好写入进程级缓存，进入页面时 `produceState` 的 initialValue 直接命中，
 * 后台不再有任何计算。
 *
 * 预览卡与色板格共用同一缓存键（`<argb>_<style>_<spec>_<isDark>`；「跟随系统」档的
 * `Color.Unspecified.toArgb()` 与偏好里的 keyColor=0 都是 "0"，故两边一致），因此预热结果两边都能命中。
 */
internal suspend fun prewarmThemePaletteCache(
    context: Context,
    isDark: Boolean,
    paletteStyle: PaletteStyle,
    colorSpec: ColorSpec.SpecVersion,
) = withContext(Dispatchers.Default) {
    val targets = buildList {
        add(Color.Unspecified)
        keyColorOptions.take(PREWARM_COLOR_COUNT).forEach { add(Color(it)) }
    }
    targets.forEach { target ->
        val cacheKey = "${target.toArgb()}_${paletteStyle.name}_${colorSpec.name}_$isDark"
        if (colorSchemeCache.containsKey(cacheKey)) return@forEach
        colorSchemeCache[cacheKey] = if (target == Color.Unspecified) {
            val base = if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            dynamicColorScheme(
                seedColor = Color.Unspecified,
                isDark = isDark,
                style = paletteStyle,
                specVersion = colorSpec,
                primary = base.primary,
                secondary = base.secondary,
                tertiary = base.tertiary,
                neutral = base.surface,
                neutralVariant = base.surfaceVariant,
                error = base.error,
            )
        } else {
            dynamicColorScheme(
                seedColor = target,
                isDark = isDark,
                style = paletteStyle,
                specVersion = colorSpec,
            )
        }
    }
}

@Composable
fun ThemeSettingsMaterial(
    state: ThemeSettingsUiState,
    actions: ThemeSettingsActions,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val uiState = state.uiState
    val currentColorMode = state.currentColorMode
    val currentKeyColor = uiState.keyColor
    val colorStyle = state.currentPaletteStyle
    val colorSpec = state.currentColorSpec
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        scrollBehavior.state.heightOffset = scrollBehavior.state.heightOffsetLimit
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = actions.onBack
                    ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null) }
                },
                title = { Text(stringResource(R.string.settings_theme)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                ),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { paddingValues ->
        val navBars = WindowInsets.navigationBars.asPaddingValues()
        val captionBar = WindowInsets.captionBar.asPaddingValues()

        Column(
            modifier = Modifier
                .padding(paddingValues)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val isDark = currentColorMode.isDark || currentColorMode.isSystem && isSystemInDarkTheme()
            ThemePreviewCard(
                keyColor = currentKeyColor,
                isDark = isDark,
                paletteStyle = colorStyle,
                colorSpec = colorSpec,
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    ColorButtonMaterial(
                        color = Color.Unspecified,
                        isSelected = currentKeyColor == 0,
                        isDark = isDark,
                        paletteStyle = colorStyle,
                        colorSpec = colorSpec,
                        onClick = {
                            actions.onSetKeyColor(0)
                        }
                    )
                }

                items(keyColorOptions, key = { it }) { color ->
                    ColorButtonMaterial(
                        color = Color(color),
                        isSelected = currentKeyColor == color,
                        isDark = isDark,
                        paletteStyle = colorStyle,
                        colorSpec = colorSpec,
                        onClick = {
                            actions.onSetKeyColor(color)
                        }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 深色模式：横排图标选择栏（跟随系统 / 浅色 / 深色），AMOLED 已拆为独立开关。
                val options = listOf(
                    ColorMode.SYSTEM to stringResource(R.string.settings_theme_mode_system),
                    ColorMode.LIGHT to stringResource(R.string.settings_theme_mode_light),
                    ColorMode.DARK to stringResource(R.string.settings_theme_mode_dark),
                )
                // Monet 开启时内部模式为 3/4/5，归一化后比较，保证选中态正确。
                val baseColorMode = if (currentColorMode.isMonet) {
                    ColorMode.fromValue(currentColorMode.toNonMonetMode())
                } else {
                    currentColorMode
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                ) {
                    options.forEachIndexed { index, (mode, label) ->
                        ToggleButton(
                            checked = baseColorMode == mode,
                            onCheckedChange = {
                                if (it) {
                                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                    actions.onSetColorMode(mode)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .semantics { role = Role.RadioButton },
                            shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                        ) {
                            Icon(
                                imageVector = when (mode) {
                                    ColorMode.SYSTEM -> Icons.TwoTone.Brightness4
                                    ColorMode.LIGHT -> Icons.TwoTone.Brightness7
                                    ColorMode.DARK -> Icons.TwoTone.Brightness3
                                    else -> Icons.TwoTone.Brightness4
                                },
                                contentDescription = label
                            )
                        }
                    }
                }

                SegmentedColumn(
                    modifier = Modifier.padding(top = 4.dp),
                    content = listOf(
                        {
                            SegmentedSwitchItem(
                                icon = Icons.TwoTone.Brightness1,
                                title = stringResource(R.string.settings_amoled),
                                summary = stringResource(R.string.settings_amoled_summary),
                                checked = uiState.amoled,
                                onCheckedChange = actions.onSetAmoled,
                            )
                        },
                    )
                )

                SegmentedColumn(
                    modifier = Modifier.padding(top = 4.dp),
                    content = listOf(
                        {
                            val styles = PaletteStyle.entries
                            SegmentedDropdownItem(
                                icon = Icons.TwoTone.Style,
                                title = stringResource(R.string.settings_color_style),
                                items = styles.map { it.name },
                                selectedIndex = styles.indexOf(colorStyle),
                                onItemSelected = { index ->
                                    actions.onSetColorStyle(styles[index].name)
                                }
                            )
                        },
                        {
                            val specs = ColorSpec.SpecVersion.entries
                            SegmentedDropdownItem(
                                icon = Icons.TwoTone.DesignServices,
                                title = stringResource(R.string.settings_color_spec),
                                items = specs.map { it.name },
                                selectedIndex = specs.indexOf(colorSpec).coerceAtLeast(0),
                                onItemSelected = { index ->
                                    actions.onSetColorSpec(specs[index].name)
                                }
                            )
                        }
                    )
                )

                // 返回动画选项（顺序必须与枚举 entries 一致，避免 selectedIndex 错位）
                val animations = PredictiveBackAnimation.entries
                val directions = PredictiveBackExitDirection.entries
                val currentAnimation = PredictiveBackAnimation.fromValueOrDefault(uiState.predictiveBackAnimation)
                val currentDirection = PredictiveBackExitDirection.fromValueOrDefault(uiState.predictiveBackExitDirection)
                val animationItems = listOf(
                    stringResource(R.string.settings_predictive_back_animation_aosp),
                    stringResource(R.string.settings_predictive_back_animation_miuix),
                    stringResource(R.string.settings_predictive_back_animation_scale),
                    stringResource(R.string.settings_predictive_back_animation_classic),
                )
                val directionItems = listOf(
                    stringResource(R.string.settings_predictive_back_direction_follow_gesture),
                    stringResource(R.string.settings_predictive_back_direction_always_right),
                    stringResource(R.string.settings_predictive_back_direction_always_left),
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    // 开关 + 菜单联动（结构对齐「莫奈取色 → 强调色」）：开关关闭即不启用返回动画，菜单整组收起。
                    SegmentedColumn(
                        modifier = Modifier.padding(top = 4.dp),
                        content = listOf(
                            {
                                SegmentedSwitchItem(
                                    icon = Icons.AutoMirrored.TwoTone.MenuOpen,
                                    title = stringResource(id = R.string.settings_enable_predictive_back),
                                    checked = uiState.enablePredictiveBack,
                                    onCheckedChange = actions.onSetEnablePredictiveBack
                                )
                            }
                        )
                    )

                    AnimatedVisibility(visible = uiState.enablePredictiveBack) {
                        // 动画样式 + 返回方向放进同一张 SegmentedColumn（与上方「色彩风格 / 色彩标准」完全同款结构）：
                        // 两者是同一张卡里的相邻两行，各自把弹出菜单锚定在自己的行上，不会互相压盖；
                        // 方向行隐藏时用 visibleLen 修正圆角分组，保证只剩一行时卡片圆角仍然正确。
                        SegmentedColumn(
                            modifier = Modifier.padding(top = 4.dp),
                            visibleLen = if (currentAnimation == PredictiveBackAnimation.SCALE) 2 else 1,
                            content = listOf(
                                {
                                    SegmentedDropdownItem(
                                        icon = Icons.TwoTone.Animation,
                                        title = stringResource(R.string.settings_predictive_back_animation),
                                        items = animationItems,
                                        selectedIndex = animations.indexOf(currentAnimation).coerceAtLeast(0),
                                        onItemSelected = { index ->
                                            actions.onSetPredictiveBackAnimation(animations[index].value)
                                        }
                                    )
                                },
                                {
                                    // 返回方向仅对「缩放」档生效，与上游 InstallerX 的显示逻辑一致。
                                    AnimatedVisibility(visible = currentAnimation == PredictiveBackAnimation.SCALE) {
                                        SegmentedDropdownItem(
                                            icon = Icons.TwoTone.SwapHoriz,
                                            title = stringResource(R.string.settings_predictive_back_direction),
                                            items = directionItems,
                                            selectedIndex = directions.indexOf(currentDirection).coerceAtLeast(0),
                                            onItemSelected = { index ->
                                                actions.onSetPredictiveBackExitDirection(directions[index].value)
                                            }
                                        )
                                    }
                                },
                            )
                        )
                    }
                }

                // 按模板逻辑：模糊/悬浮底栏/液态玻璃为 Miuix 主题独占（Material 底栏不使用
                // FloatingBottomBar，Material 页面不使用 BlurredBar），Material 模式下不显示。

                TonalCard(modifier = Modifier.padding(top = 4.dp)) {
                    var sliderValue by remember(uiState.pageScale) { mutableFloatStateOf(uiState.pageScale) }

                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.TwoTone.AspectRatio,
                                contentDescription = stringResource(id = R.string.settings_page_scale),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_page_scale),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "${(sliderValue * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Slider(
                            value = sliderValue,
                            onValueChange = { sliderValue = it },
                            onValueChangeFinished = { actions.onSetPageScale(sliderValue) },
                            valueRange = 0.8f..1.1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp + navBars.calculateBottomPadding() + captionBar.calculateBottomPadding()))
        }
    }
}

@SuppressLint("ConfigurationScreenWidthHeight", "NewApi")
@Composable
private fun ThemePreviewCard(
    keyColor: Int,
    isDark: Boolean,
    paletteStyle: PaletteStyle = PaletteStyle.TonalSpot,
    colorSpec: ColorSpec.SpecVersion = ColorSpec.SpecVersion.SPEC_2021,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.toFloat()
    val screenHeight = configuration.screenHeightDp.toFloat()
    val screenRatio = screenWidth / screenHeight
    val dynamicColor = keyColor == 0

    // 预览色板与色板格共用同一套进程级缓存，并放到 Default 线程计算：
    // 进入主题设置的首帧不再同步跑 material-kolor 全量推导（此前是进入卡顿的主因）。
    // 计算完成前用当前应用主题配色占位，避免预览卡出现空白/闪烁。
    val previewKey = remember(keyColor, isDark, paletteStyle, colorSpec) {
        "${keyColor}_${paletteStyle.name}_${colorSpec.name}_$isDark"
    }
    val previewScheme by produceState<ColorScheme?>(
        initialValue = colorSchemeCache[previewKey],
        key1 = previewKey,
    ) {
        val cached = colorSchemeCache[previewKey]
        if (cached != null) {
            value = cached
        } else {
            val computed = withContext(Dispatchers.Default) {
                if (dynamicColor) {
                    val base = if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                    dynamicColorScheme(
                        seedColor = Color.Unspecified,
                        isDark = isDark,
                        style = paletteStyle,
                        specVersion = colorSpec,
                        primary = base.primary,
                        secondary = base.secondary,
                        tertiary = base.tertiary,
                        neutral = base.surface,
                        neutralVariant = base.surfaceVariant,
                        error = base.error,
                    )
                } else {
                    dynamicColorScheme(
                        seedColor = Color(keyColor),
                        isDark = isDark,
                        style = paletteStyle,
                        specVersion = colorSpec,
                    )
                }
            }
            colorSchemeCache[previewKey] = computed
            value = computed
        }
    }
    val colorScheme = previewScheme ?: MaterialTheme.colorScheme

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.4f)
                .aspectRatio(screenRatio),
            color = colorScheme.background,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column {
                // top bar
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.TopStart
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 12.dp, top = 16.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(id = R.string.app_name),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurface
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.TopStart
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        PreviewBlock(
                            color = colorScheme.secondaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        )
                        PreviewBlock(
                            color = colorScheme.surfaceContainerHighest,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(34.dp)
                        )
                        PreviewBlock(
                            color = colorScheme.surfaceContainerHighest,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        )
                    }
                }

                // bottom bar：贴底形态，与 Material 真实底栏一致（Material 不提供悬浮底栏，
                // 悬浮/模糊为 Miuix 独占）。三个图标按 BottomBarDestinationMaterial 的顺序各占一栏、
                // 水平均分并垂直居中；这里只画图标不画文字，避免小字号在缩略图里偏移错位。
                Surface(
                    color = colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .height(40.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BottomBarDestinationMaterial.entries.forEachIndexed { index, destination ->
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = null,
                                    tint = if (index == 0) {
                                        colorScheme.primary
                                    } else {
                                        colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                    },
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewBlock(
    color: Color,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color)
    )
}
@SuppressLint("NewApi")
@Composable
private fun ColorButtonMaterial(
    color: Color,
    isSelected: Boolean,
    isDark: Boolean,
    paletteStyle: PaletteStyle = PaletteStyle.TonalSpot,
    colorSpec: ColorSpec.SpecVersion = ColorSpec.SpecVersion.SPEC_2021,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // 色板格统一异步：平台取色（「跟随系统」档）与 material-kolor 推导都放到 Default 线程 + 进程级缓存，
    // 进入页面时主线程不再跑任何色板推导（与上游 ColorPalatteCard 的 ColorSwatchPreview 同一思路）。
    val isDynamicSwatch = color == Color.Unspecified
    val cacheKey = remember(color, paletteStyle, colorSpec, isDark) {
        "${color.toArgb()}_${paletteStyle.name}_${colorSpec.name}_$isDark"
    }
    val swatchScheme by produceState<ColorScheme?>(
        initialValue = colorSchemeCache[cacheKey],
        key1 = cacheKey,
    ) {
        val cached = colorSchemeCache[cacheKey]
        if (cached != null) {
            value = cached
        } else {
            val computed = withContext(Dispatchers.Default) {
                if (isDynamicSwatch) {
                    val base = if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                    dynamicColorScheme(
                        seedColor = Color.Unspecified,
                        isDark = isDark,
                        style = paletteStyle,
                        specVersion = colorSpec,
                        primary = base.primary,
                        secondary = base.secondary,
                        tertiary = base.tertiary,
                        neutral = base.surface,
                        neutralVariant = base.surfaceVariant,
                        error = base.error,
                    )
                } else {
                    dynamicColorScheme(
                        seedColor = color,
                        isDark = isDark,
                        style = paletteStyle,
                        specVersion = colorSpec,
                    )
                }
            }
            colorSchemeCache[cacheKey] = computed
            value = computed
        }
    }
    val colorScheme: ColorScheme? = swatchScheme

    // 色板还没算完时的兜底：「跟随系统」档不能用 Color.Unspecified 兜底（会画成透明），
    // 改用当前应用主题色；固定色档仍用种子色兜底，避免出现空白格。
    val containerColor = colorScheme?.surfaceContainer ?: MaterialTheme.colorScheme.surfaceContainer
    val arcPrimary = colorScheme?.primaryContainer
        ?: if (isDynamicSwatch) MaterialTheme.colorScheme.primaryContainer else color
    val arcTertiary = colorScheme?.tertiaryContainer
        ?: if (isDynamicSwatch) MaterialTheme.colorScheme.tertiaryContainer else color.copy(alpha = 0.6f)
    val accentColor = colorScheme?.primary
        ?: if (isDynamicSwatch) MaterialTheme.colorScheme.primary else color
    val onAccentColor = colorScheme?.onPrimary ?: MaterialTheme.colorScheme.onPrimary

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
            onClick()
        },
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        modifier = Modifier.size(72.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(48.dp)) {
                drawArc(
                    color = arcPrimary,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = true
                )
                drawArc(
                    color = arcTertiary,
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = true
                )
            }

            val scale by animateFloatAsState(targetValue = if (isSelected) 1.1f else 1.0f)
            Box(
                modifier = Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
                contentAlignment = Alignment.Center
            ) {
                AnimatedVisibility(
                    visible = isSelected,
                    enter = fadeIn() + scaleIn(initialScale = 0.8f),
                    exit = fadeOut() + scaleOut(targetScale = 0.8f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .border(2.dp, accentColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(accentColor, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = onAccentColor,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(16.dp)
                            )
                        }
                    }
                }
                AnimatedVisibility(
                    visible = !isSelected,
                    enter = fadeIn() + scaleIn(initialScale = 0.8f),
                    exit = fadeOut() + scaleOut(targetScale = 0.8f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(accentColor, CircleShape)
                    )
                }
            }
        }
    }
}
