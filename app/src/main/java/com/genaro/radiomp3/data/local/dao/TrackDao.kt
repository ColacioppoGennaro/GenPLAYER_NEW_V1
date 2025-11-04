package com.genaro.radiomp3.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.genaro.radiomp3.data.local.Track
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tracks: List<Track>)

    @Query("SELECT * FROM Track WHERE id = :trackId")
    suspend fun getTrackById(trackId: Long): Track?

    @Query("SELECT * FROM Track WHERE folderPathDisplay = :folderPath")
    suspend fun getTracksByFolder(folderPath: String): List<Track>

    @Query("SELECT * FROM Track WHERE albumIdRef = :albumId")
    suspend fun getTracksByAlbum(albumId: Long): List<Track>

    @Query("SELECT * FROM Track WHERE artistName = :artistName")
    suspend fun getTracksByArtist(artistName: String): List<Track>

    @Query("SELECT * FROM Track WHERE title LIKE :query OR artistName LIKE :query OR albumTitle LIKE :query OR displayName LIKE :query")
    suspend fun searchTracks(query: String): List<Track>

    @Query("SELECT * FROM Track")
    suspend fun getAllTracks(): List<Track>

    @Query("DELETE FROM Track")
    suspend fun deleteAll()

    @Query("SELECT * FROM Track WHERE title IS NULL OR title = ''")
    suspend fun getTracksMissingTags(): List<Track>

    // Flow methods for reactive updates
    @Query("SELECT * FROM Track")
    fun getAllTracksFlow(): Flow<List<Track>>

    @Query("SELECT * FROM Track WHERE folderPathDisplay = :folderPath ORDER BY title COLLATE NOCASE")
    fun getTracksByFolderFlow(folderPath: String): Flow<List<Track>>

    @Query("SELECT * FROM Track WHERE albumTitle = :albumTitle ORDER BY title COLLATE NOCASE")
    fun getTracksByAlbumFlow(albumTitle: String): Flow<List<Track>>

    @Query("SELECT * FROM Track WHERE artistName = :artistName ORDER BY albumTitle COLLATE NOCASE, title COLLATE NOCASE")
    fun getTracksByArtistFlow(artistName: String): Flow<List<Track>>

    @Query("SELECT * FROM Track ORDER BY title COLLATE NOCASE")
    fun getAllTracksOrderedFlow(): Flow<List<Track>>
}