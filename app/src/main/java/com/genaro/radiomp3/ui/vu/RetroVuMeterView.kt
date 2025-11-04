package com.genaro.radiomp3.ui.vu

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * RetroVuMeterView: Gauge analogico retrò con due lancette (L/R).
 * Supporta attack/release ballistica, peak-hold, colori personalizzabili.
 *
 * Caratteristiche:
 * - Due strumenti affiancati L e R
 * - Scala dB da -20 a +3
 * - Lancette fluide con attack/release
 * - Puntino di peak-hold opzionale
 * - Riflesso vetro opzionale
 * - Modalità notte predisposta
 */
class RetroVuMeterView @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null
) : View(ctx, attrs) {

    var config: VuConfig = VuConfig.light()
        set(value) {
            field = value
            invalidate()
        }

    private var targetLevels = VuLevels.silence()
    private var displayLevels = VuLevels.silence()
    private var peakHoldL = -90f
    private var peakHoldR = -90f
    private var lastPeakHoldTimeL = 0L
    private var lastPeakHoldTimeR = 0L

    private var lastFrameTime = 0L

    // Paint objects (riutilizzate per evitare allocazioni)
    private val paintBackground = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    private val paintBorder = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val paintScale = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 2f
        textSize = 32f
    }
    private val paintNeedle = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val paintLabel = Paint().apply {
        isAntiAlias = true
        textSize = 40f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val paintPeakDot = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    private val paintReflection = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    fun setLevels(levels: VuLevels) {
        android.util.Log.d("VU_DEBUG", "RetroVuMeterView: setLevels called - L=${levels.peakL} R=${levels.peakR}")

        // Applica sensibilità offset
        val off = config.sensitivityDb
        targetLevels = levels.copy(
            peakL = levels.peakL + off,
            peakR = levels.peakR + off,
            rmsL = levels.rmsL + off,
            rmsR = levels.rmsR + off
        )

        android.util.Log.d("VU_DEBUG", "RetroVuMeterView: After sensitivity offset - L=${targetLevels.peakL} R=${targetLevels.peakR}")

        // Aggiorna peak-hold
        val now = System.currentTimeMillis()
        if (targetLevels.peakL > peakHoldL) {
            peakHoldL = targetLevels.peakL
            lastPeakHoldTimeL = now
        } else if (now - lastPeakHoldTimeL > (config.peakHoldSec * 1000).toLong()) {
            peakHoldL = targetLevels.peakL
            lastPeakHoldTimeL = now
        }
        if (targetLevels.peakR > peakHoldR) {
            peakHoldR = targetLevels.peakR
            lastPeakHoldTimeR = now
        } else if (now - lastPeakHoldTimeR > (config.peakHoldSec * 1000).toLong()) {
            peakHoldR = targetLevels.peakR
            lastPeakHoldTimeR = now
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post(frameRunnable)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(frameRunnable)
        super.onDetachedFromWindow()
    }

    private val frameRunnable = object : Runnable {
        override fun run() {
            val now = System.nanoTime()
            val dtMs = if (lastFrameTime == 0L) 16f else (now - lastFrameTime) / 1_000_000f
            lastFrameTime = now

            // Attack/Release ballistica
            displayLevels = displayLevels.copy(
                peakL = smooth(displayLevels.peakL, targetLevels.peakL, dtMs, config.attackMs, config.releaseMs),
                peakR = smooth(displayLevels.peakR, targetLevels.peakR, dtMs, config.attackMs, config.releaseMs),
                rmsL = smooth(displayLevels.rmsL, targetLevels.rmsL, dtMs, config.attackMs, config.releaseMs),
                rmsR = smooth(displayLevels.rmsR, targetLevels.rmsR, dtMs, config.attackMs, config.releaseMs)
            )

            invalidate()

            val fps = if (config.ecoMode) min(30, config.maxFps) else config.maxFps
            val frameDelayMs = (1000L / fps.coerceIn(15, 60)).toLong()
            postDelayed(this, frameDelayMs)
        }
    }

    private fun smooth(current: Float, target: Float, dtMs: Float, attackMs: Int, releaseMs: Int): Float {
        val attack = max(1f, attackMs.toFloat())
        val release = max(1f, releaseMs.toFloat())
        val timeConstant = if (target > current) attack else release
        val alpha = min(1f, dtMs / timeConstant)
        return current + (target - current) * alpha
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawBackground(canvas)
        val gaugeWidth = width / 2f
        drawGauge(canvas, 0f, 0f, gaugeWidth, height.toFloat(), true, displayLevels, peakHoldL)
        drawGauge(canvas, gaugeWidth, 0f, gaugeWidth, height.toFloat(), false, displayLevels, peakHoldR)
    }

    private fun drawBackground(canvas: Canvas) {
        paintBackground.color = config.colorBackground
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paintBackground)
    }

    private fun drawGauge(
        canvas: Canvas,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        isLeft: Boolean,
        levels: VuLevels,
        peakHold: Float
    ) {
        canvas.save()
        canvas.translate(x, y)

        val margin = 12f
        val cx = w / 2f
        val cy = h * 0.65f
        val radius = (w - margin * 2) * 0.35f

        // Sfondo gauge
        paintBorder.color = config.colorScale
        paintBorder.style = Paint.Style.STROKE
        paintBorder.strokeWidth = 3f
        canvas.drawRoundRect(
            RectF(margin, margin, w - margin, h - margin),
            16f,
            16f,
            paintBorder
        )

        // Scala e tacche (da -20 a +3 dB)
        drawScale(canvas, cx, cy, radius)

        // Lancetta principale (peak per eco mode, altrimenti RMS)
        val db = if (config.ecoMode) {
            max(if (isLeft) levels.peakL else levels.peakR, if (isLeft) levels.rmsL else levels.rmsR)
        } else {
            if (isLeft) levels.peakL else levels.peakR
        }
        drawNeedle(canvas, cx, cy, radius, db, config.colorNeedle)

        // Puntino peak-hold opzionale
        if (config.showPeakIndicator) {
            drawPeakDot(canvas, cx, cy, radius, peakHold)
        }

        // Label L/R
        paintLabel.color = config.colorScale
        canvas.drawText(if (isLeft) "L" else "R", cx, h - 20f, paintLabel)

        // Riflesso vetro opzionale
        if (config.showGlassReflection) {
            drawGlassReflection(canvas, margin, margin, w - margin * 2, h * 0.3f)
        }

        canvas.restore()
    }

    private fun drawScale(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val dBValues = listOf(-20, -10, -7, -5, -3, -2, -1, 0, 1, 2, 3)
        val minDb = -20f
        val maxDb = 3f
        val angleStart = -140f  // Ore 10 (~8 o'clock position)
        val angleEnd = -40f     // Ore 2 (~4 o'clock position)
        val angleRange = angleEnd - angleStart

        paintScale.color = config.colorScale
        paintScale.style = Paint.Style.STROKE
        paintScale.strokeWidth = 2f

        for (db in dBValues) {
            val t = (db - minDb) / (maxDb - minDb)
            val angle = angleStart + t * angleRange
            val rad = Math.toRadians(angle.toDouble())
            val x1 = cx + (radius - 15f) * cos(rad).toFloat()
            val y1 = cy + (radius - 15f) * sin(rad).toFloat()
            val x2 = cx + (radius - 5f) * cos(rad).toFloat()
            val y2 = cy + (radius - 5f) * sin(rad).toFloat()
            canvas.drawLine(x1, y1, x2, y2, paintScale)

            // Numero ogni 5 dB
            if (db % 5 == 0) {
                val x3 = cx + (radius - 30f) * cos(rad).toFloat()
                val y3 = cy + (radius - 30f) * sin(rad).toFloat()
                paintScale.style = Paint.Style.FILL
                paintScale.textSize = 24f
                paintScale.textAlign = Paint.Align.CENTER
                canvas.drawText(db.toString(), x3, y3 + 8f, paintScale)
                paintScale.style = Paint.Style.STROKE
            }
        }
    }

    private fun drawNeedle(canvas: Canvas, cx: Float, cy: Float, radius: Float, db: Float, color: Int) {
        val minDb = -20f
        val maxDb = 3f
        val angleStart = -140f  // Ore 10 (~8 o'clock position)
        val angleEnd = -40f     // Ore 2 (~4 o'clock position)
        val angleRange = angleEnd - angleStart

        val t = (db.coerceIn(minDb, maxDb) - minDb) / (maxDb - minDb)
        val angle = angleStart + t * angleRange
        val rad = Math.toRadians(angle.toDouble())

        val needleLen = radius * 0.85f
        val x2 = cx + needleLen * cos(rad).toFloat()
        val y2 = cy + needleLen * sin(rad).toFloat()

        paintNeedle.color = color
        paintNeedle.strokeWidth = 6f
        paintNeedle.style = Paint.Style.STROKE
        canvas.drawLine(cx, cy, x2, y2, paintNeedle)

        // Perno
        paintNeedle.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, 8f, paintNeedle)
    }

    private fun drawPeakDot(canvas: Canvas, cx: Float, cy: Float, radius: Float, peakDb: Float) {
        val minDb = -20f
        val maxDb = 3f
        val angleStart = -140f  // Ore 10 (~8 o'clock position)
        val angleEnd = -40f     // Ore 2 (~4 o'clock position)
        val angleRange = angleEnd - angleStart

        val t = (peakDb.coerceIn(minDb, maxDb) - minDb) / (maxDb - minDb)
        val angle = angleStart + t * angleRange
        val rad = Math.toRadians(angle.toDouble())

        val peakLen = radius * 0.80f
        val peakX = cx + peakLen * cos(rad).toFloat()
        val peakY = cy + peakLen * sin(rad).toFloat()

        paintPeakDot.color = if (peakDb >= 0) config.colorRed else config.colorYellow
        canvas.drawCircle(peakX, peakY, 4f, paintPeakDot)
    }

    private fun drawGlassReflection(canvas: Canvas, x: Float, y: Float, w: Float, h: Float) {
        paintReflection.shader = LinearGradient(
            x, y, x, y + h,
            Color.valueOf(1f, 1f, 1f, 60/255f).toArgb(),
            Color.valueOf(1f, 1f, 1f, 0f).toArgb(),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(RectF(x, y, x + w, y + h), 16f, 16f, paintReflection)
    }
}
