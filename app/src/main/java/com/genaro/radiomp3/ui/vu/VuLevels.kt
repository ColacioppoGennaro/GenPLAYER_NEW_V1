package com.genaro.radiomp3.ui.vu

/**
 * Dati di livello audio per il VU Meter (Peak e RMS in dBFS)
 * Valori tipici: -90 (silenzio) a 0 (max senza clipping)
 */
data class VuLevels(
    val peakL: Float,   // dBFS, left channel peak
    val peakR: Float,   // dBFS, right channel peak
    val rmsL: Float,    // dBFS, left channel RMS
    val rmsR: Float     // dBFS, right channel RMS
) {
    companion object {
        fun silence() = VuLevels(-90f, -90f, -90f, -90f)
    }
}
