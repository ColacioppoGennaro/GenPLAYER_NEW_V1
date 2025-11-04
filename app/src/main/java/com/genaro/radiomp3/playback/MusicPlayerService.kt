package com.genaro.radiomp3.playback

import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.Visualizer
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.genaro.radiomp3.ui.MainActivity
import com.genaro.radiomp3.ui.vu.VuLevels
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlin.math.log10
import kotlin.math.sqrt

class MusicPlayerService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private var visualizer: Visualizer? = null

    companion object {
        const val CUSTOM_COMMAND_TOGGLE_SHUFFLE = "TOGGLE_SHUFFLE"
        const val CUSTOM_COMMAND_TOGGLE_REPEAT = "TOGGLE_REPEAT"

        // VU Meter callback
        var vuMeterCallback: ((VuLevels) -> Unit)? = null

        // Reference to service instance for re-initializing visualizer
        private var instance: MusicPlayerService? = null

        fun reinitializeVisualizer() {
            if (instance == null) {
                Log.w("VU_DEBUG", "MusicPlayerService: Cannot reinitialize - service instance is NULL (not created yet)")
                return
            }
            instance?.let {
                Log.d("VU_DEBUG", "MusicPlayerService: Reinitializing visualizer after permission granted")
                it.visualizer?.release()
                it.setupVisualizer()
            }
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        instance = this

        // Create ExoPlayer
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        // Create MediaSession
        val sessionActivityPendingIntent = Intent(this, MainActivity::class.java).let { intent ->
            PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        // Setup audio visualizer for VU meter
        setupVisualizer()

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPendingIntent)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(SessionCommand(CUSTOM_COMMAND_TOGGLE_SHUFFLE, Bundle.EMPTY))
                        .add(SessionCommand(CUSTOM_COMMAND_TOGGLE_REPEAT, Bundle.EMPTY))
                        .build()

                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(sessionCommands)
                        .build()
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> {
                    when (customCommand.customAction) {
                        CUSTOM_COMMAND_TOGGLE_SHUFFLE -> {
                            player.shuffleModeEnabled = !player.shuffleModeEnabled
                            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                        }
                        CUSTOM_COMMAND_TOGGLE_REPEAT -> {
                            player.repeatMode = when (player.repeatMode) {
                                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                else -> Player.REPEAT_MODE_OFF
                            }
                            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                        }
                    }
                    return super.onCustomCommand(session, controller, customCommand, args)
                }
            })
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    private fun setupVisualizer() {
        try {
            // Check permission first
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    Log.w("VU_DEBUG", "MusicPlayerService: RECORD_AUDIO permission NOT granted, skipping visualizer setup")
                    return
                }
            }

            val audioSessionId = player.audioSessionId
            Log.d("VU_DEBUG", "MusicPlayerService: Setting up visualizer with audioSessionId=$audioSessionId")

            visualizer = Visualizer(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[0] // Minimum size for performance
                Log.d("VU_DEBUG", "MusicPlayerService: Visualizer captureSize=${captureSize}")

                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer,
                        waveform: ByteArray,
                        samplingRate: Int
                    ) {
                        // Calculate RMS levels from waveform data
                        val levels = calculateVuLevels(waveform)
                        Log.d("VU_DEBUG", "MusicPlayerService: Waveform captured - L=${levels.peakL} R=${levels.peakR}")

                        if (vuMeterCallback != null) {
                            vuMeterCallback?.invoke(levels)
                            Log.d("VU_DEBUG", "MusicPlayerService: Callback invoked")
                        } else {
                            Log.w("VU_DEBUG", "MusicPlayerService: vuMeterCallback is NULL!")
                        }
                    }

                    override fun onFftDataCapture(
                        visualizer: Visualizer,
                        fft: ByteArray,
                        samplingRate: Int
                    ) {
                        // Not used
                    }
                }, Visualizer.getMaxCaptureRate() / 2, true, false)
                enabled = true
                Log.d("VU_DEBUG", "MusicPlayerService: Visualizer enabled successfully")
            }
        } catch (e: Exception) {
            Log.e("VU_DEBUG", "MusicPlayerService: Failed to setup visualizer", e)
        }
    }

    private fun calculateVuLevels(waveform: ByteArray): VuLevels {
        // Split waveform into L/R channels (assuming stereo)
        var sumL = 0.0
        var sumR = 0.0
        var countL = 0
        var countR = 0

        for (i in waveform.indices step 2) {
            val sampleL = (waveform[i].toInt() - 128) / 128.0
            sumL += sampleL * sampleL
            countL++

            if (i + 1 < waveform.size) {
                val sampleR = (waveform[i + 1].toInt() - 128) / 128.0
                sumR += sampleR * sampleR
                countR++
            }
        }

        val rmsL = if (countL > 0) sqrt(sumL / countL) else 0.0
        val rmsR = if (countR > 0) sqrt(sumR / countR) else 0.0

        // Convert to dBFS then map to VU meter range (-20 to +3 dB)
        val dbFsL = if (rmsL > 0.0) (20.0 * log10(rmsL)).toFloat() else -60f
        val dbFsR = if (rmsR > 0.0) (20.0 * log10(rmsR)).toFloat() else -60f

        // Map dBFS (-60 to 0) to VU range (-20 to +3)
        // Add 40dB offset so that -20dBFS = -20dB VU and 0dBFS = +20dB VU
        // Then apply sensitivity adjustment
        val vuL = (dbFsL + 40f).coerceIn(-20f, 3f)
        val vuR = (dbFsR + 40f).coerceIn(-20f, 3f)

        return VuLevels(
            peakL = vuL,
            peakR = vuR,
            rmsL = vuL,
            rmsR = vuR
        )
    }

    override fun onDestroy() {
        instance = null
        visualizer?.release()
        visualizer = null
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
