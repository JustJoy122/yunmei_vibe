package com.yunmei.vibe.ui.screen.themesettings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.yunmei.vibe.data.model.Lock
import com.yunmei.vibe.ui.screen.home.HomeActions
import com.yunmei.vibe.ui.screen.home.HomeUiState

/**
 * 主题预览图的固定缩放比。
 *
 * 预览框 = 真机屏幕尺寸 × 该比例；虚拟屏 = 真机屏幕的 dp 尺寸（原始大小）。
 * 两者都是确定值，缩放比因此是常量，不依赖任何测量结果——此前用「测量宽度 / 假设的 360dp」
 * 反推缩放比，一旦测量值或推导高度异常就会只画出左上角一小块。
 */
internal const val THEME_PREVIEW_SCALE = 0.4f

/**
 * 预览图用的示例首页状态：字段填满并给出一个可用门锁，
 * 走「已有默认门锁」的首页分支（状态 Banner + 开门/打卡/取码卡片），与常见真机状态一致。
 */
@Composable
internal fun rememberPreviewHomeState(): HomeUiState = remember {
    val previewLock = Lock(
        label = "宿舍门锁",
        mac = "AA:BB:CC:DD:EE:FF",
        writeUuid = "0000fff1-0000-1000-8000-00805f9b34fb",
        serviceUuid = "0000fff0-0000-1000-8000-00805f9b34fb",
        secret = "PREVIEW",
        schoolNo = "001",
        lockNo = "01",
    )
    HomeUiState(
        lock = previewLock,
        defaultLock = previewLock,
        lockCount = 1,
        battery = 86,
        quickConnect = true,
        showSignButton = true,
        showCodeButton = true,
    )
}

/** 预览图里的操作全部为空实现：预览只展示外观，不触发任何真实行为。 */
@Composable
internal fun rememberPreviewHomeActions(): HomeActions = remember {
    HomeActions(
        onOpenDetail = {},
        onOpenLocks = {},
        onOpenDoor = {},
        onSetAutoConnect = {},
        onSetAutoExit = {},
        onSetAutoCode = {},
        onGetCode = {},
        onSign = {},
        onResolveSignAsk = {},
        onDismissSignAsk = {},
    )
}
