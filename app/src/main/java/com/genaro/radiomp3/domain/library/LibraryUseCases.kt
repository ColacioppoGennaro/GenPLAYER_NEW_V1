package com.genaro.radiomp3.domain.library

import android.net.Uri
import com.genaro.radiomp3.data.local.Album
import com.genaro.radiomp3.data.local.Track
import com.genaro.radiomp3.data.local.dao.AlbumDao
import com.genaro.radiomp3.data.local.dao.TrackDao

class LibraryUseCases(
    private val trackDao: TrackDao,
    private val albumDao: AlbumDao
) {

    suspend fun tracksByAlbum(albumId: Long): List<Track> {
        return trackDao.getTracksByAlbum(albumId)
    }

    suspend fun randomAlbum(): Album? {
        return albumDao.getAllAlbums().randomOrNull()
    }

    suspend fun tracksOfRandomAlbum(): List<Track> {
        val randomAlbum = randomAlbum()
        return if (randomAlbum != null) {
            trackDao.getTracksByAlbum(randomAlbum.id)
        } else {
            emptyList()
        }
    }

    suspend fun allTracksRandom(): List<Track> {
        return trackDao.searchTracks("%%").shuffled()
    }

    suspend fun openSingleTrack(uri: Uri): Track? {
        // Not implemented yet
        return null
    }

    suspend fun folderChildren(folderIdOrPath: String): List<Any> {
        // Not implemented yet
        return emptyList()
    }

    suspend fun search(query: String): List<Track> {
        return trackDao.searchTracks("%query%")
    }
}