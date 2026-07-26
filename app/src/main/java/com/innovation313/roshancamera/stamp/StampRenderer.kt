package com.innovation313.roshancamera.stamp

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import com.innovation313.roshancamera.proof.QrEncoder
import kotlin.math.roundToInt

/**
 * Draws the stamp card in the layout of the reference the owner supplied:
 * a floating rounded card near the bottom edge — map tile on the left with a
 * pin, bold region line and exact address in the middle, temperature top-right
 * and the QR (which opens the exact spot in Google Maps) under it.
 *
 * Every measurement is a fraction of the image width so a 12 MP photo and a
 * 2 MP photo carry a stamp of the same visual weight. Draws onto [source]
 * directly when it is mutable — an unconditional copy would put two ~48 MB
 * frames on the heap at once on exactly the phones this app is for.
 */
object StampRenderer {

    fun render(source: Bitmap, content: StampContent): Bitmap {
        val output = if (source.isMutable) source else source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)

        val w = output.width
        val u = w / 100f
        val margin = u * 2.5f
        val pad = u * 2.2f

        val heading = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = u * 3.6f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = u * 2.9f
        }
        val muted = Paint(body).apply { color = Color.argb(255, 216, 220, 228) }
        val tempPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = u * 3.4f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val tiny = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = u * 1.6f
        }

        val textLines = buildList {
            add(heading to content.regionLine)
            content.exactLines().forEach { add(body to it) }
            add(muted to content.dateTimeLine)
            content.businessName?.takeIf { it.isNotBlank() }?.let { add(muted to it) }
        }
        val lineGap = u * 1.1f
        val textHeight = textLines.sumOf { (p, _) -> (p.textSize + lineGap).toDouble() }.toFloat()

        val tileSize = u * 17f
        val qrSize = (u * 10f).roundToInt().coerceAtLeast(72)
        val rightColumn = maxOf(qrSize + u * 5f, tempPaint.textSize + qrSize + u * 2f)
        val cardHeight = maxOf(tileSize, textHeight, qrSize + tempPaint.textSize + u * 3f) + pad * 2
        val cardTop = output.height - margin - cardHeight
        val card = RectF(margin, cardTop, w - margin, output.height - margin)

        canvas.drawRoundRect(card, u * 1.5f, u * 1.5f, Paint().apply {
            color = Color.argb(158, 0, 0, 0)
        })

        // Map tile with pin and the attribution OSM's policy asks for.
        var textLeft = card.left + pad
        content.mapTile?.let { tile ->
            val dst = RectF(
                card.left + pad,
                card.top + (cardHeight - tileSize) / 2f,
                card.left + pad + tileSize,
                card.top + (cardHeight + tileSize) / 2f
            )
            canvas.drawBitmap(tile.bitmap, null, dst, null)
            val pinX = dst.left + tile.pinX / 256f * tileSize
            val pinY = dst.top + tile.pinY / 256f * tileSize
            canvas.drawCircle(pinX, pinY, u * 1.1f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
            })
            canvas.drawCircle(pinX, pinY, u * 0.75f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(255, 143, 0)
            })
            canvas.drawText("© OSM", dst.left + u * 0.5f, dst.bottom - u * 0.6f, tiny)
            textLeft = dst.right + pad
        }

        // Right column: temperature above the QR.
        val qr = QrEncoder.encode(content.qrPayload, qrSize)
        val qrLeft = card.right - pad - qrSize
        val qrTop = card.bottom - pad - qrSize
        content.temperature?.let {
            val label = it
            val tw = tempPaint.measureText(label)
            canvas.drawText(label, card.right - pad - tw, qrTop - u * 1.2f, tempPaint)
        }
        canvas.drawRect(
            RectF(qrLeft - u * 0.7f, qrTop - u * 0.7f, qrLeft + qrSize + u * 0.7f, qrTop + qrSize + u * 0.7f),
            Paint().apply { color = Color.WHITE }
        )
        canvas.drawBitmap(qr, qrLeft, qrTop, null)
        qr.recycle()

        // Text block, vertically centred, clipped clear of the right column.
        val textRight = qrLeft - pad
        var baseline = card.top + (cardHeight - textHeight) / 2f + textLines.first().first.textSize
        val clip = Rect(textLeft.toInt(), card.top.toInt(), textRight.toInt(), card.bottom.toInt())
        canvas.save()
        canvas.clipRect(clip)
        textLines.forEach { (paint, line) ->
            canvas.drawText(line, textLeft, baseline, paint)
            baseline += paint.textSize + lineGap
        }
        canvas.restore()

        return output
    }
}

/**
 * What goes on the photo. Free of Android resources so a plain unit test can
 * exercise it.
 */
data class StampContent(
    val regionLine: String,
    val exactAddress: String,
    val dateTimeLine: String,
    val temperature: String?,
    val businessName: String?,
    val qrPayload: String,
    val mapTile: MapTileBitmap? = null
) {
    /** The exact address, folded near a comma when it runs long. */
    fun exactLines(): List<String> {
        if (exactAddress.length <= WRAP_AT) return listOf(exactAddress)
        val cut = exactAddress.lastIndexOf(", ", WRAP_AT)
        if (cut <= 0) return listOf(exactAddress)
        return listOf(
            exactAddress.substring(0, cut + 1).trimEnd(),
            exactAddress.substring(cut + 2)
        )
    }

    private companion object {
        const val WRAP_AT = 44
    }
}

/** Renderer-agnostic carrier so StampContent stays testable off-device. */
data class MapTileBitmap(val bitmap: Bitmap, val pinX: Int, val pinY: Int)
