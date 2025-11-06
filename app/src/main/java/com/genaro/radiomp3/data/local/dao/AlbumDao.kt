package com.genaro.radiomp3.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.genaro.radiomp3.data.local.Album

@Dao
interface AlbumDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(albums: List<Album>)

    @Query("SELECT * FROM Album ORDER BY title ASC")
    suspend fun getAllAlbums(): List<Album>
}