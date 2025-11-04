package com.genaro.radiomp3.ui.vu

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Versione semplificata del VU Meter che non estende più AudioProcessor.
 * Evita errori di riferimento legati alle versioni di Media3.
 * Calcola Peak e RMS stereo da PCM 16-bit.
 */
@UnstableApi
class VuMeterProcessor(
    private val onLevels: (VuLevels) -> Unit,
    private val windowSamples: Int = 1024
) {

    private val emptyBuffer = ByteBuffer.allocate(0)

    fun processBuffer(buffer: ShortArray, length: Int) {
        var peakL = 0.0
        var peakR = 0.0
        var sumSqL = 0.0
        var sumSqR = 0.0
        var n = 0

        var i = 0
        while (i < length && i + 1 < buffer.size) {
            val l = buffer[i].toDouble() / 32768.0
            val r = buffer[i + 1].toDouble() / 32768.0
            val al = abs(l)
            val ar = abs(r)

            if (al > peakL) peakL = al
            if (ar > peakR) peakR = ar
            sumSqL += l * l
            sumSqR += r * r
            n++
            i += 2
        }

        val rmsL = if (n > 0) sqrt(sumSqL / n) else 0.0
        val rmsR = if (n > 0) sqrt(sumSqR / n) else 0.0

        onLevels(VuLevels(toDb(peakL), toDb(peakR), toDb(rmsL), toDb(rmsR)))
    }

    private fun toDb(linear: Double): Float {
        return when {
            linear <= 1e-9 -> -90f
            linear > 1.0 -> 0f
            else -> (20.0 * log10(linear)).toFloat()
        }
    }
}