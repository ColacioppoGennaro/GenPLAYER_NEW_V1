package com.genaro.radiomp3.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.genaro.radiomp3.data.local.SafRoot

@Dao
interface SafRootDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(safRoot: SafRoot)

    @Query("SELECT * FROM SafRoot")
    suspend fun getAll(): List<SafRoot>
}