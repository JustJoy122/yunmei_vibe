package com.yunmei.vibe

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.yunmei.vibe.data.preferences.SettingsPrefs
import com.yunmei.vibe.ui.MainActivity
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * README 截图流水线的本地版。
 *
 * **为什么不用 Compose 测试规则**：`createAndroidComposeRule` 的同步依赖 Espresso，
 * 而 Espresso 在部分定制 ROM（本机 ColorOS / API 37）初始化时会调
 * `InputManager.getInstance()`——这方法已被移除，直接抛 `NoSuchMethodException`。
 * 所以这里只用 UiAutomator 点按 + `UiDevice.takeScreenshot`，不碰 Espresso。
 *
 * 流程：写偏好 → 冷启 `MainActivity` → 依次点底栏三页 → 每页截一张（裁掉状态栏），
 * 产物落在 `getExternalFilesDir(null)/screenshots/`，由 CI 用 `adb pull` 直接取走。
 *
 * **为什么不导出到 `/sdcard/Download/`**：CI 不走 UTP（`connectedDebugAndroidTest` 跑完会卸载
 * APK，连带删掉 `/sdcard/Android/data/<pkg>/`），而是自己 `adb install` + `am instrument`，
 * 测试结束后 App 仍然装着，私有目录里的文件可以直接 pull。往 `/sdcard/Download/` 再 `cp`
 * 一份既需要 shell 权限、在分区上又常常失败（`/sdcard/Android/data` 与 `/sdcard/Download`
 * 属于不同挂载点），纯属多余，故已移除。
 *
 * 失败诊断：任何一步找不到控件都会 dump 当前窗口结构（`fail_<label>.xml`）与整屏截图
 * （`fail_<label>.png`），两者都在同一私有目录里，CI 失败时可以直接 pull 出来看。
 *
 * ```
 * adb shell am instrument -w \
 *   -e class com.yunmei.vibe.ScreenshotTest \
 *   com.yunmei.vibe.test/androidx.test.runner.AndroidJUnitRunner
 * adb pull /sdcard/Android/data/com.yunmei.vibe/files/screenshots/. build/screenshots/
 * ```
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device: UiDevice by lazy { UiDevice.getInstance(instrumentation) }

    /** 应用私有外部目录：测试期间写这里，卸载后会被系统清掉。 */
    private val outputDir: File by lazy {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        File(base, "screenshots").apply {
            mkdirs()
            Log.i(TAG, "私有输出目录: ${absolutePath}（存在=${exists()}）")
        }
    }

    @Test
    fun captureAllStates() {
        wakeAndUnlock()

        val states = listOf(
            ShotState("miuix_light", uiMode = "miuix", colorMode = 1),
            ShotState("material_light", uiMode = "material", colorMode = 4),
            ShotState("miuix_dark", uiMode = "miuix", colorMode = 2),
            ShotState("material_dark", uiMode = "material", colorMode = 5),
        )

        states.forEach { state ->
            applyPrefs(state.uiMode, state.colorMode)
            // 主题在 setContent 时一次性解析，必须换一个 Activity 实例才会生效。
            ActivityScenario.launch(MainActivity::class.java).use {
                device.waitForIdle()
                // 首次启动可能弹通知权限 / 崩溃上报之类的系统对话框，挡在底栏前面。
                dismissBlockingDialogs()
                // 打卡状态要等一次网络往返才落到 UI 上；等到角标出现再拍，
                // 否则首页那张图会缺「已打卡 / 未打卡」。
                waitForSignBadge()
                Thread.sleep(SETTLE_MS)

                capture(state, PAGE_HOME)
                clickBottomTab(tag = TAG_LOCKS, label = TAB_LOCKS)
                // 账号头像走 OkHttp 异步下载，1.2s 的常规等待经常拍成姓名首字。
                waitForAvatar()
                capture(state, PAGE_LOCKS)
                clickBottomTab(tag = TAG_SETTINGS, label = TAB_SETTINGS)
                capture(state, PAGE_SETTINGS)
            }
        }

        Log.i(TAG, "截图已写入 ${outputDir.absolutePath}，共 "
            + outputDir.listFiles { file -> file.isFile && file.name.endsWith(".png") }.orEmpty().size + " 张")
    }

    /**
     * 唤醒并解锁设备。
     *
     * 模拟器冷启后可能停在锁屏（`mDreamingLockscreen=true`），此时 UiAutomator 看不到
     * 任何应用控件，直接报「底栏标签不存在」。`wm dismiss-keyguard` 在无锁屏密码的
     * 模拟器上等价于上滑解锁；有密码的真机上它会失败，失败也不影响后续步骤。
     */
    private fun wakeAndUnlock() {
        runCatching { shell("input keyevent 224") }
        Thread.sleep(1_000L)
        runCatching { shell("wm dismiss-keyguard") }
        Thread.sleep(1_500L)
        Log.i(TAG, "唤醒后前台包名: " + runCatching { device.currentPackageName }.getOrNull())
    }

    /**
     * 关掉可能挡住界面的系统弹窗（权限申请、「已停止运行」等）。
     *
     * 只点与权限/确认有关的少数几个文案，且每一个都用 [runCatching] 包住：
     * 找不到就跳过，点错了也不会让整个截图任务失败。
     */
    private fun dismissBlockingDialogs() {
        val labels = listOf("允许", "始终允许", "仅在使用中允许", "允许所有", "确定", "知道了", "关闭应用")
        labels.forEach { label ->
            runCatching {
                val candidates = device.findObjects(By.text(label))
                if (candidates.isNotEmpty()) {
                    // 取最靠下的那个：系统权限对话框的按钮通常位于屏幕下半部。
                    val target = candidates.maxByOrNull { it.visibleBounds.top }
                    Log.i(TAG, "点击疑似系统弹窗按钮: $label @ ${target?.visibleBounds}")
                    target?.click()
                    device.waitForIdle()
                    Thread.sleep(600L)
                }
            }.onFailure { Log.w(TAG, "处理系统弹窗失败（$label）: ${it.message}") }
        }
    }

    /**
     * 等打卡角标（「已打卡」或「未打卡」）出现。
     *
     * 两者都没等到也不报错——学校没开打卡时整行本来就不显示，
     * 这种情况截图仍然有效，只是少一行状态。
     */
    private fun waitForSignBadge() {
        val signed = device.wait(Until.hasObject(By.text(TAB_SIGNED)), SIGN_BADGE_TIMEOUT_MS)
        if (!signed) device.wait(Until.hasObject(By.text(TAB_UNSIGNED)), SETTLE_MS * 2)
    }

    /**
     * 等门锁页的账号头像下载并渲染完成。
     *
     * 头像组件在下载成功后会带上 `contentDescription = 用户头像`，
     * 未完成时是 `用户头像加载中`（姓名首字）。等不到也不算失败——
     * 离线或 GitHub 抽风时截出来的首字兜底同样可接受，所以只等不报错。
     */
    private fun waitForAvatar() {
        device.wait(Until.hasObject(By.desc(AVATAR_LOADED)), AVATAR_TIMEOUT_MS)
        Thread.sleep(SETTLE_MS)
    }

    private fun applyPrefs(uiMode: String, colorMode: Int) {
        SettingsPrefs.of(context).edit()
            // 启动自动检查更新会弹「发现新版本」对话框，把底栏整个盖住，截图必须关掉。
            .putBoolean(SettingsPrefs.CHECK_UPDATE, false)
            .putString(SettingsPrefs.UI_MODE, uiMode)
            .putInt(SettingsPrefs.COLOR_MODE, colorMode)
            .putBoolean(SettingsPrefs.MIUIX_MONET, false)
            // 截图关 AMOLED：纯黑底会把卡片层次吃掉。
            .putBoolean(SettingsPrefs.AMOLED, false)
            .apply()
    }

    private fun capture(state: ShotState, page: String) {
        device.waitForIdle()
        val target = File(outputDir, "${state.prefix}_$page.png")
        Log.i(TAG, "Saved to: ${target.absolutePath}")
        device.takeScreenshot(target)
        // 状态栏里有时间/电量/运营商，README 里不需要这些噪声。
        cropStatusBar(target)
        BitmapFactory.decodeFile(target.absolutePath)?.let { shot ->
            Log.i(TAG, "尺寸: ${shot.width}x${shot.height} (${target.name})")
            shot.recycle()
        }
        Thread.sleep(SETTLE_MS / 2)
    }

    private fun cropStatusBar(file: File) {
        val full = BitmapFactory.decodeFile(file.absolutePath) ?: return
        try {
            val top = statusBarHeight()
            if (top <= 0 || top >= full.height) return
            val cropped = Bitmap.createBitmap(full, 0, top, full.width, full.height - top)
            FileOutputStream(file).use { out ->
                cropped.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            if (cropped !== full) cropped.recycle()
        } finally {
            full.recycle()
        }
    }

    private fun statusBarHeight(): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else 0
    }

    /**
     * 点底栏。
     *
     * 三级兜底，越靠前越稳定：
     * 1. `By.res(tag)`——底栏节点设置了 `testTagsAsResourceId`，tag 会以 resource-id 暴露；
     * 2. `By.desc(label)`——底栏图标自带 `contentDescription`（与文案相同）；
     * 3. `By.text(label)`——最后才退回文本，同一屏可能有页面标题与底栏标签两个同名节点，
     *    取 `visibleBounds.top` 最大的那个（底栏在最下方）。
     *
     * 每级都先等 [TAB_TIMEOUT_MS]；全都拿不到才判失败，并在失败时 dump XML + 截图。
     */
    private fun clickBottomTab(tag: String, label: String) {
        val target = waitForTab(tag = tag, label = label) ?: run {
            dumpFailureArtifacts("tab_$tag")
            // 顺带把前台包名打出来：多半是被别的界面（锁屏 / 崩溃对话框 / 桌面）占住了。
            Log.e(TAG, "当前前台包名: " + runCatching { device.currentPackageName }.getOrNull())
            error("底栏标签不存在: $label（tag=$tag）")
        }
        Log.i(TAG, "点击底栏 $label: bounds=${target.visibleBounds}")
        target.click()
        device.waitForIdle()
        Thread.sleep(SETTLE_MS)
    }

    private fun waitForTab(tag: String, label: String): UiObject2? {
        if (device.wait(Until.hasObject(By.res(tag)), TAB_TIMEOUT_MS)) {
            device.findObject(By.res(tag))?.let { return it }
        }
        if (device.wait(Until.hasObject(By.desc(label)), TAB_TIMEOUT_MS)) {
            device.findObject(By.desc(label))?.let { return it }
        }
        device.wait(Until.hasObject(By.text(label)), TAB_TIMEOUT_MS)
        return device.findObjects(By.text(label)).maxByOrNull { it.visibleBounds.top }
    }

    /**
     * 失败现场取证：窗口结构 + 整屏截图。
     *
     * 只 dump XML 的话，看不出是被锁屏、权限对话框还是崩溃对话框挡住；补一张 PNG
     * 就能一眼判断，CI 失败时直接从 artifact 里拉下来看。
     */
    private fun dumpFailureArtifacts(prefix: String) {
        runCatching {
            val xml = File(outputDir, "fail_$prefix.xml")
            device.dumpWindowHierarchy(xml)
            Log.w(TAG, "已 dump 窗口结构: ${xml.absolutePath}（${xml.length()} B）")
        }.onFailure { Log.w(TAG, "dump 窗口结构失败: ${it.message}") }
        runCatching {
            val png = File(outputDir, "fail_$prefix.png")
            device.takeScreenshot(png)
            Log.w(TAG, "已保存失败截图: ${png.absolutePath}（${png.length()} B）")
        }.onFailure { Log.w(TAG, "失败截图保存失败: ${it.message}") }
    }

    /** 以 shell 身份执行命令并取回输出（UiAutomation 天然具备 shell 权限）。 */
    private fun shell(command: String): String {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            .use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private data class ShotState(
        val prefix: String,
        val uiMode: String,
        val colorMode: Int,
    )

    private companion object {
        const val PAGE_HOME = "home"
        const val PAGE_LOCKS = "locks"
        const val PAGE_SETTINGS = "settings"

        const val TAB_LOCKS = "门锁"
        const val TAB_SETTINGS = "设置"

        /** 与 `BottomBarDestination*.testTag` 保持一致。 */
        const val TAG_LOCKS = "tab_locks"
        const val TAG_SETTINGS = "tab_settings"

        const val TAB_SIGNED = "已打卡"
        const val TAB_UNSIGNED = "未打卡"

        /** 等一次重组 + 淡入动画落定；截图对时序很敏感。 */
        const val SETTLE_MS = 1_200L

        /** 单个定位方式（res / desc / text）的最长等待。 */
        const val TAB_TIMEOUT_MS = 5_000L

        /** 等打卡角标出现的最长时间：冷启时门锁/打卡都是异步拉的。 */
        const val SIGN_BADGE_TIMEOUT_MS = 8_000L

        /** 头像下载超时：GitHub 走公网，慢一点也给它 8 秒。 */
        const val AVATAR_TIMEOUT_MS = 8_000L

        /** 与 [com.yunmei.vibe.ui.component.profile.AVATAR_LOADED_DESCRIPTION] 保持一致。 */
        const val AVATAR_LOADED = "用户头像"

        const val TAG = "ScreenshotTest"
    }
}
