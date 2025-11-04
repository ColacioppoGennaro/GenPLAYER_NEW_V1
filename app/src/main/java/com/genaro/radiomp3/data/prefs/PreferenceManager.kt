package com.genaro.radiomp3.data.prefs

import android.content.Context
import android.util.Log
import com.genaro.radiomp3.data.local.AppDatabase
import com.genaro.radiomp3.data.local.DefaultPreferences
import com.genaro.radiomp3.data.local.PreferenceEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Singleton PreferenceManager
 * Handles all user preferences with typed getters/setters
 * Auto-initializes DB with defaults on first run
 */
class PreferenceManager private constructor(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val preferenceDao = db.preferenceDao()

    init {
        // Auto-initialize defaults on first run
        val scope = CoroutineScope(Dispatchers.IO + Job())
        scope.launch {
            try {
                val count = preferenceDao.getPreferenceCount()
                if (count == 0) {
                    Log.d("PreferenceManager", "First run - initializing default preferences")
                    preferenceDao.insertAll(DefaultPreferences.defaults)
                }
            } catch (e: Exception) {
                Log.e("PreferenceManager", "Error initializing defaults", e)
            }
        }
    }

    companion object {
        @Volatile
        private var instance: PreferenceManager? = null

        fun getInstance(context: Context): PreferenceManager {
            return instance ?: synchronized(this) {
                instance ?: PreferenceManager(context.applicationContext).also { instance = it }
            }
        }
    }

    // ===== BOOLEAN PREFERENCES =====

    suspend fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val pref = preferenceDao.getByKey(key)
                pref?.value?.toBoolean() ?: defaultValue
            } catch (e: Exception) {
                Log.e("PreferenceManager", "Error getting boolean for key=$key", e)
                defaultValue
            }
        }
    }

    fun getBooleanFlow(key: String, defaultValue: Boolean = false): Flow<Boolean> {
        return preferenceDao.getByKeyFlow(key).map { pref ->
            pref?.value?.toBoolean() ?: defaultValue
        }
    }

    suspend fun setBoolean(key: String, value: Boolean) {
        withContext(Dispatchers.IO) {
            try {
                val current = preferenceDao.getByKey(key)
                if (current != null) {
                    preferenceDao.update(current.copy(value = value.toString(), lastModified = System.currentTimeMillis()))
                } else {
                    preferenceDao.insert(
                        PreferenceEntry(key, value.toString(), "custom", "boolean", value.toString())
                    )
                }
                Log.d("PreferenceManager", "Set $key = $value")
            } catch (e: Exception) {
                Log.e("PreferenceManager", "Error setting boolean for key=$key", e)
            }
        }
    }

    // ===== INT PREFERENCES =====

    suspend fun getInt(key: String, defaultValue: Int = 0): Int {
        return withContext(Dispatchers.IO) {
            try {
                val pref = preferenceDao.getByKey(key)
                pref?.value?.toIntOrNull() ?: defaultValue
            } catch (e: Exception) {
                Log.e("PreferenceManager", "Error getting int for key=$key", e)
                defaultValue
            }
        }
    }

    fun getIntFlow(key: String, defaultValue: Int = 0): Flow<Int> {
        return preferenceDao.getByKeyFlow(key).map { pref ->
            pref?.value?.toIntOrNull() ?: defaultValue
        }
    }

    suspend fun setInt(key: String, value: Int) {
        withContext(Dispatchers.IO) {
            try {
                val current = preferenceDao.getByKey(key)
                if (current != null) {
                    preferenceDao.update(current.copy(value = value.toString(), lastModified = System.currentTimeMillis()))
                } else {
                    preferenceDao.insert(
                        PreferenceEntry(key, value.toString(), "custom", "int", value.toString())
                    )
                }
                Log.d("PreferenceManager", "Set $key = $value")
            } catch (e: Exception) {
                Log.e("PreferenceManager", "Error setting int for key=$key", e)
            }
        }
    }

    // ===== STRING PREFERENCES =====

    suspend fun getString(key: String, defaultValue: String = ""): String {
        return withContext(Dispatchers.IO) {
            try {
                val pref = preferenceDao.getByKey(key)
                pref?.value ?: defaultValue
            } catch (e: Exception) {
                Log.e("PreferenceManager", "Error getting string for key=$key", e)
                defaultValue
            }
        }
    }

    fun getStringFlow(key: String, defaultValue: String = ""): Flow<String> {
        return preferenceDao.getByKeyFlow(key).map { pref ->
            pref?.value ?: defaultValue
        }
    }

    suspend fun setString(key: String, value: String) {
        withContext(Dispatchers.IO) {
            try {
                val current = preferenceDao.getByKey(key)
                if (current != null) {
                    preferenceDao.update(current.copy(value = value, lastModified = System.currentTimeMillis()))
                } else {
                    preferenceDao.insert(
                        PreferenceEntry(key, value, "custom", "string", value)
                    )
                }
                Log.d("PreferenceManager", "Set $key = $value")
            } catch (e: Exception) {
                Log.e("PreferenceManager", "Error setting string for key=$key", e)
            }
        }
    }

    // ===== CATEGORY-BASED PREFERENCES =====

    suspend fun getPreferencesByCategory(category: String): List<PreferenceEntry> {
        return withContext(Dispatchers.IO) {
            try {
                preferenceDao.getByCategory(category)
            } catch (e: Exception) {
                Log.e("PreferenceManager", "Error getting preferences for category=$category", e)
                emptyList()
            }
        }
    }

    fun getPreferencesByCategoryFlow(category: String): Flow<List<PreferenceEntry>> {
        return preferenceDao.getByCategoryFlow(category)
    }

    // ===== UTILITY =====

    suspend fun reset() {
        withContext(Dispatchers.IO) {
            try {
                preferenceDao.deleteAll()
                preferenceDao.insertAll(DefaultPreferences.defaults)
                Log.d("PreferenceManager", "Preferences reset to defaults")
            } catch (e: Exception) {
                Log.e("PreferenceManager", "Error resetting preferences", e)
            }
        }
    }

    suspend fun exportPreferencesAsString(): String {
        return withContext(Dispatchers.IO) {
            try {
                val prefs = preferenceDao.getAllPreferences()
                buildString {
                    appendLine("=== GenPlayer Preferences Export ===")
                    appendLine("Exported at: ${System.currentTimeMillis()}")
                    appendLine()
                    prefs.groupBy { it.category }.forEach { (category, items) ->
                        appendLine("[$category]")
                        items.forEach { pref ->
                            appendLine("  ${pref.key} = ${pref.value}")
                        }
                        appendLine()
                    }
                }
            } catch (e: Exception) {
                Log.e("PreferenceManager", "Error exporting preferences", e)
                ""
            }
        }
    }
}
