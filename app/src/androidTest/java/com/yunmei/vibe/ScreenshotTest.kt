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
 * 产物先落在 `getExternalFilesDir(null)/screenshots/`，结束时再以 shell 身份复制到
 * `/sdcard/Download/yunmei-screenshots/`：
 *
 * **为什么必须多这一步**：CI 走的是 `connectedDebugAndroidTest`，AGP / UTP 在测试
 * 跑完后会 **卸载 APK**，而 `/sdcard/Android/data/<pkg>/` 会随卸载一起被删掉，
 * 于是紧跟着的 `adb pull` 自然找不到目录（GitHub Actions 上就是这么挂的）。
 * `/sdcard/Download/` 属于公共目录，卸载 App 不会动它。
 *
 * ```
 * adb shell am instrument -w \
 *   -e class com.yunmei.vibe.ScreenshotTest \
 *   com.yunmei.vibe.test/androidx.test.runner.AndroidJUnitRunner
 * adb pull /sdcard/Android/data/com.yunmei.vibe/files/screenshots build/screenshots
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
                // 打卡状态要等一次网络往返才落到 UI 上；等到角标出现再拍，
                // 否则首页那张图会缺「已打卡 / 未打卡」。
                waitForSignBadge()
                Thread.sleep(SETTLE_MS)

                capture(state, PAGE_HOME)
                clickBottomTab(TAB_LOCKS)
                // 账号头像走 OkHttp 异步下载，1.2s 的常规等待经常拍成姓名首字。
                waitForAvatar()
                capture(state, PAGE_LOCKS)
                clickBottomTab(TAB_SETTINGS)
                capture(state, PAGE_SETTINGS)
            }
        }

        publishToPublicDir()
    }

    /**
     * 把私有目录里的 PNG 复制到 `/sdcard/Download/yunmei-screenshots/`。
     *
     * 用 [android.app.UiAutomation.executeShellCommand] 以 shell 身份执行，
     * 既不需要 `WRITE_EXTERNAL_STORAGE` / MANAGE_EXTERNAL_STORAGE 权限，
     * 也能保证「App 被卸载后截图还在」。
     */
    private fun publishToPublicDir() {
        val files = outputDir.listFiles { file -> file.isFile && file.name.endsWith(".png") }
            .orEmpty()
            .sortedBy { it.name }
        if (files.isEmpty()) {
            Log.w(TAG, "没有可导出的截图：${outputDir.absolutePath}")
            return
        }
        shell("mkdir -p $PUBLIC_DIR")
        files.forEach { file ->
            shell("cp -f \"${file.absolutePath}\" \"$PUBLIC_DIR/${file.name}\"")
        }
        Log.i(TAG, "已导出 ${files.size} 张到 $PUBLIC_DIR\n" + shell("ls -l $PUBLIC_DIR"))

        // 目录能被 shell 读到，但 adb pull 需要的是真实路径；顺带回读一次确认非空。
        val count = shell("ls $PUBLIC_DIR | wc -l").trim()
        Log.i(TAG, "$PUBLIC_DIR 内文件数 = $count")
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

    /** 以 shell 身份执行命令并取回输出（instrumentation 的 uiAutomation 天然具备 shell 权限）。 */
    private fun shell(command: String): String {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            .use { it.readBytes().toString(Charsets.UTF_8) }
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
     * 点底栏。同一屏可能有两个同名文本（页面标题 + 底栏标签），
     * 底栏在最下方，所以取 `visibleBounds.top` 最大的那个。
     */
    private fun clickBottomTab(label: String) {
        val target = device.findObjects(By.text(label))
            .maxByOrNull { it.visibleBounds.top }
            ?: run {
                // 找不到就先把当前界面结构 dump 下来，便于定位是导航错了还是控件没渲染。
                runCatching {
                    device.dumpWindowHierarchy(File(outputDir, "fail_$label.xml"))
                }
                error("底栏标签不存在: $label")
            }
        target.click()
        device.waitForIdle()
        Thread.sleep(SETTLE_MS)
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

        const val TAB_HOME = "首页"
        const val TAB_LOCKS = "门锁"
        const val TAB_SETTINGS = "设置"

        const val TAB_SIGNED = "已打卡"
        const val TAB_UNSIGNED = "未打卡"

        /** 等一次重组 + 淡入动画落定；截图对时序很敏感。 */
        const val SETTLE_MS = 1_200L

        /** 等打卡角标出现的最长时间：冷启时门锁/打卡都是异步拉的。 */
        const val SIGN_BADGE_TIMEOUT_MS = 8_000L

        /** 头像下载超时：GitHub 走公网，慢一点也给它 8 秒。 */
        const val AVATAR_TIMEOUT_MS = 8_000L

        /** 与 [com.yunmei.vibe.ui.component.profile.AVATAR_LOADED_DESCRIPTION] 保持一致。 */
        const val AVATAR_LOADED = "用户头像"

        const val TAG = "ScreenshotTest"

        /** 公共导出目录：属于 /sdcard/Download，卸载 App 不会被清掉，CI 从这里 pull。 */
        const val PUBLIC_DIR = "/sdcard/Download/yunmei-screenshots"
    }
}
