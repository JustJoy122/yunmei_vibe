package com.yunmei.vibe

import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import com.yunmei.vibe.core.di.AppContainer
import com.yunmei.vibe.data.preferences.SettingsPrefs
import com.yunmei.vibe.ui.unlock.UnlockNotifications
import com.yunmei.vibe.ui.unlock.UnlockShortcut
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class YunMeiApp : Application() {

    lateinit var container: AppContainer
        private set

    /** 应用级协程作用域：订阅门锁数据变化，同步「开门」快捷方式的可用性。 */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 上次同步快捷方式时的系统深色模式，避免配置变化时做无谓的重复推送。 */
    private var lastNightMode: Boolean? = null

    override fun onCreate() {
        super.onCreate()
        installCrashHandler()
        app = this
        // FastBle 初始化已下沉到 UnlockManager（首次真正用到蓝牙时才 init），避免拖慢冷启动。
        container = AppContainer(this)

        // 「开门」快捷方式：通知渠道只建一次；可用性随默认门锁变化。
        // LockStore.revision 是 StateFlow，订阅时会先收到当前值，因此启动即完成一次同步。
        UnlockNotifications.ensureChannel(this)
        lastNightMode = UnlockShortcut.isNightMode(this)
        appScope.launch {
            container.lockStore.revision.collect {
                refreshUnlockShortcut()
            }
        }
        // 预测性返回手势：按保存的偏好初始化（与模板 TemplateApplication 一致）。
        // 键名统一走 SettingsPrefs，避免此处与设置页各自手写字符串。
        if (SettingsPrefs.of(this).getBoolean(SettingsPrefs.ENABLE_PREDICTIVE_BACK, false)) {
            enableOnBackInvokedCallback(true)
        }
    }

    /**
     * 系统深浅色切换时刷新快捷方式图标。
     *
     * 快捷方式图标是静态资源（由启动器绘制），不会随主题自动反色，因此需要在 uiMode 变化后
     * 重新推送一次快捷方式；只有深色模式确实改变时才推送，避免无意义的重复调用。
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!::container.isInitialized) return
        val night = UnlockShortcut.isNightMode(this)
        if (night != lastNightMode) {
            lastNightMode = night
            appScope.launch { refreshUnlockShortcut() }
        }
    }

    /** 读取默认门锁状态并同步「开门」快捷方式（图标按当前深色模式选择）。 */
    private suspend fun refreshUnlockShortcut() {
        val hasDefault = withContext(Dispatchers.IO) {
            container.lockStore.getDefault() != null
        }
        withContext(Dispatchers.Main) {
            UnlockShortcut.sync(this@YunMeiApp, hasDefault)
        }
    }

    /** 切换系统「预测性返回手势」（隐藏 API，与模板 TemplateApplication 相同的反射方案）。 */
    @SuppressLint("NewApi")
    fun enableOnBackInvokedCallback(enable: Boolean) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                HiddenApiBypass.addHiddenApiExemptions(
                    "Landroid/content/pm/ApplicationInfo;->setEnableOnBackInvokedCallback"
                )
            }
            val method = ApplicationInfo::class.java.getDeclaredMethod(
                "setEnableOnBackInvokedCallback",
                Boolean::class.javaPrimitiveType
            )
            method.isAccessible = true
            method.invoke(applicationInfo, enable)
        }.onFailure { error ->
            Log.w("YunMei", "enableOnBackInvokedCallback failed", error)
        }
    }

    /**
     * 崩溃兜底：把堆栈写入 filesDir/crash-latest.txt（覆盖式，方便反馈），
     * 同时保留带时间戳的完整记录，然后交给系统默认处理。
     */
    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val content = buildString {
                    appendLine("time=$time")
                    appendLine("thread=$thread")
                    appendLine(Log.getStackTraceString(throwable))
                }
                File(filesDir, "crash-latest.txt").writeText(content)
                File(filesDir, "crash-$time.txt".replace(":", "-").replace(" ", "_")).writeText(content)
                Log.e("YunMei", "FATAL crash captured: $content")
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        /** 供模板组件（SettingsRepository 等）引用的全局实例。 */
        lateinit var app: YunMeiApp
            private set
    }
}
