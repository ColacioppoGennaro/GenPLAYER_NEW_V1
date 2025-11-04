package com.genaro.radiomp3.api

import android.content.Context
import com.genaro.radiomp3.data.Prefs
import com.genaro.radiomp3.data.Station
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Service to fetch station data from Radio Browser API
 * with intelligent caching (1 hour TTL)
 */
object RadioApiService {

    private const val API_BASE = "https://de1.api.radio-browser.info"
    private const val CACHE_DURATION_MS = 60 * 60 * 1000L // 1 hour

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * Fetch station details by UUID with caching
     */
    suspend fun getStationByUuid(context: Context, uuid: String): Station? = withContext(Dispatchers.IO) {
        try {
            // Check cache first
            val cached = getCachedStation(context, uuid)
            if (cached != null) {
                android.util.Log.d("RadioAPI", "Using cached data for station $uuid")
                return@withContext cached
            }

            // Fetch from API
            android.util.Log.d("RadioAPI", "Fetching fresh data for station $uuid")
            val url = "$API_BASE/json/stations/byuuid/$uuid"
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "GenPlayerV1/1.0")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                android.util.Log.e("RadioAPI", "API error: ${response.code}")
                return@withContext null
            }

            val body = response.body?.string() ?: return@withContext null

            // Parse JSON array (API returns array with single station)
            val type = object : TypeToken<List<Station>>() {}.type
            val stations: List<Station> = gson.fromJson(body, type)
            val station = stations.firstOrNull()

            // Cache the result
            if (station != null) {
                cacheStation(context, uuid, station)
            }

            station
        } catch (e: Exception) {
            android.util.Log.e("RadioAPI", "Error fetching station: ${e.message}")
            null
        }
    }

    /**
     * Get cached station if still fresh (< 1 hour old)
     */
    private fun getCachedStation(context: Context, uuid: String): Station? {
        val prefs = context.getSharedPreferences("station_cache", Context.MODE_PRIVATE)

        // Check timestamp
        val cacheTime = prefs.getLong("time_$uuid", 0)
        val age = System.currentTimeMillis() - cacheTime
        if (age > CACHE_DURATION_MS) {
            return null // Cache expired
        }

        // Get cached JSON
        val json = prefs.getString("data_$uuid", null) ?: return null

        return try {
            gson.fromJson(json, Station::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Save station to cache with timestamp
     */
    private fun cacheStation(context: Context, uuid: String, station: Station) {
        val prefs = context.getSharedPreferences("station_cache", Context.MODE_PRIVATE)
        val json = gson.toJson(station)

        prefs.edit()
            .putString("data_$uuid", json)
            .putLong("time_$uuid", System.currentTimeMillis())
            .apply()
    }

    /**
     * Clear all cached station data
     */
    fun clearCache(context: Context) {
        val prefs = context.getSharedPreferences("station_cache", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}
