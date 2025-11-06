package com.genaro.radiomp3.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.genaro.radiomp3.data.local.ArtworkCache

@Dao
interface ArtworkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(artwork: ArtworkCache)

    @Query("SELECT * FROM ArtworkCache WHERE hash = :hash")
    suspend fun getArtworkByHash(hash: String): ArtworkCache?
}