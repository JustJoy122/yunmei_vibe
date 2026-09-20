package com.yunmei.vibe

import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log
import com.yunmei.vibe.core.di.AppContainer
import com.yunmei.vibe.data.preferences.SettingsPrefs
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class YunMeiApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        installCrashHandler()
        app = this
        // FastBle 初始化已下沉到 UnlockManager（首次真正用到蓝牙时才 init），避免拖慢冷启动。
        container = AppContainer(this)

        // 预测性返回手势：按保存的偏好初始化（与模板 TemplateApplication 一致）。
        // 键名统一走 SettingsPrefs，避免此处与设置页各自手写字符串。
        if (SettingsPrefs.of(this).getBoolean(SettingsPrefs.ENABLE_PREDICTIVE_BACK, false)) {
            enableOnBackInvokedCallback(true)
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
