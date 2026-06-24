package com.streamingtv.player.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import kotlin.math.min

/**
 * A lightweight placeholder that draws the first letters of a channel/movie
 * name on a colour derived from the name. Gives the grid visual variety when a
 * provider supplies no logos, instead of a wall of identical TV icons.
 */
class InitialsDrawable(name: String) : Drawable() {

    private val initials: String = name.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "?" }

    private val bgColor: Int = PALETTE[Math.floorMod(name.hashCode(), PALETTE.size)]

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val textBounds = Rect()

    override fun draw(canvas: Canvas) {
        val b = bounds
        canvas.drawColor(Color.TRANSPARENT)
        canvas.drawRect(b, bgPaint)
        textPaint.textSize = min(b.width(), b.height()) * 0.42f
        textPaint.getTextBounds(initials, 0, initials.length, textBounds)
        val x = b.exactCenterX()
        val y = b.exactCenterY() - textBounds.exactCenterY()
        canvas.drawText(initials, x, y, textPaint)
    }

    override fun setAlpha(alpha: Int) { bgPaint.alpha = alpha; textPaint.alpha = alpha }
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
    @Deprecated("Deprecated in Drawable")
    override fun getOpacity(): Int = android.graphics.PixelFormat.OPAQUE

    companion object {
        private val PALETTE = intArrayOf(
            Color.parseColor("#1F6FEB"),
            Color.parseColor("#2EA043"),
            Color.parseColor("#8957E5"),
            Color.parseColor("#DB6D28"),
            Color.parseColor("#1A7F7C"),
            Color.parseColor("#BF4B8A"),
            Color.parseColor("#C9510C"),
            Color.parseColor("#3D7DD8")
        )
    }
}
