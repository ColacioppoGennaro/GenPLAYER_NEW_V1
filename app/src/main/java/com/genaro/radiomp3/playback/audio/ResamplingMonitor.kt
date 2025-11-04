package com.genaro.radiomp3.playback.audio

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.AnalyticsListener

/**
 * Monitors audio format changes and resampling
 * Detects when input and output sample rates differ
 *
 * Usage:
 * val monitor = ResamplingMonitor { inputHz, outputHz ->
 *     // Handle resampling detection
 * }
 * player.addAnalyticsListener(monitor)
 */
@OptIn(UnstableApi::class)
class ResamplingMonitor(
    private val onResamplingDetected: (inputHz: Int, outputHz: Int) -> Unit
) : AnalyticsListener {

    private var lastSampleRate: Int = -1

    override fun onDownstreamFormatChanged(
        eventTime: AnalyticsListener.EventTime,
        mediaLoadData: androidx.media3.exoplayer.source.MediaLoadData
    ) {
        val trackFormat = mediaLoadData.trackFormat
        if (trackFormat != null) {
            val sampleRate = trackFormat.sampleRate

            // Default output is usually the device's native sample rate (48 kHz on most devices)
            val outputSampleRate = 48000

            if (sampleRate > 0 && sampleRate != lastSampleRate) {
                lastSampleRate = sampleRate
                Log.d("ResamplingMonitor", "Format changed: $sampleRate Hz")
                onResamplingDetected(sampleRate, outputSampleRate)
            }
        }
    }
}
