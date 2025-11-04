package com.genaro.radiomp3.ui

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.TimeBar
import com.genaro.radiomp3.R
import com.genaro.radiomp3.data.local.AppDatabase
import com.genaro.radiomp3.playback.MusicPlayerService
import com.genaro.radiomp3.net.DeezerApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.genaro.radiomp3.ui.widgets.FeedbackBanner
import com.genaro.radiomp3.playback.audio.USBAudioAnalyzer
import com.genaro.radiomp3.data.prefs.PreferenceManager
import com.genaro.radiomp3.logging.AudioLog

class NowPlayingActivity : BaseActivity() {

    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private val controller: MediaController?
        get() = if (controllerFuture.isDone) controllerFuture.get() else null

    private lateinit var imgAlbumArt: ImageView
    private lateinit var txtTitle: TextView
    private lateinit var txtArtist: TextView
    private lateinit var txtAlbum: TextView
    private lateinit var txtTechInfo: TextView
    private lateinit var seekBar: DefaultTimeBar
    private lateinit var txtCurrentTime: TextView
    private lateinit var txtTotalTime: TextView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnPrevious: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnShuffle: ImageButton
    private lateinit var btnRepeat: ImageButton
    private lateinit var btnProPlayer: TextView

    // Technical Details Panel and TextViews
    private lateinit var technicalDetailsPanel: android.view.View
    private lateinit var txtFileSize: TextView
    private lateinit var txtCompression: TextView
    private lateinit var txtFormat: TextView
    private lateinit var txtBitrate: TextView
    private lateinit var txtVbr: TextView
    private lateinit var txtSampleRate: TextView
    private lateinit var txtBitDepth: TextView
    private lateinit var txtChannels: TextView
    private lateinit var txtDuration: TextView
    private lateinit var txtReplayGain: TextView
    private lateinit var txtEncoder: TextView

    // Audio Analysis
    private var usbAnalyzer: USBAudioAnalyzer? = null
    private var preferenceManager: PreferenceManager? = null

    // Device Information and Playback Status TextViews (nullable - landscape layout may not have these)
    private var txtUSBDevice: TextView? = null
    private var txtUSBMaxHz: TextView? = null
    private var txtBitPerfect: TextView? = null
    private var txtResamplingStatus: TextView? = null
    private var txtBufferingStatus: TextView? = null

    // Double-press detection for previous button
    private var lastPreviousPressTime = 0L
    private val doublePressInterval = 500L // 500ms window for double-press

    // Pro panel state
    private var isProPanelOpen = false
    private var currentTrack: com.genaro.radiomp3.data.local.Track? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_now_playing)

        // Setup immersive mode with double-tap
        val tapArea = findViewById<View>(R.id.tapArea)
        setupImmersiveMode(tapArea)

        initViews()
        initializeController()
    }

    private fun initViews() {
        imgAlbumArt = findViewById(R.id.imgAlbumArt)
        txtTitle = findViewById(R.id.txtTitle)
        txtArtist = findViewById(R.id.txtArtist)
        txtAlbum = findViewById(R.id.txtAlbum)
        txtTechInfo = findViewById(R.id.txtTechInfo)
        seekBar = findViewById(R.id.seekBar)
        txtCurrentTime = findViewById(R.id.txtCurrentTime)
        txtTotalTime = findViewById(R.id.txtTotalTime)

        // Technical Details Panel Views
        technicalDetailsPanel = findViewById(R.id.technicalDetailsPanel)
        txtFileSize = findViewById(R.id.txtFileSize)
        txtCompression = findViewById(R.id.txtCompression)
        txtFormat = findViewById(R.id.txtFormat)
        txtBitrate = findViewById(R.id.txtBitrate)
        txtVbr = findViewById(R.id.txtVbr)
        txtSampleRate = findViewById(R.id.txtSampleRate)
        txtBitDepth = findViewById(R.id.txtBitDepth)
        txtChannels = findViewById(R.id.txtChannels)
        txtDuration = findViewById(R.id.txtDuration)
        txtReplayGain = findViewById(R.id.txtReplayGain)
        txtEncoder = findViewById(R.id.txtEncoder)

        // Initialize PreferenceManager with try-catch for safety
        try {
            preferenceManager = PreferenceManager.getInstance(this)
        } catch (e: Exception) {
            android.util.Log.e("NowPlayingActivity", "Error initializing PreferenceManager", e)
        }

        // Device Information and Playback Status TextViews (MUST initialize before checkUSBDevices())
        // Use try-catch since landscape layout may not have all these views
        try {
            txtUSBDevice = findViewById(R.id.txtUSBDevice)
            txtUSBMaxHz = findViewById(R.id.txtUSBMaxHz)
            txtBitPerfect = findViewById(R.id.txtBitPerfect)
            txtResamplingStatus = findViewById(R.id.txtResamplingStatus)
            txtBufferingStatus = findViewById(R.id.txtBufferingStatus)

            // Initialize USB Analyzer with try-catch (after USB views are initialized)
            usbAnalyzer = USBAudioAnalyzer(this)
            // Check for USB devices
            checkUSBDevices()
        } catch (e: Exception) {
            android.util.Log.e("NowPlayingActivity", "Error initializing USB device views or analyzer", e)
        }

        // PRO button listener
        btnProPlayer = findViewById(R.id.btnProPlayer)
        btnProPlayer.setOnClickListener {
            toggleTechnicalDetailsPanel()
        }

        // Enable seeking by dragging the seekbar
        seekBar.addListener(object : TimeBar.OnScrubListener {
            override fun onScrubStart(timeBar: TimeBar, position: Long) {
                // User started dragging
            }

            override fun onScrubMove(timeBar: TimeBar, position: Long) {
                // Update time display while dragging
                txtCurrentTime.text = formatTime(position)
            }

            override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
                // Apply seek when user releases
                if (!canceled) {
                    controller?.seekTo(position)
                }
            }
        })

        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnPrevious = findViewById(R.id.btnPrevious)
        btnNext = findViewById(R.id.btnNext)
        btnShuffle = findViewById(R.id.btnShuffle)
        btnRepeat = findViewById(R.id.btnRepeat)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        btnPlayPause.setOnClickListener {
            controller?.let {
                if (it.isPlaying) it.pause() else it.play()
            }
        }

        // Previous button with double-press detection
        btnPrevious.setOnClickListener {
            controller?.let { ctrl ->
                val currentTime = System.currentTimeMillis()
                val timeSinceLastPress = currentTime - lastPreviousPressTime

                if (timeSinceLastPress < doublePressInterval && lastPreviousPressTime != 0L) {
                    // Double press - go to previous track (force it even if far into song)
                    if (ctrl.hasPreviousMediaItem()) {
                        ctrl.seekToPreviousMediaItem()
                        android.util.Log.d("NowPlayingActivity", "Double press - going to previous track")
                    }
                } else {
                    // Single press - restart current track
                    ctrl.seekTo(0)
                    android.util.Log.d("NowPlayingActivity", "Single press - restarting track")
                }

                lastPreviousPressTime = currentTime
            }
        }

        // Next button
        btnNext.setOnClickListener {
            controller?.let { ctrl ->
                if (ctrl.hasNextMediaItem()) {
                    ctrl.seekToNextMediaItem()
                    android.util.Log.d("NowPlayingActivity", "Skipping to next track")
                } else {
                    android.util.Log.d("NowPlayingActivity", "No next track available")
                }
            }
        }

        btnShuffle.setOnClickListener {
            controller?.let { ctrl ->
                ctrl.shuffleModeEnabled = !ctrl.shuffleModeEnabled
                updateShuffleButton(ctrl.shuffleModeEnabled)
                android.util.Log.d("NowPlayingActivity", "Shuffle: ${ctrl.shuffleModeEnabled}")
            }
        }

        btnRepeat.setOnClickListener {
            controller?.let {
                it.repeatMode = when (it.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
                updateRepeatButton(it.repeatMode)
            }
        }
    }

    private fun initializeController() {
        val sessionToken = SessionToken(this, ComponentName(this, MusicPlayerService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()

        controllerFuture.addListener({
            controller?.let { setupPlayer(it) }
        }, MoreExecutors.directExecutor())
    }

    private fun setupPlayer(player: MediaController) {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlayPauseButton(isPlaying)
            }

            override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                updateMetadata(mediaMetadata)
            }
        })


        // Initial state
        updatePlayPauseButton(player.isPlaying)
        updateShuffleButton(player.shuffleModeEnabled)
        updateRepeatButton(player.repeatMode)
        player.currentMediaItem?.mediaMetadata?.let { updateMetadata(it) }

        // Start progress update
        startProgressUpdate()
    }

    private fun updateMetadata(metadata: androidx.media3.common.MediaMetadata) {
        txtTitle.text = metadata.title ?: "Unknown"
        txtArtist.text = metadata.artist ?: "Unknown Artist"
        txtAlbum.text = metadata.albumTitle ?: "Unknown Album"

        // Load technical info from database
        controller?.currentMediaItem?.mediaId?.let { mediaId ->
            lifecycleScope.launch {
                try {
                    val trackId = mediaId.toLongOrNull()
                    if (trackId != null) {
                        val track = withContext(Dispatchers.IO) {
                            AppDatabase.getInstance(applicationContext).trackDao().getTrackById(trackId)
                        }
                        track?.let { updateTechnicalInfo(it) }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("NowPlayingActivity", "Error loading technical info", e)
                }
            }
        }
    }

    private fun updateTechnicalInfo(track: com.genaro.radiomp3.data.local.Track) {
        // Memorizza il track corrente per i dettagli PRO
        currentTrack = track

        val parts = mutableListOf<String>()
        var formatName = ""

        // Format: MP3 / FLAC
        track.mimeType?.let { mime ->
            when {
                mime.contains("mp3", ignoreCase = true) -> {
                    parts.add("MP3")
                    formatName = "MP3"
                }
                mime.contains("flac", ignoreCase = true) -> {
                    parts.add("FLAC")
                    formatName = "FLAC"
                }
                mime.contains("ogg", ignoreCase = true) -> {
                    parts.add("OGG")
                    formatName = "OGG"
                }
                mime.contains("m4a", ignoreCase = true) || mime.contains("aac", ignoreCase = true) -> {
                    parts.add("AAC")
                    formatName = "AAC"
                }
                mime.contains("wav", ignoreCase = true) -> {
                    parts.add("WAV")
                    formatName = "WAV"
                }
                else -> {
                    // Extract from URI
                    val ext = track.displayName.substringAfterLast(".", "").uppercase()
                    if (ext.isNotEmpty()) {
                        parts.add(ext)
                        formatName = ext
                    }
                }
            }
        }

        // Bitrate: 320 kbps
        track.bitrateKbps?.let { parts.add("$it kbps") }

        // Sample rate: 44.1 kHz
        track.sampleRateHz?.let {
            val khz = it / 1000.0
            parts.add("${String.format("%.1f", khz)} kHz")
        }

        // Bit depth: 16-bit
        track.bitDepth?.let { parts.add("$it-bit") }

        // Channels: Stereo / Mono
        track.channels?.let {
            parts.add(when (it) {
                1 -> "Mono"
                2 -> "Stereo"
                else -> "${it}ch"
            })
        }

        if (parts.isNotEmpty()) {
            txtTechInfo.text = parts.joinToString(" • ")
            txtTechInfo.visibility = android.view.View.VISIBLE
        } else {
            txtTechInfo.visibility = android.view.View.GONE
        }

        // Load album art or show format placeholder
        loadAlbumArt(track, formatName)
    }

    private fun loadAlbumArt(track: com.genaro.radiomp3.data.local.Track, formatName: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Step 1: Try to extract embedded album art
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(this@NowPlayingActivity, Uri.parse(track.uri))
                val artBytes = retriever.embeddedPicture
                retriever.release()

                if (artBytes != null) {
                    // Found embedded art - show it immediately
                    android.util.Log.d("NowPlayingActivity", "Found embedded art: ${artBytes.size} bytes")

                    val bitmap = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)

                    if (bitmap != null) {
                        android.util.Log.d("NowPlayingActivity", "Bitmap decoded successfully: ${bitmap.width}x${bitmap.height}")
                        withContext(Dispatchers.Main) {
                            imgAlbumArt.setImageBitmap(bitmap)
                            imgAlbumArt.setColorFilter(null)
                            android.util.Log.d("NowPlayingActivity", "Bitmap set to ImageView")
                        }
                        return@launch
                    } else {
                        android.util.Log.w("NowPlayingActivity", "Failed to decode embedded artwork - bitmap is null")
                        // Continue to try online download
                    }
                } else {
                    android.util.Log.d("NowPlayingActivity", "No embedded artwork found")
                }

                // Step 2: No embedded art - try to download from Deezer
                android.util.Log.d("NowPlayingActivity", "No embedded art, searching online for: ${track.artistName} - ${track.albumTitle}")
                val coverUrl = DeezerApi.searchAlbumCover(track.artistName, track.albumTitle)

                withContext(Dispatchers.Main) {
                    if (!coverUrl.isNullOrBlank()) {
                        // Found cover online - load with Glide
                        android.util.Log.d("NowPlayingActivity", "Loading cover from: $coverUrl")
                        try {
                            Glide.with(this@NowPlayingActivity)
                                .asBitmap()
                                .load(coverUrl)
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .into(object : com.bumptech.glide.request.target.CustomTarget<Bitmap>() {
                                    override fun onResourceReady(
                                        resource: Bitmap,
                                        transition: com.bumptech.glide.request.transition.Transition<in Bitmap>?
                                    ) {
                                        imgAlbumArt.setImageBitmap(resource)
                                        imgAlbumArt.setColorFilter(null)
                                    }

                                    override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {
                                        // Do nothing
                                    }

                                    override fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) {
                                        android.util.Log.w("NowPlayingActivity", "Failed to load cover from URL")
                                        showFormatPlaceholder(formatName)
                                    }
                                })
                        } catch (e: Exception) {
                            android.util.Log.e("NowPlayingActivity", "Glide error", e)
                            showFormatPlaceholder(formatName)
                        }
                    } else {
                        // No cover found online - show format placeholder
                        android.util.Log.d("NowPlayingActivity", "No cover found online, showing placeholder")
                        showFormatPlaceholder(formatName)
                    }
                }

            } catch (e: Exception) {
                android.util.Log.e("NowPlayingActivity", "Error loading album art", e)
                withContext(Dispatchers.Main) {
                    showFormatPlaceholder(formatName)
                }
            }
        }
    }

    private fun showFormatPlaceholder(format: String) {
        if (format.isEmpty()) {
            imgAlbumArt.setImageResource(android.R.drawable.ic_menu_gallery)
            imgAlbumArt.setColorFilter(Color.parseColor("#666666"))
            android.util.Log.d("NowPlayingActivity", "Showing default gray placeholder")
            return
        }

        // Create a bitmap with the format text
        val size = 400
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background color based on format
        val bgColor = when (format.uppercase()) {
            "MP3" -> Color.parseColor("#FF6B35")
            "FLAC" -> Color.parseColor("#004E89")
            "AAC", "M4A" -> Color.parseColor("#8338EC")
            "OGG" -> Color.parseColor("#06A77D")
            "WAV" -> Color.parseColor("#D62828")
            else -> Color.parseColor("#555555")
        }
        canvas.drawColor(bgColor)

        // Draw format text
        val paint = Paint().apply {
            color = Color.WHITE
            textSize = 80f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
        }

        val xPos = size / 2f
        val yPos = (size / 2f) - ((paint.descent() + paint.ascent()) / 2f)
        canvas.drawText(format, xPos, yPos, paint)

        imgAlbumArt.setImageDrawable(BitmapDrawable(resources, bitmap))
        imgAlbumArt.setColorFilter(null)
        android.util.Log.d("NowPlayingActivity", "Showing format placeholder: $format")
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        btnPlayPause.setImageResource(
            if (isPlaying) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )
    }

    private fun updateShuffleButton(enabled: Boolean) {
        btnShuffle.setColorFilter(
            if (enabled) android.graphics.Color.parseColor("#03DAC5")
            else android.graphics.Color.parseColor("#888888")
        )
    }

    private fun updateRepeatButton(mode: Int) {
        val color = when (mode) {
            Player.REPEAT_MODE_OFF -> android.graphics.Color.parseColor("#888888")
            Player.REPEAT_MODE_ALL -> android.graphics.Color.parseColor("#1DB954")
            Player.REPEAT_MODE_ONE -> android.graphics.Color.parseColor("#03DAC5")
            else -> android.graphics.Color.parseColor("#888888")
        }
        btnRepeat.setColorFilter(color)
    }

    private fun startProgressUpdate() {
        lifecycleScope.launch {
            while (true) {
                controller?.let { player ->
                    val position = player.currentPosition
                    val duration = player.duration

                    if (duration > 0) {
                        txtCurrentTime.text = formatTime(position)
                        txtTotalTime.text = formatTime(duration)
                        seekBar.setPosition(position)
                        seekBar.setDuration(duration)
                    }
                }
                delay(200)
            }
        }
    }

    private fun formatTime(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours = (millis / (1000 * 60 * 60))

        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        MediaController.releaseFuture(controllerFuture)
    }

    private fun toggleTechnicalDetailsPanel() {
        isProPanelOpen = !isProPanelOpen
        if (isProPanelOpen) {
            technicalDetailsPanel.visibility = android.view.View.VISIBLE
            currentTrack?.let { updateTechnicalDetailsPanel(it) }
        } else {
            technicalDetailsPanel.visibility = android.view.View.GONE
        }
    }

    private fun updateTechnicalDetailsPanel(track: com.genaro.radiomp3.data.local.Track) {
        // 📁 FILE INFORMATION
        track.sizeBytes?.let { size ->
            val sizeMB = size / (1024 * 1024).toFloat()
            txtFileSize.text = "File Size: ${String.format("%.2f", sizeMB)} MB"
        } ?: run {
            txtFileSize.text = "File Size: —"
        }

        val compression = when {
            track.mimeType?.contains("flac", ignoreCase = true) == true -> "Lossless (FLAC)"
            track.mimeType?.contains("wav", ignoreCase = true) == true -> "Lossless (WAV)"
            track.mimeType?.contains("alac", ignoreCase = true) == true -> "Lossless (ALAC)"
            track.mimeType?.contains("mp3", ignoreCase = true) == true -> "Lossy (MP3)"
            track.mimeType?.contains("aac", ignoreCase = true) == true -> "Lossy (AAC)"
            track.mimeType?.contains("m4a", ignoreCase = true) == true -> "Lossy (AAC)"
            track.mimeType?.contains("ogg", ignoreCase = true) == true -> "Lossy (OGG)"
            else -> "Unknown"
        }
        txtCompression.text = "Compression: $compression"

        // 🎵 AUDIO FORMAT
        val formatName = when {
            track.mimeType?.contains("mp3", ignoreCase = true) == true -> "MP3"
            track.mimeType?.contains("flac", ignoreCase = true) == true -> "FLAC"
            track.mimeType?.contains("wav", ignoreCase = true) == true -> "WAV"
            track.mimeType?.contains("aac", ignoreCase = true) == true -> "AAC"
            track.mimeType?.contains("m4a", ignoreCase = true) == true -> "M4A"
            track.mimeType?.contains("alac", ignoreCase = true) == true -> "ALAC"
            track.mimeType?.contains("ogg", ignoreCase = true) == true -> "OGG Vorbis"
            else -> track.displayName.substringAfterLast(".", "").uppercase()
        }
        txtFormat.text = "Format: $formatName"

        track.bitrateKbps?.let {
            txtBitrate.text = "Bitrate: $it kbps"
        } ?: run {
            txtBitrate.text = "Bitrate: —"
        }

        // VBR/CBR detection (semplice euristica per MP3)
        val vbrInfo = when {
            track.mimeType?.contains("mp3", ignoreCase = true) == true && track.bitrateKbps == null -> "VBR (Variable)"
            track.mimeType?.contains("mp3", ignoreCase = true) == true -> "CBR (Constant)"
            else -> "—"
        }
        txtVbr.text = "VBR/CBR: $vbrInfo"

        track.sampleRateHz?.let {
            val khz = it / 1000.0
            txtSampleRate.text = "Sample Rate: ${String.format("%.1f", khz)} kHz"
        } ?: run {
            txtSampleRate.text = "Sample Rate: —"
        }

        track.bitDepth?.let {
            txtBitDepth.text = "Bit Depth: $it-bit"
        } ?: run {
            txtBitDepth.text = "Bit Depth: —"
        }

        track.channels?.let {
            val channelName = when (it) {
                1 -> "Mono"
                2 -> "Stereo"
                else -> "${it}ch"
            }
            txtChannels.text = "Channels: $channelName"
        } ?: run {
            txtChannels.text = "Channels: —"
        }

        // 🎚️ METADATA
        track.durationMs?.let {
            txtDuration.text = "Duration: ${formatTime(it)}"
        } ?: run {
            txtDuration.text = "Duration: —"
        }

        // ReplayGain e Encoder (placeholders)
        txtReplayGain.text = "ReplayGain: —"
        txtEncoder.text = "Encoder: —"

        // Update resampling and USB info
        updateResamplingStatus(track.sampleRateHz ?: 44100, track.sampleRateHz ?: 44100)
        checkUSBDevices()
    }

    private fun checkUSBDevices() {
        // Guard: only proceed if usbAnalyzer and USB views are available
        if (usbAnalyzer == null || txtUSBDevice == null || txtUSBMaxHz == null) {
            txtUSBDevice?.text = "USB Audio: —"
            txtUSBMaxHz?.text = "Max: —"
            return
        }

        try {
            val devices = usbAnalyzer?.getConnectedUSBDevices() ?: emptyList()
            if (devices.isNotEmpty()) {
                val device = devices.first()
                AudioLog.usbDeviceDetected(device.name, device.maxSampleRate)

                txtUSBDevice?.text = "USB Audio: ${device.name}"
                txtUSBMaxHz?.text = "Max: ${device.maxSampleRate} kHz"
            } else {
                txtUSBDevice?.text = "USB Audio: —"
                txtUSBMaxHz?.text = "Max: —"
            }
        } catch (e: Exception) {
            android.util.Log.e("NowPlayingActivity", "Error checking USB devices", e)
            txtUSBDevice?.text = "USB Audio: —"
            txtUSBMaxHz?.text = "Max: —"
        }
    }

    private fun updateResamplingStatus(inputHz: Int, outputHz: Int) {
        val isBitPerfect = inputHz == outputHz
        txtBitPerfect?.text = "Bit-Perfect: ${if (isBitPerfect) "✅ YES" else "❌ NO"}"
        txtResamplingStatus?.text = if (isBitPerfect) {
            "✅ No resampling ($inputHz kHz)"
        } else {
            "⚠️ Resampling: $inputHz → $outputHz kHz"
        }
    }
}
