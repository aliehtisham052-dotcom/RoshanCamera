package com.innovation313.roshancamera.stamp

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.innovation313.roshancamera.proof.QrEncoder
import kotlin.math.roundToInt

/**
 * Draws the stamp in the owner's mockup design: a stack of dark rounded pills
 * up the lower-left of the photo — date/time, coordinates, address (two
 * lines), altitude and accuracy, weather, a small map with the pin, compass
 * heading, and a "Captured by …" watermark line — with the proof QR (which
 * opens the exact spot in Google Maps) tucked into the lower-right corner.
 *
 * Every measurement is a fraction of the image width so a 12 MP photo and a
 * 2 MP photo carry a stamp of the same visual weight. Draws onto [source]
 * directly when it is mutable — an unconditional copy would put two ~48 MB
 * frames on the heap at once on exactly the phones this app is for.
 *
 * Icon badges arrive pre-rendered as [StampIcons] (the same vector art the
 * screen shows), so this class stays free of Android resources and a plain
 * unit test can still exercise the layout logic with icons = null.
 */
object StampRenderer {

    private class Row(
        val icon: Bitmap?,
        val lines: List<Line>
    )

    private class Line(val text: String, val bold: Boolean)

    fun render(source: Bitmap, content: StampContent, icons: StampIcons? = null): Bitmap {
        val output = if (source.isMutable) source else source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)

        val w = output.width
        val u = w / 100f
        val margin = u * 2.5f
        val padH = u * 1.8f
        val padV = u * 1.2f
        val badge = u * 4.2f
        val gap = u * 1.4f
        val rowGap = u * 1.2f
        val corner = u * 1.6f

        val bold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = u * 2.8f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = u * 2.6f
        }
        val tiny = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = u * 1.5f
        }
        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(179, 8, 18, 33)
        }

        // The rows, top to bottom, in the mockup's order. Null content skips
        // its row rather than printing a blank.
        val addressLines = buildList {
            add(Line(content.regionLine, bold = true))
            content.exactLines().forEach { add(Line(it, bold = false)) }
        }
        // The map block is drawn between these two groups, per the mockup.
        val aboveMap = buildList {
            add(Row(icons?.calendar, listOf(Line(content.dateTimeLine, bold = true))))
            content.coordsLine?.let { add(Row(icons?.pin, listOf(Line(it, bold = false)))) }
            add(Row(icons?.home, addressLines))
            content.altitudeLine?.let { add(Row(icons?.mountain, listOf(Line(it, bold = false)))) }
            content.weatherLine?.let { add(Row(icons?.sun, listOf(Line(it, bold = false)))) }
        }
        val belowMap = buildList {
            content.compassLine?.let { add(Row(icons?.compass, listOf(Line(it, bold = false)))) }
            content.watermarkLine?.let { add(Row(icons?.camera, listOf(Line(it, bold = true)))) }
        }

        fun paintFor(line: Line) = if (line.bold) bold else body
        fun rowHeight(row: Row): Float {
            val text = row.lines.sumOf { (paintFor(it).textSize + u * 0.6f).toDouble() }.toFloat() - u * 0.6f
            return maxOf(badge, text) + padV * 2
        }
        fun rowWidth(row: Row): Float {
            val text = row.lines.maxOf { paintFor(it).measureText(it.text) }
            val iconSpan = if (row.icon != null || icons != null) badge + gap else 0f
            return padH + iconSpan + text + padH
        }

        val mapWidth = u * 30f
        val mapHeight = u * 19f

        var y = output.height - margin

        // Draw bottom-up: watermark first, then compass, map, then the rest.
        fun drawRow(row: Row, bottom: Float): Float {
            val h = rowHeight(row)
            val rw = rowWidth(row).coerceAtMost(w - margin * 2)
            val rect = RectF(margin, bottom - h, margin + rw, bottom)
            canvas.drawRoundRect(rect, corner, corner, pillPaint)

            var textLeft = rect.left + padH
            row.icon?.let {
                val iconRect = RectF(
                    rect.left + padH,
                    rect.centerY() - badge / 2,
                    rect.left + padH + badge,
                    rect.centerY() + badge / 2
                )
                canvas.drawBitmap(it, null, iconRect, null)
                textLeft = iconRect.right + gap
            } ?: run { if (icons != null) textLeft += badge + gap }

            val textHeight = row.lines.sumOf { (paintFor(it).textSize + u * 0.6f).toDouble() }
                .toFloat() - u * 0.6f
            var baseline = rect.centerY() - textHeight / 2 + row.lines.first().let { paintFor(it).textSize * 0.85f }
            canvas.save()
            canvas.clipRect(rect)
            row.lines.forEach { line ->
                canvas.drawText(line.text, textLeft, baseline, paintFor(line))
                baseline += paintFor(line).textSize + u * 0.6f
            }
            canvas.restore()
            return rect.top - rowGap
        }

        belowMap.reversed().forEach { y = drawRow(it, y) }

        content.mapTile?.let { tile ->
            val rect = RectF(margin, y - mapHeight, margin + mapWidth, y)
            val clip = Path().apply { addRoundRect(rect, corner, corner, Path.Direction.CW) }
            canvas.save()
            canvas.clipPath(clip)
            canvas.drawBitmap(tile.bitmap, null, rect, null)
            // The fix, pinned where it falls on this tile.
            val pinX = rect.left + tile.pinX / 256f * rect.width()
            val pinY = rect.top + tile.pinY / 256f * rect.height()
            canvas.drawCircle(pinX, pinY, u * 1.1f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
            })
            canvas.drawCircle(pinX, pinY, u * 0.75f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(229, 57, 53)
            })
            canvas.drawText("© OSM", rect.left + u * 0.6f, rect.bottom - u * 0.7f, tiny)
            canvas.restore()
            y = rect.top - rowGap
        }

        aboveMap.reversed().forEach { y = drawRow(it, y) }

        // Proof QR, lower-right corner, clear of the pill stack.
        val qrSize = (u * 9f).roundToInt().coerceAtLeast(72)
        val qr = QrEncoder.encode(content.qrPayload, qrSize)
        val qrLeft = w - margin - qrSize
        val qrTop = output.height - margin - qrSize
        canvas.drawRect(
            RectF(
                qrLeft - u * 0.7f, qrTop - u * 0.7f,
                qrLeft + qrSize + u * 0.7f, qrTop + qrSize + u * 0.7f
            ),
            Paint().apply { color = Color.WHITE }
        )
        canvas.drawBitmap(qr, qrLeft.toFloat(), qrTop.toFloat(), null)
        qr.recycle()

        return output
    }
}

/**
 * What goes on the photo. Free of Android resources so a plain unit test can
 * exercise it. The original six fields keep their meaning; the newer rows are
 * optional and simply absent when null.
 */
data class StampContent(
    val regionLine: String,
    val exactAddress: String,
    val dateTimeLine: String,
    val temperature: String?,
    val businessName: String?,
    val qrPayload: String,
    val mapTile: MapTileBitmap? = null,
    val coordsLine: String? = null,
    val altitudeLine: String? = null,
    val weatherLine: String? = null,
    val compassLine: String? = null,
    val watermarkLine: String? = null
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

/** Pre-rendered icon badges — the same art the live overlay shows. */
data class StampIcons(
    val calendar: Bitmap,
    val pin: Bitmap,
    val home: Bitmap,
    val mountain: Bitmap,
    val sun: Bitmap,
    val compass: Bitmap,
    val camera: Bitmap
)
