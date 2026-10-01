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
 * 需要脱敏的关键词（不区分大小写）。命中任意一个的行，其"消息体"会被替换为 ***。
 * 说明：这里只做保守过滤，宁可多脱敏也不放行；关键词由本次安全审计确定。
 */
private val SENSITIVE_KEYWORDS = listOf(
    "token=", "token:", "userid=", "user_id=", "lockpwd", "secret=", "secret:",
    "password", "passwd", "authorization", "cookie", "session=",
)

/**
 * 日志文件名：`YunmeiVibe_log_yyyyMMdd_HHmmss.txt.gz`
 * 内容为 gzip 压缩的纯文本报告，因此保留 `.txt.gz` 后缀（保存/分享 Intent 使用 application/gzip）。
 */
fun bugreportFileName(): String {
    val current = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
    return "${APP_NAME_EN}_log_${current}.txt.gz"
}

/**
 * 对一行日志做脱敏：保留 logcat `-v time` 的头部（时间/PID/TID/级别/TAG），
 * 只把 `TAG: 消息` 之后的消息体替换为 ***，因此排障仍能看到"哪个 TAG 在什么时候打了日志"。
 * 无法识别头部时整行替换。
 */
private fun redactLine(line: String): String {
    val lower = line.lowercase()
    if (SENSITIVE_KEYWORDS.none { lower.contains(it) }) return line
    val marker = line.indexOf(": ")
    return if (marker in 1 until line.length - 2) {
        line.substring(0, marker + 1) + " ***（含敏感字段，已脱敏）"
    } else {
        "***（含敏感字段，已脱敏）"
    }
}

/** 逐行脱敏，并在开头给出脱敏行数提示。 */
private fun redactLogcat(raw: String): String {
    val lines = raw.split("\n")
    var redacted = 0
    val out = lines.joinToString("\n") { line ->
        val r = redactLine(line)
        if (r != line) redacted++
        r
    }
    val header = if (redacted == 0) {
        "已脱敏：未发现含敏感关键词的日志行。"
    } else {
        "已脱敏：$redacted 行含敏感关键词（token/userId/lockPwd/secret/password 等）的消息体已替换为 ***。"
    }
    return header + "\n" + out
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
        appendLine("Logcat（已脱敏）:")
        appendLine(redactLogcat(readLogcat()))
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