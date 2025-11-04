package com.genaro.radiomp3.scanner

import android.content.Context
import android.net.Uri
import android.util.Log
import com.genaro.radiomp3.data.local.ArtworkCache
import com.genaro.radiomp3.data.local.Track
import com.genaro.radiomp3.data.local.dao.ArtworkDao
import com.genaro.radiomp3.data.local.dao.TrackDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class TagEnrichment(
    private val context: Context,
    private val trackDao: TrackDao,
    private val artworkDao: ArtworkDao
) {

    suspend fun enrich(tracks: List<Track>) = withContext(Dispatchers.IO) {
        val updatedTracks = mutableListOf<Track>()

        for (track in tracks) {
            val enrichedTrack = readTags(track)
            updatedTracks.add(enrichedTrack ?: track)
        }

        if (updatedTracks.isNotEmpty()) {
            trackDao.insert(updatedTracks)
        }
    }

    private suspend fun readTags(track: Track): Track? {
        val tempFile = copyUriToTempFile(Uri.parse(track.uri))
        if (tempFile == null || !tempFile.exists() || tempFile.length() == 0L) {
            tempFile?.delete()
            Log.w("TagEnrichment", "Failed to copy or created empty temp file for ${track.uri}")
            return null
        }

        try {
            val audioFile = AudioFileIO.read(tempFile)
            val tag: Tag? = audioFile.tag
            val header = audioFile.audioHeader

            var artworkHash: String? = null
            tag?.firstArtwork?.binaryData?.let { artworkBytes ->
                artworkHash = saveArtwork(artworkBytes)
            }

            return track.copy(
                title = tag?.getFirst(FieldKey.TITLE)?.takeIf(String::isNotBlank) ?: track.title,
                artistName = tag?.getFirst(FieldKey.ARTIST)?.takeIf(String::isNotBlank) ?: track.artistName,
                albumTitle = tag?.getFirst(FieldKey.ALBUM)?.takeIf(String::isNotBlank) ?: track.albumTitle,
                durationMs = (header?.trackLength?.toLong()?.let { it * 1000 })?.takeIf { it > 0 } ?: track.durationMs,
                bitrateKbps = header?.bitRate?.toIntOrNull() ?: track.bitrateKbps,
                sampleRateHz = header?.sampleRateAsNumber ?: track.sampleRateHz,
                channels = header?.channels?.toIntOrNull() ?: track.channels,
                mimeType = header?.format ?: track.mimeType,
                embeddedArtHash = artworkHash ?: track.embeddedArtHash
            )
        } catch (e: Exception) {
            Log.w("TagEnrichment", "Could not read tags for '${track.displayName}': ${e.message}")
            return null
        } finally {
            tempFile.delete()
        }
    }

    private fun copyUriToTempFile(uri: Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val tempFile = File.createTempFile("genplayer_media", ".tmp", context.cacheDir)

            FileOutputStream(tempFile).use { outputStream ->
                inputStream.use { it.copyTo(outputStream) }
            }
            tempFile
        } catch (e: Exception) {
            Log.e("TagEnrichment", "Failed to copy URI to temp file: $uri", e)
            null
        }
    }

    private suspend fun saveArtwork(artworkData: ByteArray): String {
        val hash = sha1(artworkData)
        val artDir = File(context.filesDir, "art")
        if (!artDir.exists()) {
            artDir.mkdirs()
        }
        val file = File(artDir, "$hash.jpg")

        if (!file.exists()) {
            file.writeBytes(artworkData)
            val artworkCache = ArtworkCache(
                hash = hash,
                path = file.absolutePath,
                width = null,
                height = null
            )
            artworkDao.insert(artworkCache)
        }
        return hash
    }

    private fun sha1(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-1")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }
}