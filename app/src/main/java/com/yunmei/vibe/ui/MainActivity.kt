package com.yunmei.vibe.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.yunmei.vibe.R
import com.yunmei.vibe.ui.component.bottombar.BottomBar
import com.yunmei.vibe.ui.component.bottombar.MainPagerState
import com.yunmei.vibe.ui.component.bottombar.SideRail
import com.yunmei.vibe.ui.component.bottombar.rememberMainPagerState
import com.yunmei.vibe.ui.component.dialog.rememberConfirmDialog
import com.yunmei.vibe.ui.navigation3.LocalNavigator
import com.yunmei.vibe.ui.navigation3.Navigator
import com.yunmei.vibe.ui.navigation3.Route
import com.yunmei.vibe.ui.navigation3.rememberNavigator
import com.yunmei.vibe.ui.screen.about.AboutScreen
import com.yunmei.vibe.ui.screen.home.HomePager
import com.yunmei.vibe.ui.screen.license.LicenseScreen
import com.yunmei.vibe.ui.screen.locks.LocksPager
import com.yunmei.vibe.ui.screen.login.LoginScreen
import com.yunmei.vibe.ui.screen.lockdetail.LockDetailScreen
import com.yunmei.vibe.ui.screen.settings.SettingPager
import com.yunmei.vibe.ui.screen.themesettings.ThemeSettingsScreen
import com.yunmei.vibe.ui.theme.LocalColorMode
import com.yunmei.vibe.ui.theme.LocalEnableBlur
import com.yunmei.vibe.ui.theme.LocalEnableFloatingBottomBar
import com.yunmei.vibe.ui.theme.LocalEnableFloatingBottomBarBlur
import com.yunmei.vibe.ui.theme.TemplateTheme
import com.yunmei.vibe.ui.theme.ThemeController
import com.yunmei.vibe.ui.util.rememberBlurBackdrop
import com.yunmei.vibe.ui.util.rememberContentReady
import com.yunmei.vibe.ui.viewmodel.MainActivityViewModel
import com.yunmei.vibe.ui.viewmodel.MainPagerConfig
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

class MainActivity : ComponentActivity() {

    /**
     * 应用「开屏（窗口）底色」主题，必须在 `super.onCreate()`（进而 `setContentView`）之前调用。
     *
     * 系统在进程启动前就已按清单主题绘制开屏窗口 / API 31+ 的 SplashScreen，那一帧只能跟随
     * **系统**深浅色（见 `res/values-night/themes.xml`）；这里再按**应用内**主题设置把应用窗口
     * 底色对齐到最终配色，保证系统开屏 → Compose 首帧之间无闪白、无跳色：
     *
     * - 应用内深色 + AMOLED 开关 → 纯黑（`Theme.YunmeiVibe.Amoled`）
     * - 应用内深色（未开 AMOLED）→ 深色 surface（`Theme.YunmeiVibe.Dark`）
     * - 应用内浅色 → 保持清单主题不变（浅色）
     *
     * `color_mode` / `amoled` 复用 [ThemeController.getAppSettings] 读取，与应用内配色逻辑同源。
     */
    private fun applyStartupTheme() {
        val appSettings = ThemeController.getAppSettings(this)
        val colorMode = appSettings.colorMode
        val systemNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val darkTheme = colorMode.isDark || (colorMode.isSystem && systemNight)
        if (!darkTheme) return
        // `DARK_AMOLED` 为历史遗留的合并模式，与独立的 amoled 开关等价处理。
        val amoled = appSettings.amoled || colorMode.isAmoled
        setTheme(if (amoled) R.style.Theme_YunmeiVibe_Amoled else R.style.Theme_YunmeiVibe_Dark)
    }

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        applyStartupTheme()
        super.onCreate(savedInstanceState)

        setContent {
            val viewModel = viewModel<MainActivityViewModel>()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val selectedMainPage by viewModel.selectedMainPage.collectAsStateWithLifecycle()
            val appSettings = uiState.appSettings
            val uiMode = uiState.uiMode
            val darkMode = appSettings.colorMode.isDark || (appSettings.colorMode.isSystem && isSystemInDarkTheme())

            DisposableEffect(darkMode) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    ) { darkMode },
                    navigationBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    ) { darkMode },
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
                onDispose { }
            }

            // 游客模式：启动一律直接进入主界面，登录页仅作为二级页面按需进入，
            // 不再强制"必须先登录且必须有门锁"。
            val navigator = rememberNavigator(Route.Main)
            val systemDensity = LocalDensity.current
            val density = remember(systemDensity, uiState.pageScale) {
                Density(systemDensity.density * uiState.pageScale, systemDensity.fontScale)
            }

            CompositionLocalProvider(
                LocalNavigator provides navigator,
                LocalDensity provides density,
                LocalColorMode provides appSettings.colorMode.value,
                LocalEnableBlur provides uiState.enableBlur,
                LocalEnableFloatingBottomBar provides uiState.enableFloatingBottomBar,
                LocalEnableFloatingBottomBarBlur provides uiState.enableFloatingBottomBarBlur,
                LocalUiMode provides uiMode,
            ) {
                val currentSelectedPage by rememberUpdatedState(selectedMainPage)
                val mainScreenEntry = @Composable {
                    MainScreen(
                        initialPage = currentSelectedPage,
                        onPageChanged = viewModel::setSelectedMainPage,
                        onAutoOpenRequested = viewModel::requestAutoOpen,
                        onUpdateCheckRequested = viewModel::checkUpdateOnStartup,
                        autoOpenTrigger = viewModel.autoOpenTrigger,
                    )
                }
                TemplateTheme(appSettings = appSettings, uiMode = uiMode) {
                    // 应用启动自检更新：check_update 开启且存在更高的正式 Release 时弹窗提示。
                    // Debug 包不会产生结果（见 ui/util/UpdateChecker.kt 的 BuildConfig.DEBUG 判断）。
                    val latestVersion by viewModel.latestVersion.collectAsStateWithLifecycle()
                    val uriHandler = LocalUriHandler.current
                    val updateDialog = rememberConfirmDialog(
                        onConfirm = {
                            latestVersion?.downloadUrl
                                ?.takeIf { it.isNotBlank() }
                                ?.let(uriHandler::openUri)
                            viewModel.dismissUpdate()
                        },
                        onDismiss = { viewModel.dismissUpdate() },
                    )
                    val updateTitle = stringResource(R.string.update_available_title)
                    val updateMessageFormat = stringResource(R.string.update_available_message)
                    val updateConfirmLabel = stringResource(R.string.update_confirm)
                    val updateCancelLabel = stringResource(R.string.cancel)
                    LaunchedEffect(latestVersion) {
                        latestVersion?.let { info ->
                            updateDialog.showConfirm(
                                title = updateTitle,
                                content = updateMessageFormat.format(info.versionName),
                                confirm = updateConfirmLabel,
                                dismiss = updateCancelLabel,
                            )
                        }
                    }
                    // 稳定化 NavDisplay 参数：主题热切换（莫奈开关等）时参数引用保持不变，
                    // NavDisplay 被 strong skipping 跳过，避免导航场景/手势状态 churn
                    // 导致的 stale 手势误派发（navigationevent b/375343407）。
                    val navOnBack = remember(navigator) {
                        {
                            // 返回键兜底：
                            // - 多级栈 → 正常出栈；
                            // - 只剩登录页（旧版本状态恢复等异常情况）→ 强清导航栈回到主界面
                            //   （等价于 FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK 跳转）；
                            // - 只剩主界面 → 交给系统默认行为（退出应用）。
                            when {
                                navigator.backStackSize() > 1 -> navigator.pop()
                                navigator.current() is Route.Login -> navigator.replaceAll(listOf(Route.Main))
                                else -> finish()
                            }
                        }
                    }
                    val freshDecorators: List<androidx.navigation3.runtime.NavEntryDecorator<androidx.navigation3.runtime.NavKey>> =
                        listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator()
                        )
                    val navEntryDecorators: List<androidx.navigation3.runtime.NavEntryDecorator<androidx.navigation3.runtime.NavKey>> =
                        remember { freshDecorators }
                    val navSceneStrategies = remember {
                        listOf<androidx.navigation3.scene.SceneStrategy<androidx.navigation3.runtime.NavKey>>(
                            SinglePaneSceneStrategy()
                        )
                    }
                    // entryProvider：组合上下文构建（inline 需要），remember 缓存首次实例保证跨重组引用稳定。
                    val freshEntryProvider: (androidx.navigation3.runtime.NavKey) -> androidx.navigation3.runtime.NavEntry<androidx.navigation3.runtime.NavKey> =
                        entryProvider {
                            entry<Route.Main> { mainScreenEntry() }
                            entry<Route.Login> { LoginScreen() }
                            entry<Route.ThemeSettings> { ThemeSettingsScreen() }
                            entry<Route.About> { AboutScreen() }
                            entry<Route.OpenSourceLicense> { LicenseScreen() }
                            entry<Route.LockDetail> { route -> LockDetailScreen(route.label) }
                        }
                    val navEntryProvider = remember { freshEntryProvider }

                    val navDisplay = @Composable {
                        NavDisplay(
                            backStack = navigator.backStack,
                            entryDecorators = navEntryDecorators,
                            onBack = navOnBack,
                            sceneStrategies = navSceneStrategies,
                            entryProvider = navEntryProvider,
                        )
                    }

                    when (uiMode) {
                        UiMode.Material -> androidx.compose.material3.Scaffold { navDisplay() }
                        UiMode.Miuix -> Scaffold { navDisplay() }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

val LocalMainPagerState = staticCompositionLocalOf<MainPagerState> { error("LocalMainPagerState not provided") }

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun MainScreen(
    initialPage: Int = 0,
    onPageChanged: (Int) -> Unit = {},
    onAutoOpenRequested: () -> Unit = {},
    onUpdateCheckRequested: () -> Unit = {},
    autoOpenTrigger: kotlinx.coroutines.flow.StateFlow<Long> = kotlinx.coroutines.flow.MutableStateFlow(0L),
) {
    val navController = LocalNavigator.current
    val enableBlur = LocalEnableBlur.current
    val enableFloatingBottomBar = LocalEnableFloatingBottomBar.current
    val enableFloatingBottomBarBlur = LocalEnableFloatingBottomBarBlur.current
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { MainPagerConfig.PAGE_COUNT })
    val mainPagerState = rememberMainPagerState(pagerState)
    var userScrollEnabled by remember { mutableStateOf(true) }
    val uiMode = LocalUiMode.current
    val surfaceColor = when (uiMode) {
        UiMode.Material -> MaterialTheme.colorScheme.surface // Blur is not used in Material, this is just a placeholder
        UiMode.Miuix -> MiuixTheme.colorScheme.surface
    }
    val blurBackdrop = rememberBlurBackdrop(enableBlur)

    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }

    val settledPage = mainPagerState.pagerState.settledPage
    LaunchedEffect(settledPage) {
        onPageChanged(settledPage)
    }

    val currentPage = mainPagerState.pagerState.currentPage
    LaunchedEffect(currentPage) {
        mainPagerState.syncPage()
    }

    // 自动开门：每次进入主界面检查一次，同一进程内只触发一次（由 MainActivityViewModel 消费标记保证）。
    // 启动自检更新：同一进程内也只触发一次，受设置页「检查更新」开关控制。
    LaunchedEffect(Unit) {
        onAutoOpenRequested()
        onUpdateCheckRequested()
    }

    // 自动开门触发后，把 Pager 切回「首页」，由 HomePager 消费触发值执行开门。
    val autoTrigger by autoOpenTrigger.collectAsStateWithLifecycle()
    LaunchedEffect(autoTrigger) {
        if (autoTrigger > 0 && mainPagerState.selectedPage != MainPagerConfig.PAGE_HOME) {
            mainPagerState.animateToPage(MainPagerConfig.PAGE_HOME)
        }
    }

    MainScreenBackHandler(mainPagerState, navController)

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val useNavigationRail = isLandscape && !(uiMode == UiMode.Miuix && enableFloatingBottomBar)

    CompositionLocalProvider(
        LocalMainPagerState provides mainPagerState
    ) {
        val contentReady = rememberContentReady()
        val pagerContent = @Composable { bottomInnerPadding: Dp ->
            Box(modifier = if (blurBackdrop != null) Modifier.layerBackdrop(blurBackdrop) else Modifier) {
                HorizontalPager(
                    modifier = Modifier
                        .then(if (enableFloatingBottomBar && enableFloatingBottomBarBlur) Modifier.layerBackdrop(backdrop) else Modifier),
                    state = mainPagerState.pagerState,
                    beyondViewportPageCount = if (contentReady) 1 else 0,
                    userScrollEnabled = userScrollEnabled,
                ) { page ->
                    val isCurrentPage = page == settledPage
                    when (page) {
                        MainPagerConfig.PAGE_HOME -> if (isCurrentPage || contentReady) HomePager(navController, bottomInnerPadding, isCurrentPage, autoOpenTrigger)
                        MainPagerConfig.PAGE_LOCKS -> if (isCurrentPage || contentReady) LocksPager(navController, bottomInnerPadding, isCurrentPage)
                        MainPagerConfig.PAGE_SETTINGS -> if (isCurrentPage || contentReady) SettingPager(navController, bottomInnerPadding)
                    }
                }
            }
        }

        if (useNavigationRail) {
            val startInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
                .only(WindowInsetsSides.Start)
            val navBarBottomPadding = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

            when (uiMode) {
                UiMode.Material -> androidx.compose.material3.Scaffold {
                    Row {
                        SideRail(
                            blurBackdrop = blurBackdrop,
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .consumeWindowInsets(startInsets)
                        ) {
                            pagerContent(navBarBottomPadding)
                        }
                    }
                }

                UiMode.Miuix -> Scaffold { _ ->
                    Row {
                        SideRail(
                            blurBackdrop = blurBackdrop,
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .consumeWindowInsets(startInsets)
                        ) {
                            pagerContent(navBarBottomPadding)
                        }
                    }
                }
            }
        } else {
            val bottomBar = @Composable {
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BottomBar(
                        blurBackdrop = blurBackdrop,
                        backdrop = backdrop,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }

            when (uiMode) {
                UiMode.Material -> androidx.compose.material3.Scaffold(bottomBar = bottomBar) { innerPadding ->
                    pagerContent(innerPadding.calculateBottomPadding())
                }

                UiMode.Miuix -> Scaffold(bottomBar = bottomBar) { innerPadding ->
                    pagerContent(innerPadding.calculateBottomPadding())
                }
            }
        }
    }
}

@Composable
private fun MainScreenBackHandler(
    mainState: MainPagerState,
    navController: Navigator,
) {
    val isPagerBackHandlerEnabled by remember {
        derivedStateOf {
            navController.current() is Route.Main && navController.backStackSize() == 1 && mainState.selectedPage != 0
        }
    }

    val navEventState = rememberNavigationEventState(NavigationEventInfo.None)

    NavigationBackHandler(
        state = navEventState,
        isBackEnabled = isPagerBackHandlerEnabled,
        onBackCompleted = {
            mainState.animateToPage(0)
        }
    )
}
