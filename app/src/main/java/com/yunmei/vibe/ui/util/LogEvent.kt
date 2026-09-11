package com.yunmei.vibe.ui.util

import android.content.Context
import android.os.Build
import android.system.Os
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPOutputStream

/** 本工程规范英文名（日志文件名前缀的唯一来源，避免多处硬编码）。 */
const val APP_NAME_EN = "YunmeiVibe"

/**
 * 日志文件名：`YunmeiVibe_log_yyyyMMdd_HHmmss.txt.gz`
 * 内容为 gzip 压缩的纯文本报告，因此保留 `.txt.gz` 后缀（保存/分享 Intent 使用 application/gzip）。
 */
fun bugreportFileName(): String {
    val current = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
    return "${APP_NAME_EN}_log_${current}.txt.gz"
}

fun getBugreportFile(context: Context): File {
    val targetFile = File(context.cacheDir, bugreportFileName())

    val report = buildString {
        appendLine("App: ${getAppVersion(context)}")
        appendLine("BRAND: ${Build.BRAND}")
        appendLine("MODEL: ${Build.MODEL}")
        appendLine("PRODUCT: ${Build.PRODUCT}")
        appendLine("MANUFACTURER: ${Build.MANUFACTURER}")
        appendLine("SDK: ${Build.VERSION.SDK_INT}")
        appendLine("PREVIEW_SDK: ${Build.VERSION.PREVIEW_SDK_INT}")
        appendLine("FINGERPRINT: ${Build.FINGERPRINT}")
        appendLine("DEVICE: ${Build.DEVICE}")
        runCatching { Os.uname() }.onSuccess { uname ->
            appendLine("KernelRelease: ${uname.release}")
            appendLine("KernelVersion: ${uname.version}")
            appendLine("Machine: ${uname.machine}")
            appendLine("Nodename: ${uname.nodename}")
            appendLine("Sysname: ${uname.sysname}")
        }
        appendLine()
        appendLine("Logcat:")
        appendLine(readLogcat())
    }

    GZIPOutputStream(targetFile.outputStream()).use { output ->
        output.write(report.toByteArray())
    }

    return targetFile
}

private fun readLogcat(): String {
    return runCatching {
        ProcessBuilder("logcat", "-d", "-v", "time")
            .redirectErrorStream(true)
            .start()
            .inputStream
            .bufferedReader()
            .use { it.readText() }
            .ifBlank { "No logcat output." }
    }.getOrElse { "Unable to read logcat: ${it.message}" }
}
