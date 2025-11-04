package com.genaro.radiomp3.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.genaro.radiomp3.data.local.Artist

@Dao
interface ArtistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(artists: List<Artist>)

    @Query("SELECT * FROM Artist ORDER BY name ASC")
    suspend fun getAllArtists(): List<Artist>
}