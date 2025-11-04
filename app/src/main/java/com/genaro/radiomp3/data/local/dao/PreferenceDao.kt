package com.genaro.radiomp3.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.genaro.radiomp3.data.local.PreferenceEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface PreferenceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(preference: PreferenceEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(preferences: List<PreferenceEntry>)

    @Update
    suspend fun update(preference: PreferenceEntry)

    @Query("SELECT * FROM Preference WHERE key = :key")
    suspend fun getByKey(key: String): PreferenceEntry?

    @Query("SELECT * FROM Preference WHERE key = :key")
    fun getByKeyFlow(key: String): Flow<PreferenceEntry?>

    @Query("SELECT * FROM Preference WHERE category = :category")
    suspend fun getByCategory(category: String): List<PreferenceEntry>

    @Query("SELECT * FROM Preference WHERE category = :category")
    fun getByCategoryFlow(category: String): Flow<List<PreferenceEntry>>

    @Query("SELECT * FROM Preference ORDER BY category, key")
    suspend fun getAllPreferences(): List<PreferenceEntry>

    @Query("SELECT * FROM Preference ORDER BY category, key")
    fun getAllPreferencesFlow(): Flow<List<PreferenceEntry>>

    @Query("DELETE FROM Preference WHERE key = :key")
    suspend fun deleteByKey(key: String)

    @Query("DELETE FROM Preference")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM Preference")
    suspend fun getPreferenceCount(): Int
}
