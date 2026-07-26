package com.innovation313.roshancamera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * Rule-of-thirds guide over the viewfinder. Drawn, not laid out — four lines
 * do not justify four views.
 */
class GridOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint().apply {
        color = Color.WHITE
        alpha = 90
        strokeWidth = resources.displayMetrics.density
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawLine(w / 3, 0f, w / 3, h, paint)
        canvas.drawLine(2 * w / 3, 0f, 2 * w / 3, h, paint)
        canvas.drawLine(0f, h / 3, w, h / 3, paint)
        canvas.drawLine(0f, 2 * h / 3, w, 2 * h / 3, paint)
    }
}
