package com.innovation313.roshancamera.proof

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Renders the proof payload as a QR bitmap.
 *
 * Built on `zxing:core` alone — the `zxing-android-embedded` wrapper would pull
 * in an activity, camera plumbing and resources this app does not need, against
 * a hard size budget.
 */
object QrEncoder {

    /**
     * @param quietZoneModules white border around the code, in modules. Four is
     * the QR specification's minimum; below it, scanners struggle against a
     * photo background.
     */
    fun encode(
        content: String,
        sizePx: Int,
        quietZoneModules: Int = 4
    ): Bitmap {
        val hints = mapOf(
            // Medium correction survives a scuffed print without inflating the
            // module count the way High would.
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to quietZoneModules,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)

        val width = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }
}
