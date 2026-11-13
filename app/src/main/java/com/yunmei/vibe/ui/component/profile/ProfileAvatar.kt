package com.yunmei.vibe.ui.component.profile

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.yunmei.vibe.YunMeiApp
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

/**
 * 个人头像：优先用 OkHttp 拉 `headimg/<userId>.png`，失败或没有地址时显示姓名首字。
 *
 * 项目没有引入图片库（Coil 已在清理轮移除），这里沿用群组成员页的做法：
 * 内存缓存 + 外部缓存目录的 PNG 文件缓存，避免每次进入页面都重新下载。
 */
@Composable
fun ProfileAvatar(
    url: String,
    fallback: String,
    size: Dp,
    containerColor: Color,
    contentColor: Color,
    fontSize: Int = 20,
) {
    val bitmap = rememberAvatarBitmap(url)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (bitmap != null) Color.Transparent else containerColor),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = fallback.take(1),
                color = contentColor,
                fontSize = fontSize.sp,
                fontWeight = FontWeight(550),
            )
        }
    }
}

private val avatarClient by lazy { OkHttpClient() }

private val avatarMemoryCache = object : LinkedHashMap<String, ImageBitmap>(8, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean =
        size > 16
}

@Composable
private fun rememberAvatarBitmap(url: String): ImageBitmap? {
    var bitmap by remember(url) { mutableStateOf(if (url.isBlank()) null else avatarMemoryCache[url]) }
    LaunchedEffect(url) {
        if (url.isBlank() || avatarMemoryCache.containsKey(url)) return@LaunchedEffect
        val loaded = runCatching { downloadAvatar(url) }.getOrNull() ?: return@LaunchedEffect
        avatarMemoryCache[url] = loaded
        bitmap = loaded
    }
    return bitmap
}

private fun downloadAvatar(url: String): ImageBitmap? {
    val cacheFile = File(avatarCacheDir(), avatarFileName(url))
    if (cacheFile.isFile && cacheFile.length() > 0) {
        BitmapFactory.decodeFile(cacheFile.absolutePath)?.let { return it.asImageBitmap() }
    }
    val request = Request.Builder().url(url).build()
    val bytes = avatarClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) return null
        response.body.bytes()
    }
    cacheFile.parentFile?.mkdirs()
    runCatching { cacheFile.writeBytes(bytes) }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
}

private fun avatarCacheDir(): File =
    (YunMeiApp.app.getExternalFilesDir(null) ?: YunMeiApp.app.filesDir).resolve("avatar")

private fun avatarFileName(url: String): String {
    val digest = MessageDigest.getInstance("MD5").digest(url.toByteArray())
    return digest.joinToString("") { "%02x".format(it) } + ".png"
}
