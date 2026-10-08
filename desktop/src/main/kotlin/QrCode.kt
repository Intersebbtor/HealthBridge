package com.healthbridge.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

fun generateQRCode(content: String): ImageBitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512, mapOf(EncodeHintType.MARGIN to 0))
        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = ByteArray(width * height * 4)
        var k = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val v = if (bitMatrix[x, y]) 0x0F.toByte() else 0xFF.toByte()
                pixels[k++] = v
                pixels[k++] = v
                pixels[k++] = v
                pixels[k++] = 0xFF.toByte()
            }
        }
        val bitmap = Bitmap()
        bitmap.allocPixels(ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.PREMUL))
        bitmap.installPixels(pixels)
        bitmap.asComposeImageBitmap()
    } catch (e: Exception) {
        null
    }
}
