package com.yunmei.vibe

import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log
import com.clj.fastble.BleManager
import com.yunmei.vibe.core.di.AppContainer
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
        BleManager.getInstance().init(this)
        container = AppContainer(this)

        // 预测性返回手势：按保存的偏好初始化（与模板 TemplateApplication 一致）。
        if (getSharedPreferences("settings", MODE_PRIVATE).getBoolean("enable_predictive_back", false)) {
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
