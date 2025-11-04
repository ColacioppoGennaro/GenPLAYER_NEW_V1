package com.genaro.radiomp3.playback

import android.content.Context
import android.content.Intent
import android.os.Build

object PlayerRepo {

    var currentTitle: String? = null
        private set
    var currentArtist: String? = null
        private set
    var currentArtworkUrl: String? = null
        private set
    var currentBitrate: Int? = null
        private set
    var currentCodec: String? = null
        private set
    var isPlaying: Boolean = false
        private set

    var onStateChanged: (() -> Unit)? = null

    private fun startPlayerService(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun playUri(context: Context, uri: String, title: String) {
        currentTitle = title
        currentArtist = null
        currentArtworkUrl = null

        android.util.Log.d("PlayerRepo", "Playing: $title - URL: $uri")

        val intent = Intent(context, PlayerService::class.java).apply {
            action = PlayerService.ACTION_PLAY
            putExtra(PlayerService.EXTRA_URI, uri)
            putExtra(PlayerService.EXTRA_TITLE, title)
        }
        startPlayerService(context, intent)
    }

    fun pause(context: Context) {
        val intent = Intent(context, PlayerService::class.java).apply {
            action = PlayerService.ACTION_PAUSE
        }
        startPlayerService(context, intent)
    }

    fun resume(context: Context) {
        val intent = Intent(context, PlayerService::class.java).apply {
            action = PlayerService.ACTION_RESUME
        }
        startPlayerService(context, intent)
    }

    fun stop(context: Context) {
        val intent = Intent(context, PlayerService::class.java).apply {
            action = PlayerService.ACTION_STOP
        }
        startPlayerService(context, intent)
        reset()
    }

    internal fun updateMetadata(title: String?, artist: String?, artworkUrl: String?) {
        currentTitle = title ?: currentTitle
        currentArtist = artist
        currentArtworkUrl = artworkUrl
        onStateChanged?.invoke()
    }

    internal fun updateAudioInfo(bitrate: Int?, codec: String?) {
        currentBitrate = bitrate
        currentCodec = codec
        onStateChanged?.invoke()
    }

    internal fun updatePlayingState(playing: Boolean) {
        isPlaying = playing
        onStateChanged?.invoke()
    }

    private fun reset() {
        currentTitle = null
        currentArtist = null
        currentArtworkUrl = null
        currentBitrate = null
        currentCodec = null
        isPlaying = false
        onStateChanged?.invoke()
    }
}
