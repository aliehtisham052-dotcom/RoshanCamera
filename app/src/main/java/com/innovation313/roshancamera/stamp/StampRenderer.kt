package com.innovation313.roshancamera.stamp

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.innovation313.roshancamera.proof.QrEncoder
import kotlin.math.roundToInt

/**
 * Draws the proof band onto a captured frame.
 *
 * Everything is sized as a fraction of the image width so a 12 MP photo and a
 * 2 MP photo carry a stamp of the same visual weight. Nothing here touches a
 * view or a resource, which keeps it off the main thread and unit-testable.
 */
object StampRenderer {

    /**
     * Draws onto [source] directly when it is mutable, and only copies when it
     * is not. A twelve-megapixel frame is about 48 MB in memory; an
     * unconditional copy would put two of them on the heap at once, on phones
     * that may only have a couple of hundred megabytes to give an app.
     */
    fun render(source: Bitmap, content: StampContent): Bitmap {
        val output = if (source.isMutable) source else source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)

        val width = output.width
        val unit = width / 100f

        val qrSize = (unit * 18f).roundToInt().coerceAtLeast(96)
        val padding = unit * 3f
        val lineGap = unit * 1.2f
        val textSize = unit * 3.4f

        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.textSize = textSize
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val heading = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BRAND_GOLD
            this.textSize = textSize * 1.15f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        val lines = content.lines()
        val textBlockHeight = lines.size * (textSize + lineGap)
        val bandHeight = maxOf(qrSize + padding * 2, textBlockHeight + padding * 2)
        val bandTop = output.height - bandHeight

        canvas.drawRect(
            RectF(0f, bandTop, width.toFloat(), output.height.toFloat()),
            Paint().apply { color = BAND_INK }
        )
        canvas.drawRect(
            RectF(0f, bandTop, width.toFloat(), bandTop + unit * 0.4f),
            Paint().apply { color = BRAND_GOLD }
        )

        val qr = QrEncoder.encode(content.qrPayload, qrSize)
        val qrLeft = width - qrSize - padding
        val qrTop = bandTop + (bandHeight - qrSize) / 2f
        // White plate behind the code: a QR loses scannability the moment its
        // quiet zone sits on top of photo detail.
        canvas.drawRect(
            RectF(qrLeft - unit, qrTop - unit, qrLeft + qrSize + unit, qrTop + qrSize + unit),
            Paint().apply { color = Color.WHITE }
        )
        canvas.drawBitmap(qr, qrLeft, qrTop, null)
        qr.recycle()

        var baseline = bandTop + padding + textSize
        lines.forEachIndexed { index, line ->
            canvas.drawText(line, padding, baseline, if (index == 0) heading else body)
            baseline += textSize + lineGap
        }

        return output
    }

    private val BRAND_GOLD = Color.parseColor("#D9A441")
    private val BAND_INK = Color.parseColor("#CC0D0F14")
}

/**
 * The text that goes on the photo. Assembled by the caller so this class stays
 * free of Android resources and can be exercised from a plain unit test.
 */
data class StampContent(
    val addressLine: String,
    val coordinatesLine: String,
    val dateTimeLine: String,
    val accuracyLine: String,
    val businessName: String?,
    val qrPayload: String
) {
    fun lines(): List<String> = buildList {
        add(businessName?.takeIf { it.isNotBlank() } ?: DEFAULT_HEADING)
        add(addressLine)
        add(coordinatesLine)
        add(dateTimeLine)
        add(accuracyLine)
    }

    private companion object {
        const val DEFAULT_HEADING = "Roshan Camera"
    }
}
