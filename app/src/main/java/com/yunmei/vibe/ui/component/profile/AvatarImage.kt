package com.yunmei.vibe.ui.component.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream

/**
 * 头像图片预处理：方形裁剪 + 缩放到 512 + JPEG 压缩。
 *
 * 官方前端用的是 `yq-avatar` 组件（`selWidth/selHeight 400upx`、`expWidth/expHeight 200upx`、
 * `inner=true`、`quality=0.5`），也就是「先跳系统裁剪页框成方形，再按 200px、质量 0.5 导出」。
 * 云莓没有内置裁剪 UI，这里退一步：居中裁成正方形再缩到 512、质量 90——体积仍然很小，
 * 显示质量还更好一些，也不用额外引入裁剪库。
 *
 * 只在 IO 线程调用（解码整张图是重活）。
 */
object AvatarImage {

    /** 输出边长：服务端头像按 200px 生成，512 足够清晰且不至于让上传体过大。 */
    private const val TARGET_SIZE = 512

    private const val JPEG_QUALITY = 90

    /** 读不到、解码失败一律返回 null，由调用方提示用户。 */
    fun prepare(context: Context, uri: Uri): ByteArray? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, TARGET_SIZE)
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        val square = cropSquare(decoded)
        val scaled = if (square.width > TARGET_SIZE) {
            Bitmap.createScaledBitmap(square, TARGET_SIZE, TARGET_SIZE, true)
        } else {
            square
        }
        ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
    }.getOrNull()

    /** 计算 2 的幂采样率：只要求解码结果不小于目标边长的两倍，避免一次解出整张原图。 */
    private fun sampleSize(width: Int, height: Int, target: Int): Int {
        var sample = 1
        var shortSide = minOf(width, height)
        while (shortSide / 2 >= target * 2) {
            shortSide /= 2
            sample *= 2
        }
        return sample
    }

    /** 居中裁剪成正方形；原图本身是方形时直接返回，省一次内存拷贝。 */
    private fun cropSquare(source: Bitmap): Bitmap {
        val side = minOf(source.width, source.height)
        if (source.width == side && source.height == side) return source
        val left = (source.width - side) / 2
        val top = (source.height - side) / 2
        return Bitmap.createBitmap(source, left, top, side, side)
    }
}
