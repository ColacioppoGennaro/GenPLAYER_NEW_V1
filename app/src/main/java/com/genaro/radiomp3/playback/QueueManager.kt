package com.genaro.radiomp3.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import com.genaro.radiomp3.data.local.AppDatabase
import com.genaro.radiomp3.data.local.QueueItem
import com.genaro.radiomp3.data.local.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class QueueManager(private val context: Context) {

    private val database = AppDatabase.getInstance(context)

    suspend fun playTrack(track: Track) = withContext(Dispatchers.IO) {
        // Clear current queue
        database.queueItemDao().clearAll()

        // Add single track to queue
        database.queueItemDao().insert(
            QueueItem(
                trackId = track.id,
                position = 0,
                addedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun playTrackList(tracks: List<Track>, startIndex: Int = 0) = withContext(Dispatchers.IO) {
        // Clear current queue
        database.queueItemDao().clearAll()

        // Add all tracks to queue
        val now = System.currentTimeMillis()
        val queueItems = tracks.mapIndexed { index, track ->
            QueueItem(
                trackId = track.id,
                position = index,
                addedAt = now
            )
        }
        database.queueItemDao().insertAll(queueItems)
    }

    suspend fun playAlbum(albumId: Long) = withContext(Dispatchers.IO) {
        // Get all tracks from album ordered by track number
        val tracks = database.trackDao().getTracksByAlbum(albumId)

        // Clear current queue
        database.queueItemDao().clearAll()

        // Add tracks to queue
        val now = System.currentTimeMillis()
        val queueItems = tracks.mapIndexed { index, track ->
            QueueItem(
                trackId = track.id,
                position = index,
                addedAt = now
            )
        }
        database.queueItemDao().insertAll(queueItems)
    }

    suspend fun addToQueue(track: Track) = withContext(Dispatchers.IO) {
        val currentQueue = database.queueItemDao().getAll()
        val nextPosition = currentQueue.maxOfOrNull { it.position }?.plus(1) ?: 0

        database.queueItemDao().insert(
            QueueItem(
                trackId = track.id,
                position = nextPosition,
                addedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun getCurrentQueue(): List<Track> = withContext(Dispatchers.IO) {
        val queueItems = database.queueItemDao().getAll()

        // Get tracks maintaining queue order
        queueItems.mapNotNull { queueItem ->
            database.trackDao().getTrackById(queueItem.trackId)
        }
    }

    suspend fun clearQueue() = withContext(Dispatchers.IO) {
        database.queueItemDao().clearAll()
    }

    fun trackToMediaItem(track: Track): MediaItem {
        return MediaItem.Builder()
            .setUri(track.uri)
            .setMediaId(track.id.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title ?: track.displayName)
                    .setArtist(track.artistName ?: "Unknown Artist")
                    .setAlbumTitle(track.albumTitle ?: "Unknown Album")
                    .build()
            )
            .build()
    }

    suspend fun getMediaItems(): List<MediaItem> {
        val tracks = getCurrentQueue()
        return tracks.map { trackToMediaItem(it) }
    }
}
