package com.genaro.radiomp3.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Drawable

/**
 * Custom drawable per la barra colorata dei pulsanti homepage
 * Basato su SVG specification - barra con angoli arrotondati a sinistra
 * che si assottiglia verso destra
 */
class BarShapeDrawable(private val color: Int) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = this@BarShapeDrawable.color
        style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas) {
        val width = bounds.width().toFloat()
        val height = bounds.height().toFloat()

        // Scala da SVG viewBox (357.5 x 128) al nostro rettangolo
        // Il path SVG ha larghezza effettiva ~55.5 (da x=10 a x=65.5)
        // e altezza ~108 (da y=10 a y=118)
        val svgWidth = 55.5f
        val svgHeight = 108f
        val svgOffsetX = 10f
        val svgOffsetY = 10f

        val scaleX = width / svgWidth
        val scaleY = height / svgHeight

        // Crea il path basato sul SVG
        val path = Path().apply {
            // Punto di inizio (10, 37) -> scalato
            moveTo((10f - svgOffsetX) * scaleX, (37f - svgOffsetY) * scaleY)

            // Curva arrotondata top-left: Bezier C(10, 19, 19, 10, 37, 10)
            cubicTo(
                (10f - svgOffsetX) * scaleX, (19f - svgOffsetY) * scaleY,
                (19f - svgOffsetX) * scaleX, (10f - svgOffsetY) * scaleY,
                (37f - svgOffsetX) * scaleX, (10f - svgOffsetY) * scaleY
            )

            // Linea destra top: L(65.5, 10)
            lineTo((65.5f - svgOffsetX) * scaleX, (10f - svgOffsetY) * scaleY)

            // Curva "apertura" top-right: C(47.5, 10, 38.5, 19, 38.5, 37)
            cubicTo(
                (47.5f - svgOffsetX) * scaleX, (10f - svgOffsetY) * scaleY,
                (38.5f - svgOffsetX) * scaleX, (19f - svgOffsetY) * scaleY,
                (38.5f - svgOffsetX) * scaleX, (37f - svgOffsetY) * scaleY
            )

            // Linea destra: L(38.5, 91)
            lineTo((38.5f - svgOffsetX) * scaleX, (91f - svgOffsetY) * scaleY)

            // Curva "apertura" bottom-right: C(38.5, 109, 47.5, 118, 65.5, 118)
            cubicTo(
                (38.5f - svgOffsetX) * scaleX, (109f - svgOffsetY) * scaleY,
                (47.5f - svgOffsetX) * scaleX, (118f - svgOffsetY) * scaleY,
                (65.5f - svgOffsetX) * scaleX, (118f - svgOffsetY) * scaleY
            )

            // Linea sinistra bottom: L(37, 118)
            lineTo((37f - svgOffsetX) * scaleX, (118f - svgOffsetY) * scaleY)

            // Curva arrotondata bottom-left: C(19, 118, 10, 109, 10, 91)
            cubicTo(
                (19f - svgOffsetX) * scaleX, (118f - svgOffsetY) * scaleY,
                (10f - svgOffsetX) * scaleX, (109f - svgOffsetY) * scaleY,
                (10f - svgOffsetX) * scaleX, (91f - svgOffsetY) * scaleY
            )

            // Chiudi path
            close()
        }

        canvas.drawPath(path, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}
