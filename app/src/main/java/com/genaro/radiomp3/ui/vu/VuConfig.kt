package com.genaro.radiomp3.ui.vu

import android.graphics.Color

data class VuConfig(
    val skin: Skin = Skin.ANALOG_RETRO,
    val sensitivityDb: Float = 0f,              // ±6 dB offset
    val attackMs: Int = 10,                     // attack time (ms)
    val releaseMs: Int = 300,                   // release time (ms)
    val peakHoldSec: Float = 1.5f,              // peak hold duration (sec)
    val maxFps: Int = 30,                       // max frame rate
    val ecoMode: Boolean = false,               // if true: peak only + lower FPS
    val nightMode: Boolean = false,             // dark theme
    val colorBackground: Int = 0,    // beige retro
    val colorNeedle: Int = 0,         // red needle
    val colorScale: Int = 0,                          // scale text
    val colorYellow: Int = 0,         // yellow threshold
    val colorRed: Int = 0,            // red threshold
    val showGlassReflection: Boolean = true,    // show glass shine effect
    val showPeakIndicator: Boolean = true       // show peak hold dot
) {
    enum class Skin {
        ANALOG_RETRO,   // classic analog gauge with needles
        LED_BAR         // future: LED bar display
    }

    companion object {
        fun light(): VuConfig = VuConfig(
            colorBackground = Color.parseColor("#F3E2B8"),
            colorNeedle = Color.parseColor("#D42B2B"),
            colorScale = Color.BLACK,
            colorYellow = Color.parseColor("#FFC107"),
            colorRed = Color.parseColor("#E53935"),
            nightMode = false
        )

        fun dark(): VuConfig = VuConfig(
            colorBackground = Color.parseColor("#2C2C2C"),
            colorNeedle = Color.parseColor("#FF6B6B"),
            colorScale = Color.parseColor("#FFFFFF"),
            colorYellow = Color.parseColor("#FFD700"),
            colorRed = Color.parseColor("#FF4444"),
            nightMode = true
        )
    }
}
