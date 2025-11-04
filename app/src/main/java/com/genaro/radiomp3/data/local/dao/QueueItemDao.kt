package com.genaro.radiomp3.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.genaro.radiomp3.data.local.QueueItem

@Dao
interface QueueItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: QueueItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<QueueItem>)

    @Query("SELECT * FROM QueueItem ORDER BY position ASC")
    suspend fun getAll(): List<QueueItem>

    @Query("DELETE FROM QueueItem")
    suspend fun clearAll()

    @Query("DELETE FROM QueueItem WHERE id = :id")
    suspend fun delete(id: Long)
}
