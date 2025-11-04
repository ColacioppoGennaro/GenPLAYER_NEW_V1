package com.genaro.radiomp3.playback

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.genaro.radiomp3.R
import com.genaro.radiomp3.ui.MainActivity

class PlayerService : Service() {

    companion object {
        const val ACTION_PLAY = "com.genaro.radiomp3.action.PLAY"
        const val ACTION_PAUSE = "com.genaro.radiomp3.action.PAUSE"
        const val ACTION_RESUME = "com.genaro.radiomp3.action.RESUME"
        const val ACTION_STOP = "com.genaro.radiomp3.action.STOP"

        const val METADATA_UPDATED = "com.genaro.radiomp3.action.METADATA_UPDATED"
        const val EXTRA_URI = "uri"
        const val EXTRA_TITLE = "title"
        const val EXTRA_ARTIST = "artist"
        const val EXTRA_ARTWORK = "artwork"

        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "player_channel"

        // Reference to service instance for re-initializing visualizer
        private var instance: PlayerService? = null

        fun reinitializeVisualizer() {
            if (instance == null) {
                android.util.Log.w("VU_DEBUG", "PlayerService: Cannot reinitialize - service instance is NULL (not created yet)")
                return
            }
            instance?.let {
                android.util.Log.d("VU_DEBUG", "PlayerService: Reinitializing PlayerHolder visualizer")
                it.playerHolder.reinitializeVisualizer()
            }
        }
    }

    private lateinit var playerHolder: PlayerHolder

    override fun onCreate() {
        super.onCreate()
        instance = this
        android.util.Log.d("PLAY_CHAIN", "PlayerService: onCreate")
        createNotificationChannel()
        playerHolder = PlayerHolder(applicationContext)
        playerHolder.initialize()

        playerHolder.onMetadataChanged = { title, artist, artworkUrl ->
            PlayerRepo.updateMetadata(title, artist, artworkUrl)
            updateNotification()
            broadcastMetadata(title, artist, artworkUrl)
        }

        playerHolder.onPlaybackStateChanged = { isPlaying ->
            PlayerRepo.updatePlayingState(isPlaying)
            if (isPlaying) {
                updateNotification()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        // CRITICAL LOG: This is the entry point. Does the service receive the command?
        android.util.Log.e("PLAY_CHAIN", "PlayerService: Received command -> $action")

        when (action) {
            ACTION_PLAY -> {
                startForegroundService()
                val uri = intent.getStringExtra(EXTRA_URI)
                val title = intent.getStringExtra(EXTRA_TITLE)
                if (uri.isNullOrBlank()) {
                    android.util.Log.e("PLAY_CHAIN", "PlayerService: ACTION_PLAY with NULL or BLANK URI. Stopping.")
                    return START_NOT_STICKY
                }
                val safeTitle = title ?: "Unknown"
                playerHolder.play(uri, safeTitle)
            }
            ACTION_PAUSE -> playerHolder.pause()
            ACTION_RESUME -> playerHolder.resume()
            ACTION_STOP -> {
                playerHolder.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun broadcastMetadata(title: String?, artist: String?, artworkUrl: String?) {
        val intent = Intent(METADATA_UPDATED).apply {
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_ARTIST, artist)
            putExtra(EXTRA_ARTWORK, artworkUrl)
        }
        sendBroadcast(intent)
    }

    private fun startForegroundService() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        val notification = buildNotification()
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = PlayerRepo.currentTitle ?: "Radio & MP3"
        val artist = PlayerRepo.currentArtist ?: "Playing..."

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Media Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows currently playing media"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        playerHolder.release()
        android.util.Log.d("PLAY_CHAIN", "PlayerService: onDestroy")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
