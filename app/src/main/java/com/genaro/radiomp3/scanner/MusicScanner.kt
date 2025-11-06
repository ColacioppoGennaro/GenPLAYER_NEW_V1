package com.genaro.radiomp3.scanner

import android.content.Context
import com.genaro.radiomp3.data.local.dao.AlbumDao
import com.genaro.radiomp3.data.local.dao.ArtistDao
import com.genaro.radiomp3.data.local.dao.ArtworkDao
import com.genaro.radiomp3.data.local.dao.SafRootDao
import com.genaro.radiomp3.data.local.dao.TrackDao

class MusicScanner(
    private val context: Context,
    private val trackDao: TrackDao,
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao,
    private val artworkDao: ArtworkDao,
    private val safRootDao: SafRootDao
) {

    suspend fun scan() {
        // Scan logic will be implemented here
    }
}