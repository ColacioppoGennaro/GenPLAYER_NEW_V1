package com.genaro.radiomp3.playback

import android.content.Context
import android.media.audiofx.Visualizer
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.extractor.metadata.icy.IcyHeaders
import androidx.media3.extractor.metadata.icy.IcyInfo
import com.genaro.radiomp3.data.Prefs
import com.genaro.radiomp3.ui.vu.VuLevels
import com.genaro.radiomp3.logging.AudioLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.log10
import kotlin.math.sqrt

@OptIn(UnstableApi::class)
class PlayerHolder(private val context: Context) {

    var player: ExoPlayer? = null
        private set

    var onMetadataChanged: ((title: String?, artist: String?, artworkUrl: String?) -> Unit)? = null
    var onPlaybackStateChanged: ((isPlaying: Boolean) -> Unit)? = null

    private var lastStreamTitle: String? = null
    private val artworkProvider = ArtworkProvider()
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var visualizer: Visualizer? = null

    fun initialize() {
        if (player != null) return

        val bufferMode = Prefs.getBufferMode(context)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(bufferMode.getBufferMs(), bufferMode.getBufferMs() * 2, 1500, 2000)
            .build()

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("GenRadio/1.0 (Android)")
            .setAllowCrossProtocolRedirects(true)

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(httpDataSourceFactory)

        player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        player?.addAnalyticsListener(object : AnalyticsListener {
            override fun onDownstreamFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                mediaLoadData: MediaLoadData
            ) {
                android.util.Log.d("METADATA_STREAM", "⚡ onDownstreamFormatChanged called")
                val trackFormat = mediaLoadData.trackFormat

                // Log format detection
                trackFormat?.let { format ->
                    val codec = format.sampleMimeType?.let {
                        when {
                            it.contains("mp3", ignoreCase = true) -> "MP3"
                            it.contains("aac", ignoreCase = true) -> "AAC"
                            it.contains("opus", ignoreCase = true) -> "Opus"
                            it.contains("vorbis", ignoreCase = true) -> "Vorbis"
                            it.contains("flac", ignoreCase = true) -> "FLAC"
                            it.contains("wav", ignoreCase = true) -> "WAV"
                            else -> it
                        }
                    } ?: "Unknown"
                    val bitDepth = format.pcmEncoding.takeIf { it > 0 }?.let {
                        when (it) {
                            C.ENCODING_PCM_16BIT -> 16
                            C.ENCODING_PCM_24BIT -> 24
                            C.ENCODING_PCM_32BIT -> 32
                            else -> 16
                        }
                    } ?: 16
                    val sampleRate = format.sampleRate.takeIf { it > 0 } ?: 44100
                    val channels = format.channelCount
                    AudioLog.formatDetected(codec, bitDepth, sampleRate, channels)
                }

                if (trackFormat == null) {
                    android.util.Log.w("METADATA_STREAM", "⚠️ trackFormat is NULL")
                    return
                }

                val metadata = trackFormat.metadata
                if (metadata == null) {
                    android.util.Log.w("METADATA_STREAM", "⚠️ metadata is NULL (radio may not send ICY metadata)")
                    return
                }

                android.util.Log.d("METADATA_STREAM", "📦 Metadata entries: ${metadata.length()}")

                for (i in 0 until metadata.length()) {
                    val entry = metadata[i]
                    android.util.Log.d("METADATA_STREAM", "  Entry[$i]: ${entry.javaClass.simpleName}")

                    when (entry) {
                        is IcyInfo -> {
                            // Dynamic metadata (song title, artist) - changes during playback
                            val streamTitle = entry.title
                            android.util.Log.i("METADATA_STREAM", "🎵 ICY Info - Title: '$streamTitle'")

                            if (!streamTitle.isNullOrBlank() && streamTitle != lastStreamTitle) {
                                lastStreamTitle = streamTitle
                                val (artist, title) = parseStreamTitle(streamTitle)
                                android.util.Log.i("METADATA_STREAM", "🎤 Parsed -> Artist: '$artist', Title: '$title'")

                                // Log metadata (use "Unknown" for null values)
                                AudioLog.metadataLoaded(
                                    title ?: "Unknown",
                                    artist ?: "Unknown Artist",
                                    "Unknown Album"
                                )

                                // Immediately update UI with whatever we have
                                onMetadataChanged?.invoke(title, artist, null)

                                if (artist != null && title != null) {
                                    coroutineScope.launch {
                                        android.util.Log.d("ARTWORK_PROVIDER", "🔍 Searching artwork for: '$artist' - '$title'")
                                        val artworkUrl = artworkProvider.findArtwork(artist, title)
                                        if (artworkUrl != null) {
                                            android.util.Log.d("ARTWORK_PROVIDER", "✅ Found artwork: $artworkUrl")
                                            onMetadataChanged?.invoke(title, artist, artworkUrl)
                                        } else {
                                            android.util.Log.d("ARTWORK_PROVIDER", "❌ No artwork found")
                                        }
                                    }
                                }
                            }
                        }
                        is IcyHeaders -> {
                            // Static metadata (station info) - sent once at connection
                            android.util.Log.i("METADATA_STREAM", "📻 ICY Headers:")
                            android.util.Log.i("METADATA_STREAM", "  Name: ${entry.name}")
                            android.util.Log.i("METADATA_STREAM", "  Genre: ${entry.genre}")
                            android.util.Log.i("METADATA_STREAM", "  Bitrate: ${entry.bitrate} kbps")
                            android.util.Log.i("METADATA_STREAM", "  URL: ${entry.url}")
                            android.util.Log.i("METADATA_STREAM", "  Public: ${entry.isPublic}")
                            android.util.Log.i("METADATA_STREAM", "  MetadataInterval: ${entry.metadataInterval}")

                            // Update audio quality info for UI
                            val bitrateKbps = (entry.bitrate / 1000).takeIf { it > 0 }
                            val codecName = trackFormat.sampleMimeType?.let {
                                when {
                                    it.contains("mp3", ignoreCase = true) -> "MP3"
                                    it.contains("aac", ignoreCase = true) -> "AAC"
                                    it.contains("opus", ignoreCase = true) -> "Opus"
                                    it.contains("vorbis", ignoreCase = true) -> "Vorbis"
                                    it.contains("flac", ignoreCase = true) -> "FLAC"
                                    else -> it.substringAfter("/").uppercase()
                                }
                            }
                            PlayerRepo.updateAudioInfo(bitrateKbps, codecName)
                            android.util.Log.d("METADATA_STREAM", "Audio info updated: $codecName @ $bitrateKbps kbps")

                            if (entry.metadataInterval <= 0) {
                                android.util.Log.w("METADATA_STREAM", "⚠️ This radio does NOT send song metadata (metadataInterval = ${entry.metadataInterval})")
                            }
                        }
                    }
                }
            }
        })

        // Setup visualizer for VU meter
        setupVisualizer()

        player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val playing = playbackState == Player.STATE_READY && player?.playWhenReady == true
                onPlaybackStateChanged?.invoke(playing)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                onPlaybackStateChanged?.invoke(isPlaying)
            }

            override fun onMetadata(metadata: androidx.media3.common.Metadata) {
                android.util.Log.d("METADATA_LISTENER", "🎵 onMetadata called with ${metadata.length()} entries")

                for (i in 0 until metadata.length()) {
                    val entry = metadata[i]
                    android.util.Log.d("METADATA_LISTENER", "  Entry[$i]: ${entry.javaClass.simpleName}")

                    if (entry is IcyInfo) {
                        val streamTitle = entry.title
                        android.util.Log.i("METADATA_LISTENER", "🎵 ICY Info - Title: '$streamTitle'")

                        if (!streamTitle.isNullOrBlank() && streamTitle != lastStreamTitle) {
                            lastStreamTitle = streamTitle
                            val (artist, title) = parseStreamTitle(streamTitle)
                            android.util.Log.i("METADATA_LISTENER", "🎤 Parsed -> Artist: '$artist', Title: '$title'")

                            // Immediately update UI
                            onMetadataChanged?.invoke(title, artist, null)

                            if (artist != null && title != null) {
                                coroutineScope.launch {
                                    android.util.Log.d("ARTWORK_PROVIDER", "🔍 Searching artwork for: '$artist' - '$title'")
                                    val artworkUrl = artworkProvider.findArtwork(artist, title)
                                    if (artworkUrl != null) {
                                        android.util.Log.d("ARTWORK_PROVIDER", "✅ Found artwork: $artworkUrl")
                                        onMetadataChanged?.invoke(title, artist, artworkUrl)
                                    } else {
                                        android.util.Log.d("ARTWORK_PROVIDER", "❌ No artwork found")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e("PlayerHolder", "Playback error: ${error.message}", error)
            }
        })
    }

    private fun parseStreamTitle(streamTitle: String): Pair<String?, String?> {
        // Virgin Radio format: "Title~Artist~Year~Duration~..."
        if (streamTitle.contains("~")) {
            val parts = streamTitle.split("~")
            if (parts.size >= 2) {
                val title = parts[0].trim()
                val artist = parts[1].trim()
                android.util.Log.d("PARSER", "Virgin Radio format detected: Title='$title', Artist='$artist'")
                return Pair(artist, title)
            }
        }

        // Standard format: "Artist - Title"
        val parts = streamTitle.split(" - ", limit = 2)
        return if (parts.size > 1) {
            val artist = parts[0].trim()
            val title = parts[1].trim()
            android.util.Log.d("PARSER", "Standard format: Artist='$artist', Title='$title'")
            Pair(artist, title)
        } else {
            android.util.Log.d("PARSER", "Unknown format, using as title only: '$streamTitle'")
            Pair(null, streamTitle.trim())
        }
    }

    private fun inferMimeType(url: String): String = when {
        url.endsWith(".m3u8", ignoreCase = true) -> MimeTypes.APPLICATION_M3U8
        url.endsWith(".mp3", ignoreCase = true)  -> MimeTypes.AUDIO_MPEG
        url.endsWith(".aac", ignoreCase = true)  -> MimeTypes.AUDIO_AAC
        url.endsWith(".flac", ignoreCase = true) -> MimeTypes.AUDIO_FLAC
        else -> MimeTypes.AUDIO_UNKNOWN
    }

    fun play(uri: String, title: String) {
        // Check if the same URI is already loaded and playing
        val currentMediaItem = player?.currentMediaItem
        val currentUri = currentMediaItem?.localConfiguration?.uri?.toString()
        val isPlaying = player?.isPlaying == true

        if (currentUri == uri && isPlaying) {
            android.util.Log.d("PlayerHolder", "✅ Same URI already playing - no restart needed: $uri")
            return
        }

        lastStreamTitle = null
        android.util.Log.d("PlayerHolder", "▶️ Starting playback: $uri")
        android.util.Log.d("PlayerHolder", "▶️ Station: $title")

        val item = MediaItem.Builder()
            .setUri(uri)
            .setMimeType(inferMimeType(uri))
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
            .build()

        player?.setMediaItem(item)
        player?.prepare()
        player?.play()

        android.util.Log.d("PlayerHolder", "▶️ Player started, waiting for metadata...")
    }

    fun pause() {
        player?.pause()
    }

    fun resume() {
        player?.play()
    }

    fun stop() {
        player?.stop()
    }

    private fun setupVisualizer() {
        try {
            // Check permission first
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    Log.w("VU_DEBUG", "PlayerHolder: RECORD_AUDIO permission NOT granted, skipping visualizer setup")
                    return
                }
            }

            val audioSessionId = player?.audioSessionId ?: return
            Log.d("VU_DEBUG", "PlayerHolder: Setting up visualizer with audioSessionId=$audioSessionId")

            visualizer = Visualizer(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[0]
                Log.d("VU_DEBUG", "PlayerHolder: Visualizer captureSize=${captureSize}")

                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer,
                        waveform: ByteArray,
                        samplingRate: Int
                    ) {
                        val levels = calculateVuLevels(waveform)
                        Log.d("VU_DEBUG", "PlayerHolder: Waveform captured - L=${levels.peakL} R=${levels.peakR}")

                        if (MusicPlayerService.vuMeterCallback != null) {
                            MusicPlayerService.vuMeterCallback?.invoke(levels)
                            Log.d("VU_DEBUG", "PlayerHolder: Callback invoked")
                        } else {
                            Log.w("VU_DEBUG", "PlayerHolder: vuMeterCallback is NULL!")
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
                Log.d("VU_DEBUG", "PlayerHolder: Visualizer enabled successfully")
            }
        } catch (e: Exception) {
            Log.e("VU_DEBUG", "PlayerHolder: Failed to setup visualizer", e)
        }
    }

    private fun calculateVuLevels(waveform: ByteArray): VuLevels {
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
        val vuL = (dbFsL + 40f).coerceIn(-20f, 3f)
        val vuR = (dbFsR + 40f).coerceIn(-20f, 3f)

        return VuLevels(
            peakL = vuL,
            peakR = vuR,
            rmsL = vuL,
            rmsR = vuR
        )
    }

    fun reinitializeVisualizer() {
        Log.d("VU_DEBUG", "PlayerHolder: Reinitializing visualizer")
        visualizer?.release()
        visualizer = null
        setupVisualizer()
    }

    fun release() {
        visualizer?.release()
        visualizer = null
        player?.release()
        player = null
    }
}
