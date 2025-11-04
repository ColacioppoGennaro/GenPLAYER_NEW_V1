package com.genaro.radiomp3.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import coil.load
import com.genaro.radiomp3.R
import com.genaro.radiomp3.playback.PlayerRepo
import com.genaro.radiomp3.playback.PlayerService
import com.genaro.radiomp3.utils.FaviconHelper

class RadioPlayerActivity : BaseActivity() {

    private lateinit var imgStationLogo: ImageView
    private lateinit var imgArtwork: ImageView
    private lateinit var coverController: CoverController
    private lateinit var txtStationName: TextView
    private lateinit var txtMetadata: TextView
    private lateinit var txtAudioInfo: TextView
    private lateinit var btnPlay: ImageButton
    private lateinit var btnPause: ImageButton
    private lateinit var btnHome: ImageButton

    // PRO button and technical details panel
    private lateinit var btnProPlayer: TextView
    private lateinit var technicalDetailsPanel: View
    private lateinit var txtPlayerCodec: TextView
    private lateinit var txtPlayerBitrate: TextView
    private lateinit var txtPlayerSampleRate: TextView
    private lateinit var txtPlayerChannels: TextView
    private lateinit var txtPlayerServer: TextView
    private lateinit var txtPlayerProtocol: TextView
    private lateinit var txtPlayerBuffer: TextView
    private lateinit var txtPlayerState: TextView
    private lateinit var txtPlayerTTFA: TextView

    private var stationId: String? = null
    private var stationName: String? = null
    private var stationUrl: String? = null
    private var stationFavicon: String? = null
    private var stationHomepage: String? = null

    private var isPanelVisible = false

    private val transparentDrawable = ColorDrawable(Color.TRANSPARENT)

    private val metadataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == PlayerService.METADATA_UPDATED) {
                val title = intent.getStringExtra(PlayerService.EXTRA_TITLE)
                val artist = intent.getStringExtra(PlayerService.EXTRA_ARTIST)
                val artworkUrl = intent.getStringExtra(PlayerService.EXTRA_ARTWORK)
                android.util.Log.d("RadioPlayerActivity", "📻 Metadata broadcast received -> Title: '$title', Artist: '$artist', Artwork: '$artworkUrl'")
                updateUi(title, artist, artworkUrl)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_radio_player)

        // Setup immersive mode with double-tap
        val tapArea = findViewById<View>(R.id.tapArea)
        setupImmersiveMode(tapArea)

        // Find views
        imgStationLogo = findViewById(R.id.imgStationLogo)
        imgArtwork = findViewById(R.id.imgArtwork)

        txtStationName = findViewById(R.id.txtStationName)
        txtMetadata = findViewById(R.id.txtMetadata)
        txtAudioInfo = findViewById(R.id.txtAudioInfo)
        btnPlay = findViewById(R.id.btnPlay)
        btnPause = findViewById(R.id.btnPause)
        btnHome = findViewById(R.id.btnHome)

        // Initialize PRO button and technical details panel
        btnProPlayer = findViewById(R.id.btnProPlayer)
        technicalDetailsPanel = findViewById(R.id.technicalDetailsPanel)
        txtPlayerCodec = findViewById(R.id.txtPlayerCodec)
        txtPlayerBitrate = findViewById(R.id.txtPlayerBitrate)
        txtPlayerSampleRate = findViewById(R.id.txtPlayerSampleRate)
        txtPlayerChannels = findViewById(R.id.txtPlayerChannels)
        txtPlayerServer = findViewById(R.id.txtPlayerServer)
        txtPlayerProtocol = findViewById(R.id.txtPlayerProtocol)
        txtPlayerBuffer = findViewById(R.id.txtPlayerBuffer)
        txtPlayerState = findViewById(R.id.txtPlayerState)
        txtPlayerTTFA = findViewById(R.id.txtPlayerTTFA)

        // Initialize CoverController
        coverController = CoverController(this, imgArtwork, crossfadeMs = 650)

        // Get data from Intent
        stationId = intent.getStringExtra("station_id")
        stationName = intent.getStringExtra("station_name")
        stationUrl = intent.getStringExtra("station_url")
        stationFavicon = intent.getStringExtra("station_favicon")
        stationHomepage = intent.getStringExtra("station_homepage")

        // Set initial UI state
        txtStationName.text = stationName ?: "Unknown Station"
        title = stationName ?: "Radio Player"

        // Load station logo using FaviconHelper
        val faviconUrls = FaviconHelper.getFaviconUrls(
            apiIconUrl = stationFavicon,
            homepage = stationHomepage,
            streamUrl = stationUrl
        )
        loadImageWithFallback(imgStationLogo, faviconUrls, 0)

        // Set station logo for CoverController fallback (sarà usato se nessuna cover arriva)
        coverController.setStationLogo(faviconUrls.firstOrNull())

        // SOLO all'avvio: mostra logo o fallback (UNA VOLTA)
        coverController.updateCover(null)

        // Show audio info (bitrate, codec) for audiophiles
        displayAudioQuality()

        // Set up button listeners
        btnPlay.setOnClickListener { playStream() }
        btnPause.setOnClickListener { PlayerRepo.pause(this) }
        btnHome.setOnClickListener { finish() }

        // PRO button toggles technical details panel
        btnProPlayer.setOnClickListener { toggleTechnicalDetailsPanel() }

        // Start playing immediately
        playStream()
        observePlayerState()
    }

    private fun observePlayerState() {
        PlayerRepo.onStateChanged = {
            val isPlaying = PlayerRepo.isPlaying
            btnPlay.visibility = if (isPlaying) View.GONE else View.VISIBLE
            btnPause.visibility = if (isPlaying) View.VISIBLE else View.GONE

            // Update audio quality info when state changes
            displayAudioQuality()
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(PlayerService.METADATA_UPDATED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(metadataReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(metadataReceiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(metadataReceiver)
    }

    private fun playStream() {
        if (stationUrl != null && stationName != null) {
            PlayerRepo.playUri(this, stationUrl!!, stationName!!)
        } else {
            Toast.makeText(this, "Error: Station data missing", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun updateUi(title: String?, artist: String?, artworkUrl: String?) {
        // Update metadata text
        val metadataText = when {
            !artist.isNullOrBlank() && !title.isNullOrBlank() -> "$artist - $title"
            !title.isNullOrBlank() -> title
            else -> "Listening..."
        }
        txtMetadata.text = metadataText
        android.util.Log.d("RadioPlayerActivity", "Metadata updated: '$metadataText'")

        // Update cover using CoverController - gestisce tutto automaticamente
        android.util.Log.d("RadioPlayerActivity", "Artwork URL: '$artworkUrl'")
        coverController.updateCover(artworkUrl)

        // Update audio quality info (deve essere sempre visibile)
        displayAudioQuality()
    }

    private fun displayAudioQuality() {
        // Get audio stream info from PlayerRepo
        val bitrate = PlayerRepo.currentBitrate
        val codec = PlayerRepo.currentCodec

        if (bitrate != null || codec != null) {
            val info = buildString {
                codec?.let { append("$it") }
                bitrate?.let {
                    if (isNotEmpty()) append(" • ")
                    append("${it} kbps")
                }
                // Quality indicator
                if (bitrate != null) {
                    append(" • ")
                    append(when {
                        bitrate >= 320 -> "HQ"
                        bitrate >= 192 -> "High"
                        bitrate >= 128 -> "Med"
                        else -> "Low"
                    })
                }
            }

            if (info.isNotEmpty()) {
                txtAudioInfo.text = info
                txtAudioInfo.visibility = View.VISIBLE
                android.util.Log.d("RadioPlayerActivity", "Audio info: $info")
            }
        }
    }

    private fun loadImageWithFallback(imageView: ImageView, urls: List<String>, index: Int) {
        if (index >= urls.size) {
            // All URLs failed, show transparent
            android.util.Log.w("RadioPlayerActivity", "All logo URLs failed")
            imageView.setImageDrawable(transparentDrawable)
            return
        }

        val url = urls[index]
        android.util.Log.d("RadioPlayerActivity", "Trying logo URL #${index + 1}/${urls.size}")

        imageView.load(url) {
            placeholder(transparentDrawable)
            error(transparentDrawable)
            crossfade(250)
            listener(
                onSuccess = { _, _ ->
                    android.util.Log.d("RadioPlayerActivity", "✓ SUCCESS loading from URL #${index + 1}")
                },
                onError = { _, _ ->
                    android.util.Log.w("RadioPlayerActivity", "✗ FAILED URL #${index + 1}, trying next")
                    // Try next URL
                    loadImageWithFallback(imageView, urls, index + 1)
                }
            )
        }
    }

    private fun toggleTechnicalDetailsPanel() {
        isPanelVisible = !isPanelVisible
        technicalDetailsPanel.visibility = if (isPanelVisible) View.VISIBLE else View.GONE

        if (isPanelVisible) {
            // Update technical details when panel becomes visible
            updateTechnicalDetails()
        }
    }

    private fun updateTechnicalDetails() {
        // Get live playback data from PlayerRepo
        val bitrate = PlayerRepo.currentBitrate
        val codec = PlayerRepo.currentCodec

        // Audio Format Section
        txtPlayerCodec.text = if (codec != null) {
            "Codec: $codec (audio/${codec.lowercase()})"
        } else {
            "Codec: Unknown"
        }

        txtPlayerBitrate.text = if (bitrate != null) {
            "Bitrate: $bitrate kbps"
        } else {
            "Bitrate: Unknown"
        }

        // Sample rate and channels - if available from PlayerRepo (may not be exposed)
        txtPlayerSampleRate.text = "Sample Rate: N/A"
        txtPlayerChannels.text = "Channels: N/A"

        // Stream Info Section
        txtPlayerServer.text = if (stationUrl != null) {
            try {
                val url = java.net.URL(stationUrl)
                "Server: ${url.host}"
            } catch (e: Exception) {
                "Server: Unknown"
            }
        } else {
            "Server: Unknown"
        }

        txtPlayerProtocol.text = if (stationUrl != null) {
            val protocol = if (stationUrl!!.startsWith("https://")) "HTTPS" else "HTTP"
            "Protocol: $protocol"
        } else {
            "Protocol: Unknown"
        }

        // Performance Section - from PlayerRepo
        val isPlaying = PlayerRepo.isPlaying
        txtPlayerState.text = "State: ${if (isPlaying) "Playing" else "Paused"}"

        // Buffer percentage - may not be exposed, show placeholder
        txtPlayerBuffer.text = "Buffer: N/A"
        txtPlayerTTFA.text = "TTFA: N/A"
    }

    override fun onDestroy() {
        PlayerRepo.onStateChanged = null
        super.onDestroy()
    }
}
