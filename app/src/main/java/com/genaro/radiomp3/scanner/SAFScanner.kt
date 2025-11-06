package com.genaro.radiomp3.scanner

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.genaro.radiomp3.data.Prefs
import com.genaro.radiomp3.data.local.Track
import com.genaro.radiomp3.data.local.dao.SafRootDao
import com.genaro.radiomp3.data.local.dao.TrackDao

class SAFScanner(private val context: Context, private val trackDao: TrackDao, private val safRootDao: SafRootDao) {

    suspend fun scan(isDifferential: Boolean = true) {
        try {
            android.util.Log.d("SAFScanner", "Starting ${if (isDifferential) "differential" else "full"} scan...")

            // Get existing tracks from DB (for diff scan)
            val existingTracks = if (isDifferential) {
                android.util.Log.d("SAFScanner", "Loading existing tracks from database...")
                val tracks = trackDao.getAllTracks()
                android.util.Log.d("SAFScanner", "Found ${tracks.size} existing tracks")
                tracks.associateBy { it.uri }
            } else {
                android.util.Log.d("SAFScanner", "Clearing old tracks from database...")
                trackDao.deleteAll()
                android.util.Log.d("SAFScanner", "Database cleared")
                emptyMap()
            }

        // Collect URIs from both sources
        val urisToScan = mutableSetOf<android.net.Uri>()

        val roots = safRootDao.getAll()
        android.util.Log.d("SAFScanner", "🗄️ Room database roots: ${roots.size} folders")
        if (roots.isNotEmpty()) {
            roots.forEach { root ->
                android.util.Log.d("SAFScanner", "  └─ URI: ${root.treeUri}, Name: ${root.displayName}")
                urisToScan.add(android.net.Uri.parse(root.treeUri))
            }
        } else {
            android.util.Log.d("SAFScanner", "  └─ No folders in Room database")
        }

        val legacyUri = Prefs.getTreeUri(context)
        android.util.Log.d("SAFScanner", "📋 SharedPreferences legacy: ${if (legacyUri != null) "YES ($legacyUri)" else "NO"}")
        if (legacyUri != null) {
            android.util.Log.d("SAFScanner", "Found legacy folder in Settings: $legacyUri")
            urisToScan.add(legacyUri)
        }

        android.util.Log.d("SAFScanner", "📊 Total unique folders to scan: ${urisToScan.size}")

        if (urisToScan.isEmpty()) {
            android.util.Log.w("SAFScanner", "⚠️ NO FOLDERS CONFIGURED!")
            android.util.Log.w("SAFScanner", "  - Room database is empty")
            android.util.Log.w("SAFScanner", "  - SharedPreferences legacy is empty")
            android.util.Log.w("SAFScanner", "  - User needs to configure a folder in Settings first")
            return
        }

        val tracksBuffer = mutableListOf<Track>()
        val BATCH_SIZE = 50
        var totalTracksAdded = 0

        for (uri in urisToScan) {
            val rootFile = DocumentFile.fromTreeUri(context, uri)
            val displayName = rootFile?.name ?: uri.toString()
            android.util.Log.d("SAFScanner", "📁 Scanning: $displayName")
            android.util.Log.d("SAFScanner", "  URI: $uri")

            if (rootFile == null) {
                android.util.Log.e("SAFScanner", "  ❌ DocumentFile is NULL - cannot access URI")
                android.util.Log.e("SAFScanner", "  ⚠️ SOLUTION: Re-grant permission in Settings > Pick Folder")
            } else if (!rootFile.exists()) {
                android.util.Log.e("SAFScanner", "  ❌ Folder does not exist at this URI")
                android.util.Log.e("SAFScanner", "  ⚠️ SOLUTION: Folder may have been deleted or moved")
            } else if (!rootFile.canRead()) {
                android.util.Log.e("SAFScanner", "  ❌ Cannot read folder - permission lost?")
                android.util.Log.e("SAFScanner", "  ⚠️ SOLUTION: Re-grant permission in Settings > Pick Folder")
            } else {
                android.util.Log.d("SAFScanner", "  ✅ Folder accessible")
                android.util.Log.d("SAFScanner", "  🔍 Scanning files recursively...")

                val initialBufferSize = tracksBuffer.size
                scanDirectoryDifferential(rootFile, tracksBuffer, "", existingTracks)
                val tracksFound = tracksBuffer.size - initialBufferSize
                android.util.Log.d("SAFScanner", "  ✅ Scan found $tracksFound audio files")

                // Write buffered tracks to DB in batches
                while (tracksBuffer.isNotEmpty()) {
                    val batch = tracksBuffer.take(BATCH_SIZE)
                    trackDao.insert(batch)
                    totalTracksAdded += batch.size
                    android.util.Log.d("SAFScanner", "  💾 Batch written: ${batch.size} tracks (total so far: $totalTracksAdded)")

                    // Remove written tracks from buffer
                    repeat(batch.size) { tracksBuffer.removeAt(0) }

                    // Delay to prevent overwhelming the UI thread
                    kotlinx.coroutines.delay(100)
                }
            }
        }

        // Write any remaining tracks
        if (tracksBuffer.isNotEmpty()) {
            trackDao.insert(tracksBuffer)
            totalTracksAdded += tracksBuffer.size
            android.util.Log.d("SAFScanner", "💾 Final batch: ${tracksBuffer.size} tracks")
            tracksBuffer.clear()
        }

            android.util.Log.d("SAFScanner", "═══════════════════════════════════════════════════")
            android.util.Log.d("SAFScanner", "✅ SCAN COMPLETE")
            android.util.Log.d("SAFScanner", "   Total tracks added/updated: $totalTracksAdded")
            if (totalTracksAdded == 0) {
                android.util.Log.w("SAFScanner", "⚠️  NO AUDIO FILES FOUND!")
                android.util.Log.w("SAFScanner", "   Possible reasons:")
                android.util.Log.w("SAFScanner", "   1. Folder is empty")
                android.util.Log.w("SAFScanner", "   2. No .mp3, .flac, .m4a, .wav files present")
                android.util.Log.w("SAFScanner", "   3. Files are in subfolders (scanner is recursive)")
            }
            android.util.Log.d("SAFScanner", "═══════════════════════════════════════════════════")
        } catch (e: Exception) {
            android.util.Log.e("SAFScanner", "💥 CRITICAL ERROR in scan()", e)
            android.util.Log.e("SAFScanner", "Exception type: ${e.javaClass.simpleName}")
            android.util.Log.e("SAFScanner", "Message: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun scanDirectoryDifferential(
        directory: DocumentFile,
        tracks: MutableList<Track>,
        relativePath: String,
        existingTracks: Map<String, Track>
    ) {
        val files = directory.listFiles()
        android.util.Log.d("SAFScanner", "Scanning directory: ${directory.name} - Found ${files.size} items")

        for (file in files) {
            if (file.isDirectory) {
                android.util.Log.d("SAFScanner", "  📂 Subfolder: ${file.name}")
                scanDirectoryDifferential(file, tracks, "$relativePath/${file.name}", existingTracks)
            } else if (isAudioFile(file)) {
                val fileUri = file.uri.toString()
                val existingTrack = existingTracks[fileUri]

                // Check if file is new or modified
                val lastModified = file.lastModified()
                val isNew = existingTrack == null
                val isModified = existingTrack != null && existingTrack.dateAdded != lastModified

                if (isNew || isModified) {
                    android.util.Log.d("SAFScanner", "  🎵 ${if (isNew) "NEW" else "MODIFIED"}: ${file.name}")

                    val metadata = extractAudioMetadata(file)

                    val track = Track(
                        uri = fileUri,
                        displayName = file.name ?: "Unknown",
                        title = metadata["title"] as? String,
                        artistName = metadata["artist"] as? String,
                        albumTitle = metadata["album"] as? String,
                        albumIdRef = null,
                        durationMs = metadata["duration"] as? Long,
                        bitrateKbps = metadata["bitrate"] as? Int,
                        sampleRateHz = metadata["sampleRate"] as? Int,
                        bitDepth = metadata["bitDepth"] as? Int,
                        channels = metadata["channels"] as? Int,
                        mimeType = file.type,
                        sizeBytes = file.length(),
                        dateAdded = lastModified,
                        folderPathDisplay = relativePath,
                        embeddedArtHash = null,
                        audioHash = null,
                        source = "SAF"
                    )
                    tracks.add(track)
                } else {
                    android.util.Log.v("SAFScanner", "  ⏭️ Unchanged: ${file.name}")
                }
            } else {
                android.util.Log.v("SAFScanner", "  ⏭️ Skipped: ${file.name}")
            }
        }
    }

    private fun scanDirectory(directory: DocumentFile, tracks: MutableList<Track>, relativePath: String) {
        val files = directory.listFiles()
        android.util.Log.d("SAFScanner", "Scanning directory: ${directory.name} - Found ${files.size} items")

        for (file in files) {
            if (file.isDirectory) {
                android.util.Log.d("SAFScanner", "  📂 Subfolder: ${file.name}")
                scanDirectory(file, tracks, "$relativePath/${file.name}")
            } else if (isAudioFile(file)) {
                android.util.Log.d("SAFScanner", "  🎵 Audio file: ${file.name} (${file.length()} bytes)")

                // Extract audio metadata
                val metadata = extractAudioMetadata(file)

                val track = Track(
                    uri = file.uri.toString(),
                    displayName = file.name ?: "Unknown",
                    title = metadata["title"] as? String,
                    artistName = metadata["artist"] as? String,
                    albumTitle = metadata["album"] as? String,
                    albumIdRef = null,
                    durationMs = metadata["duration"] as? Long,
                    bitrateKbps = metadata["bitrate"] as? Int,
                    sampleRateHz = metadata["sampleRate"] as? Int,
                    bitDepth = metadata["bitDepth"] as? Int,
                    channels = metadata["channels"] as? Int,
                    mimeType = file.type,
                    sizeBytes = file.length(),
                    dateAdded = file.lastModified(),
                    folderPathDisplay = relativePath,
                    embeddedArtHash = null,
                    audioHash = null,
                    source = "SAF"
                )
                tracks.add(track)
            } else {
                android.util.Log.v("SAFScanner", "  ⏭️ Skipped: ${file.name}")
            }
        }
    }

    private fun extractAudioMetadata(file: DocumentFile): Map<String, Any?> {
        val metadata = mutableMapOf<String, Any?>()

        try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(context, file.uri)

            android.util.Log.d("SAFScanner", "📊 Extracting metadata for: ${file.name}")

            // Extract text metadata
            metadata["title"] = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
            metadata["artist"] = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
            metadata["album"] = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM)

            android.util.Log.d("SAFScanner", "  Title: ${metadata["title"]}, Artist: ${metadata["artist"]}, Album: ${metadata["album"]}")

            // Extract duration
            val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            if (durationStr != null) {
                metadata["duration"] = durationStr.toLongOrNull()
                android.util.Log.d("SAFScanner", "  Duration: ${metadata["duration"]} ms")
            }

            // Extract bitrate (in bps, convert to kbps)
            val bitrateStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_BITRATE)
            if (bitrateStr != null) {
                val bps = bitrateStr.toLongOrNull()
                if (bps != null) {
                    metadata["bitrate"] = (bps / 1000).toInt()
                    android.util.Log.d("SAFScanner", "  Bitrate: ${metadata["bitrate"]} kbps")
                } else {
                    android.util.Log.w("SAFScanner", "  Bitrate string not parseable: $bitrateStr")
                }
            } else {
                android.util.Log.w("SAFScanner", "  Bitrate: NOT AVAILABLE")
            }

            // Extract sample rate
            val sampleRateStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
            if (sampleRateStr != null) {
                metadata["sampleRate"] = sampleRateStr.toIntOrNull()
                android.util.Log.d("SAFScanner", "  Sample rate: ${metadata["sampleRate"]} Hz")
            } else {
                android.util.Log.w("SAFScanner", "  Sample rate: NOT AVAILABLE")
            }

            // Extract channels - Note: NUM_TRACKS is not the right key for channels
            // Android doesn't provide a direct METADATA_KEY for channel count
            // We'll default to stereo for music files
            val mimeType = file.type
            android.util.Log.d("SAFScanner", "  MIME type: $mimeType")

            // Default to stereo for most music files
            if (mimeType?.contains("audio", ignoreCase = true) == true) {
                metadata["channels"] = 2  // Default assumption: stereo
                android.util.Log.d("SAFScanner", "  Channels: 2 (assumed stereo)")
            }

            // Bit depth is not directly available via MediaMetadataRetriever
            // For FLAC, assume 16-bit (most common)
            if (mimeType?.contains("flac", ignoreCase = true) == true) {
                metadata["bitDepth"] = 16
                android.util.Log.d("SAFScanner", "  Bit depth: 16-bit (FLAC default assumption)")
            }

            android.util.Log.d("SAFScanner", "✅ Metadata extraction complete for ${file.name}")
            retriever.release()
        } catch (e: Exception) {
            android.util.Log.e("SAFScanner", "❌ Error extracting metadata for ${file.name}", e)
        }

        return metadata
    }

    private fun isAudioFile(file: DocumentFile): Boolean {
        val name = file.name ?: return false
        return name.endsWith(".mp3", true) ||
                name.endsWith(".flac", true) ||
                name.endsWith(".m4a", true) ||
                name.endsWith(".wav", true)
    }
}