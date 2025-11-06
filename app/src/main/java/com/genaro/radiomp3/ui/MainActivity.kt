package com.genaro.radiomp3.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.genaro.radiomp3.R
import com.genaro.radiomp3.data.HomePageButton
import com.genaro.radiomp3.data.Prefs
import com.genaro.radiomp3.ui.vu.VuMeterDialogFragment
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : BaseActivity() {

    companion object {
        private const val PERMISSION_REQUEST_RECORD_AUDIO = 200
        private const val LONG_PRESS_DURATION = 2000L  // 2 secondi
        private const val CLICK_DEBOUNCE_MS = 500L  // 500ms debounce per click
    }

    private lateinit var buttonsContainer: LinearLayout
    private val buttons = mutableListOf<HomePageButton>()
    private var draggedButton: View? = null
    private var dragStartY = 0f
    private var dragStartIndex = -1  // indice di partenza quando inizi il drag
    private var isDragging = false
    private var lastHoverIndex = -1  // ultima posizione "hover" durante il drag
    private val handler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable { startDrag() }

    // Debounce for mini player clicks
    private var lastMiniPlayerClickTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Setup immersive mode with double-tap
        val tapArea = findViewById<View>(R.id.tapArea)
        setupImmersiveMode(tapArea)

        // Setup Settings button
        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Setup buttons container
        buttonsContainer = findViewById(R.id.buttonsContainer)

        // Applica padding laterale maggiore in landscape
        adjustPaddingForOrientation()

        // Load buttons
        loadAndDisplayButtons()

        // Request RECORD_AUDIO permission for VU meter
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    PERMISSION_REQUEST_RECORD_AUDIO
                )
            }
        }
    }

    private fun loadAndDisplayButtons() {
        buttons.clear()
        buttons.addAll(Prefs.getHomePageButtons(this))
        rebuildUI()
    }

    private fun rebuildUI() {
        buttonsContainer.removeAllViews()
        draggedButton = null
        dragStartIndex = -1
        isDragging = false
        lastHoverIndex = -1

        android.util.Log.d("MainActivity", "rebuildUI: Total buttons=${buttons.size}, Enabled=${buttons.filter { it.isEnabled }.size}")
        buttons.forEach { button ->
            android.util.Log.d("MainActivity", "Button: ${button.id} enabled=${button.isEnabled}")
        }

        var viewIndex = 0
        buttons.forEach { button ->
            if (button.isEnabled) {
                android.util.Log.d("MainActivity", "Creating view for: ${button.id}")
                val buttonView = createButtonView(button, viewIndex)
                buttonsContainer.addView(buttonView)
                viewIndex++
            }
        }
        android.util.Log.d("MainActivity", "rebuildUI complete: Views added=${viewIndex}")
    }

    private fun createButtonView(button: HomePageButton, index: Int): View {
        val inflater = LayoutInflater.from(this)

        // Use different layout for mini player
        val layoutId = if (button.type == HomePageButton.ButtonType.MINI_PLAYER) {
            R.layout.item_mini_player_button
        } else {
            R.layout.item_homepage_button
        }

        val buttonView = inflater.inflate(layoutId, buttonsContainer, false)

        // Salva l'indice nel tag per evitare indexOf()
        buttonView.tag = index

        // Styling con ombra colorata (shadow bar effect)
        val buttonColor = android.graphics.Color.parseColor(button.color)

        // Crea un LayerDrawable dinamico con l'ombra colorata
        val shadowShape = android.graphics.drawable.GradientDrawable().apply {
            setColor(buttonColor)
            cornerRadius = 12f
        }

        val mainShape = android.graphics.drawable.GradientDrawable().apply {
            setColor(android.graphics.Color.parseColor("#2A2A2A"))
            cornerRadius = 12f
            setStroke(1, android.graphics.Color.parseColor("#404040"))
        }

        val layerDrawable = android.graphics.drawable.LayerDrawable(arrayOf(shadowShape, mainShape)).apply {
            setLayerInset(0, 0, 0, 0, 0)    // Ombra: full width (niente sporgenza)
            setLayerInset(1, 4, 0, 0, 0)    // Principale: spostato di 4dp a destra (accorciato a sinistra)
        }
        buttonView.background = layerDrawable

        // Configure based on button type
        if (button.type == HomePageButton.ButtonType.MINI_PLAYER) {
            setupMiniPlayer(buttonView, button)
        } else {
            val textView = buttonView.findViewById<android.widget.TextView>(R.id.buttonText)
            textView.text = "${button.emoji} ${button.name}"
        }

        // Click listener
        buttonView.setOnClickListener {
            handleButtonClick(button)
        }

        // Long press per drag
        buttonView.setOnTouchListener { v, event ->
            val idx = v.tag as? Int ?: index
            handleLongPressDrag(v, event, button, idx)
        }

        return buttonView
    }

    private fun setupMiniPlayer(buttonView: View, button: HomePageButton) {
        val connectionInfo = buttonView.findViewById<android.widget.TextView>(R.id.connectionInfo)
        val qualityInfo = buttonView.findViewById<android.widget.TextView>(R.id.qualityInfo)
        val trackInfo = buttonView.findViewById<android.widget.TextView>(R.id.trackInfo)
        val extraInfo = buttonView.findViewById<android.widget.TextView>(R.id.extraInfo)

        // Enable marquee for all TextViews
        connectionInfo.isSelected = true
        qualityInfo.isSelected = true
        trackInfo.isSelected = true
        extraInfo.isSelected = true

        // Always visible if enabled (no dynamic hiding)
        buttonView.visibility = View.VISIBLE

        // Create AudioPathRepository
        val audioPathRepo = com.genaro.radiomp3.playback.audio.AudioPathRepository(this)

        // Function to update mini player display
        val updateMiniPlayer = {
            if (com.genaro.radiomp3.playback.PlayerRepo.isPlaying) {
                // Web Radio is playing - get data from PlayerRepo
                val stationName = com.genaro.radiomp3.playback.PlayerRepo.currentStationName ?: "Web Radio"
                val artist = com.genaro.radiomp3.playback.PlayerRepo.currentArtist ?: "Unknown Artist"
                val title = com.genaro.radiomp3.playback.PlayerRepo.currentTitle ?: "Unknown Title"
                val bitrate = com.genaro.radiomp3.playback.PlayerRepo.currentBitrate
                val codec = com.genaro.radiomp3.playback.PlayerRepo.currentCodec ?: "MP3"

                // Riga 3: Radio station name
                trackInfo.text = stationName

                // Riga 4: Artist ➤ Title
                extraInfo.text = "$artist ➤ $title ➤ ►"
                extraInfo.visibility = View.VISIBLE

                // Build source info for Web Radio
                val sourceInfo = audioPathRepo.createSourceInfo(
                    uri = null, // Web radio doesn't have URI
                    codec = codec,
                    sampleRateHz = null, // Not available from PlayerRepo
                    bitDepth = null,
                    channels = null,
                    bitrateKbps = bitrate
                )
                connectionInfo.text = sourceInfo.toCompactString()

                // Build sink info
                val sinkInfo = audioPathRepo.getCurrentSinkInfo()
                qualityInfo.text = sinkInfo.toCompactString()
            } else {
                // Try to get track info from MP3 MediaController
                try {
                    val sessionToken = androidx.media3.session.SessionToken(
                        this,
                        android.content.ComponentName(this, com.genaro.radiomp3.playback.MusicPlayerService::class.java)
                    )
                    val controllerFuture = androidx.media3.session.MediaController.Builder(this, sessionToken).buildAsync()
                    controllerFuture.addListener({
                        try {
                            val controller = controllerFuture.get()
                            val currentItem = controller.currentMediaItem

                            if (currentItem != null && controller.isPlaying) {
                                // We have a track playing from MP3 player
                                val artist = currentItem.mediaMetadata.artist?.toString() ?: "Unknown Artist"
                                val title = currentItem.mediaMetadata.title?.toString() ?: currentItem.mediaId

                                // Riga 3: Track Title (MP3 doesn't have station name)
                                trackInfo.text = "$artist ➤ $title ➤ ►"

                                // Riga 4: Hidden for MP3
                                extraInfo.visibility = View.GONE

                                // Extract format info from ExoPlayer
                                val format = controller.currentTracks.groups.firstOrNull()?.getTrackFormat(0)
                                val sampleRateHz = format?.sampleRate
                                val channels = format?.channelCount
                                val bitrate = format?.averageBitrate?.let { it / 1000 } // Convert to kbps
                                val codec = format?.sampleMimeType?.let {
                                    when {
                                        it.contains("flac") -> "FLAC"
                                        it.contains("mp3") -> "MP3"
                                        it.contains("aac") -> "AAC"
                                        it.contains("opus") -> "OPUS"
                                        it.contains("vorbis") -> "Vorbis"
                                        else -> "Audio"
                                    }
                                }

                                // Build source info
                                val sourceInfo = audioPathRepo.createSourceInfo(
                                    uri = currentItem.localConfiguration?.uri,
                                    codec = codec,
                                    sampleRateHz = sampleRateHz,
                                    bitDepth = null, // ExoPlayer doesn't expose bit depth directly
                                    channels = channels,
                                    bitrateKbps = bitrate
                                )
                                connectionInfo.text = sourceInfo.toCompactString()

                                // Build sink info
                                val sinkInfo = audioPathRepo.getCurrentSinkInfo()
                                qualityInfo.text = sinkInfo.toCompactString()
                            } else {
                                // No track playing - show default message
                                trackInfo.text = "No track playing"
                                connectionInfo.text = "Origine: Waiting for playback..."
                                qualityInfo.text = "Uscita: Ready"
                            }

                            androidx.media3.session.MediaController.releaseFuture(controllerFuture)
                        } catch (e: Exception) {
                            android.util.Log.e("MiniPlayer", "Error getting track info", e)
                            trackInfo.text = "No track playing"
                            connectionInfo.text = "Origine: Error"
                            qualityInfo.text = "Uscita: Error"
                        }
                    }, com.google.common.util.concurrent.MoreExecutors.directExecutor())
                } catch (e: Exception) {
                    android.util.Log.e("MiniPlayer", "Error setting up mini player", e)
                    trackInfo.text = "No track playing"
                    connectionInfo.text = "Origine: Error"
                    qualityInfo.text = "Uscita: Error"
                }
            }
        }

        // Initial update
        updateMiniPlayer()

        // Register callback to PlayerRepo for real-time updates
        com.genaro.radiomp3.playback.PlayerRepo.onStateChanged = {
            updateMiniPlayer()
        }
    }

    private fun handleButtonClick(button: HomePageButton) {
        when (button.id) {
            "mini_player" -> {
                // Debounce: prevent multiple rapid clicks
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastMiniPlayerClickTime < CLICK_DEBOUNCE_MS) {
                    android.util.Log.d("MainActivity", "Click ignored (debounce)")
                    return
                }
                lastMiniPlayerClickTime = currentTime

                // Three independent checks to determine which player is active
                // Only one should be true at a time

                // Check 1: Web Radio player
                if (com.genaro.radiomp3.playback.PlayerRepo.isPlaying) {
                    // Pass station data to RadioPlayerActivity
                    val intent = Intent(this, RadioPlayerActivity::class.java)
                    val stationId = com.genaro.radiomp3.playback.PlayerRepo.currentStationId
                    val stationName = com.genaro.radiomp3.playback.PlayerRepo.currentStationName
                    val stationUrl = com.genaro.radiomp3.playback.PlayerRepo.currentStationUrl

                    if (stationId != null && stationName != null && stationUrl != null) {
                        intent.putExtra("station_id", stationId)
                        intent.putExtra("station_name", stationName)
                        intent.putExtra("station_url", stationUrl)
                    }
                    startActivity(intent)
                }

                // Check 2: MP3 Local player
                if (!com.genaro.radiomp3.playback.PlayerRepo.isPlaying) {
                    // Try to check if MP3 player is active
                    try {
                        val sessionToken = androidx.media3.session.SessionToken(
                            this,
                            android.content.ComponentName(this, com.genaro.radiomp3.playback.MusicPlayerService::class.java)
                        )
                        val controllerFuture = androidx.media3.session.MediaController.Builder(this, sessionToken).buildAsync()
                        controllerFuture.addListener({
                            try {
                                val controller = controllerFuture.get()
                                if (controller.isPlaying && controller.currentMediaItem != null) {
                                    startActivity(Intent(this, NowPlayingActivity::class.java))
                                }
                                androidx.media3.session.MediaController.releaseFuture(controllerFuture)
                            } catch (e: Exception) {
                                android.util.Log.e("MainActivity", "Error checking MP3 player", e)
                            }
                        }, com.google.common.util.concurrent.MoreExecutors.directExecutor())
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Error accessing MP3 player controller", e)
                    }
                }

                // Check 3: NAS player (when implemented in future)
                // if (nasPlayer.isPlaying) {
                //     startActivity(Intent(this, NASPlayerActivity::class.java))
                // }
            }
            "web_radio" -> startActivity(Intent(this, RadioFavoritesActivity::class.java))
            "mp3" -> startActivity(Intent(this, LocalMusicActivity::class.java))
            "youtube" -> openExternalApp("com.google.android.youtube", "https://www.youtube.com")
            "spotify" -> openExternalApp("com.spotify.music", "https://open.spotify.com")
            "vu_meter" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        com.genaro.radiomp3.playback.MusicPlayerService.reinitializeVisualizer()
                        com.genaro.radiomp3.playback.PlayerService.reinitializeVisualizer()
                        VuMeterDialogFragment.show(supportFragmentManager)
                    } else {
                        Toast.makeText(this, "VU Meter needs audio permission", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    VuMeterDialogFragment.show(supportFragmentManager)
                }
            }
            else -> {
                // Custom button - apri link
                button.link?.let { link ->
                    try {
                        if (link.contains(".") || link.startsWith("http")) {
                            // Sembra un'URL
                            val uri = if (link.startsWith("http")) {
                                Uri.parse(link)
                            } else {
                                Uri.parse("https://$link")
                            }
                            val browserIntent = Intent(Intent.ACTION_VIEW, uri)
                            startActivity(browserIntent)
                        } else {
                            // Package name
                            openExternalApp(link, "")
                        }
                    } catch (e: Exception) {
                        Toast.makeText(
                            this,
                            "Link Error: Cannot open ${button.name}",
                            Toast.LENGTH_LONG
                        ).show()
                        android.util.Log.e("MainActivity", "Error opening link: $link", e)
                    }
                }
            }
        }
    }

    private fun handleLongPressDrag(view: View, event: MotionEvent, button: HomePageButton, index: Int): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragStartY = event.rawY
                dragStartIndex = index
                draggedButton = view
                isDragging = false
                lastHoverIndex = index

                // Avvia long press timer
                handler.postDelayed(longPressRunnable, LONG_PRESS_DURATION)

                // Consuma l'evento per avere il controllo completo
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val currentY = event.rawY
                val deltaY = currentY - dragStartY

                // Se si è mosso troppo prima del long press, annulla
                if (!isDragging && abs(deltaY) > 50) {
                    handler.removeCallbacks(longPressRunnable)
                    // Permetti allo ScrollView di gestire lo scroll
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }

                // Se siamo in modalità drag
                if (isDragging && draggedButton != null) {
                    // IMPORTANTE: Disabilita lo scroll del parent durante il drag
                    view.parent?.requestDisallowInterceptTouchEvent(true)

                    // Muovi visivamente il pulsante trascinato
                    draggedButton?.translationY = deltaY

                    // Calcola dove dovrebbe essere posizionato
                    val buttonHeight = draggedButton!!.height.toFloat() + 16f
                    val jumps = (deltaY / buttonHeight).roundToInt()
                    val hoverIndex = (dragStartIndex + jumps).coerceIn(0, buttonsContainer.childCount - 1)

                    // Se la posizione hover è cambiata, anima gli altri pulsanti
                    if (hoverIndex != lastHoverIndex) {
                        animateOtherButtonsForHover(hoverIndex)
                        lastHoverIndex = hoverIndex
                    }

                    return true
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)

                // Riabilita lo scroll del parent
                view.parent?.requestDisallowInterceptTouchEvent(false)

                if (isDragging && draggedButton != null) {
                    // Calcola la posizione finale
                    val deltaY = draggedButton!!.translationY
                    val buttonHeight = draggedButton!!.height.toFloat() + 16f
                    val jumps = (deltaY / buttonHeight).roundToInt()
                    val finalIndex = (dragStartIndex + jumps).coerceIn(0, buttons.size - 1)

                    // Reset animazioni degli altri pulsanti
                    resetOtherButtonsAnimations()

                    // Se la posizione è cambiata, fai la move finale
                    if (finalIndex != dragStartIndex) {
                        // Move nella lista
                        val element = buttons.removeAt(dragStartIndex)
                        buttons.add(finalIndex, element)

                        // Aggiorna ordini e salva
                        val updatedButtons = buttons.mapIndexed { idx, btn ->
                            btn.copy(order = idx)
                        }
                        Prefs.setHomePageButtons(this, updatedButtons)

                        Toast.makeText(this, "Button order updated", Toast.LENGTH_SHORT).show()
                    }

                    // Ricostruisci UI
                    isDragging = false
                    draggedButton = null
                    dragStartIndex = -1
                    lastHoverIndex = -1
                    rebuildUI()

                    return true
                } else {
                    // Era un click normale
                    val distance = abs(event.rawY - dragStartY)
                    if (distance < 50) {
                        handleButtonClick(button)
                    }

                    // Resetta stato
                    draggedButton = null
                    dragStartIndex = -1
                    lastHoverIndex = -1
                    return true
                }
            }
        }
        return true
    }

    private fun animateOtherButtonsForHover(hoverIndex: Int) {
        // Anima gli altri pulsanti per fare spazio nella posizione hover
        val draggedIndex = dragStartIndex
        val buttonHeight = draggedButton?.height?.toFloat() ?: 0f

        for (i in 0 until buttonsContainer.childCount) {
            val childView = buttonsContainer.getChildAt(i)
            if (childView != draggedButton) {
                val targetTranslationY = when {
                    // Se sono tra la posizione originale e quella hover, mi sposto
                    draggedIndex < hoverIndex && i > draggedIndex && i <= hoverIndex -> -buttonHeight
                    draggedIndex > hoverIndex && i < draggedIndex && i >= hoverIndex -> buttonHeight
                    else -> 0f
                }

                childView.animate()
                    .translationY(targetTranslationY)
                    .setDuration(200)
                    .start()
            }
        }
    }

    private fun resetOtherButtonsAnimations() {
        // Reset tutte le animazioni degli altri pulsanti
        for (i in 0 until buttonsContainer.childCount) {
            val childView = buttonsContainer.getChildAt(i)
            if (childView != draggedButton) {
                childView.animate()
                    .translationY(0f)
                    .setDuration(200)
                    .start()
            }
        }
    }

    private fun startDrag() {
        isDragging = true
        // Disabilita lo scroll del parent ScrollView durante il drag
        draggedButton?.parent?.requestDisallowInterceptTouchEvent(true)

        draggedButton?.apply {
            elevation = 10f
            scaleX = 0.95f
            scaleY = 0.95f
            alpha = 0.7f
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                com.genaro.radiomp3.playback.MusicPlayerService.reinitializeVisualizer()
                com.genaro.radiomp3.playback.PlayerService.reinitializeVisualizer()
                Toast.makeText(this, "VU Meter enabled", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openExternalApp(packageName: String, webUrl: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                startActivity(intent)
            } else if (webUrl.isNotEmpty()) {
                Toast.makeText(this, "App not installed, opening browser...", Toast.LENGTH_SHORT).show()
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl))
                startActivity(browserIntent)
            } else {
                Toast.makeText(this, "App not installed", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Error: Cannot open this app or link",
                Toast.LENGTH_LONG
            ).show()
            android.util.Log.e("MainActivity", "Error opening app: $packageName", e)
        }
    }

    private fun adjustPaddingForOrientation() {
        val orientation = resources.configuration.orientation
        val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE

        val verticalPadding = (32 * resources.displayMetrics.density).toInt()
        val horizontalPadding = if (isLandscape) {
            // In landscape: padding maggiore (25% dello schermo)
            (resources.displayMetrics.widthPixels * 0.25).toInt()
        } else {
            // In portrait: padding normale
            verticalPadding
        }

        buttonsContainer.setPadding(
            horizontalPadding,
            verticalPadding,
            horizontalPadding,
            verticalPadding
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        adjustPaddingForOrientation()
    }

    override fun onResume() {
        super.onResume()
        // Ricarica bottoni quando torna dalla settings
        loadAndDisplayButtons()
    }
}
