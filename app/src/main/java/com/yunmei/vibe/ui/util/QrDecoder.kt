package com.yunmei.vibe.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.io.InputStream

/**
 * 从静态图片（相机拍照 / 相册选择）解码二维码内容。
 *
 * 相机拍照（ACTION_IMAGE_CAPTURE）与相册选择（Photo Picker）两条取图路径都汇聚到这里，
 * 统一走 ZXing core 的静态图解码，输出二维码原文字符串，
 * 交由既有的 `LocksViewModel.addLockFromShare(raw)` 做门锁解析与添加。
 *
 * @return 解码成功返回二维码原文；无法解码/图片不可读返回 null。
 */
fun decodeQrFromUri(context: Context, uri: Uri): String? {
    val bytes = runCatching {
        context.contentResolver.openInputStream(uri)?.use(InputStream::readBytes)
    }.getOrNull() ?: return null
    if (bytes.isEmpty()) return null
    val bitmap = decodeSampledBitmap(bytes) ?: return null
    return decodeQrFromBitmap(bitmap)
}

/** 按需下采样，避免整张大图直接解码造成 OOM。 */
private fun decodeSampledBitmap(bytes: ByteArray, maxSize: Int = 2000): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    while (bounds.outWidth / (sampleSize * 2) >= maxSize || bounds.outHeight / (sampleSize * 2) >= maxSize) {
        sampleSize *= 2
    }

    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

private fun decodeQrFromBitmap(bitmap: Bitmap): String? {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 0 || height <= 0) return null

    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    val source = RGBLuminanceSource(width, height, pixels)
    val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
    val hints = mapOf(
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
    )

    return runCatching {
        MultiFormatReader().decode(binaryBitmap, hints).text
    }.getOrNull()
}
