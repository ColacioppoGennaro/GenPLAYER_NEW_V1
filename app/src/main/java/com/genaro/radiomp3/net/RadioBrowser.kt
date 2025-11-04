package com.genaro.radiomp3.net

import android.content.Context
import com.genaro.radiomp3.data.Country
import com.genaro.radiomp3.data.Prefs
import com.genaro.radiomp3.data.Station
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object RadioBrowser {
    private const val BASE_URL = "https://de1.api.radio-browser.info/json"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    suspend fun getCountries(ctx: Context): Result<List<Country>> = withContext(Dispatchers.IO) {
        // Check cache first
        Prefs.getCachedCountries(ctx)?.let {
            return@withContext Result.success(it)
        }

        try {
            val request = Request.Builder()
                .url("$BASE_URL/countries")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
            val type = object : TypeToken<List<Country>>() {}.type
            val countries: List<Country> = gson.fromJson(body, type)

            // Filter and sort
            val filtered = countries
                .filter { it.stationCount > 0 }
                .sortedByDescending { it.stationCount }

            // Cache it
            Prefs.setCachedCountries(ctx, filtered)

            Result.success(filtered)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getStationsByCountry(ctx: Context, country: String): Result<List<Station>> =
        withContext(Dispatchers.IO) {
            // Check cache
            Prefs.getCachedStations(ctx, country)?.let {
                android.util.Log.d("RadioBrowser", "Returning ${it.size} cached stations for '$country'")
                return@withContext Result.success(it)
            }

            try {
                // Request stations with favicon and extended info for better quality
                val url = "$BASE_URL/stations/bycountryexact/${country.replace(" ", "%20")}?limit=200&order=votes&reverse=true&has_extended_info=true"
                android.util.Log.d("RadioBrowser", "Fetching from URL: $url")

                val request = Request.Builder()
                    .url(url)
                    .build()

                val response = client.newCall(request).execute()
                android.util.Log.d("RadioBrowser", "HTTP Response code: ${response.code}")

                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}"))
                }

                val body = response.body?.string() ?:
                    return@withContext Result.failure(Exception("Empty response"))

                android.util.Log.d("RadioBrowser", "Response body length: ${body.length} chars")

                val type = object : TypeToken<List<Station>>() {}.type
                val stations: List<Station> = gson.fromJson(body, type)
                android.util.Log.d("RadioBrowser", "Parsed ${stations.size} stations")

                // Filter valid stations
                val filtered = stations.filter {
                    it.url.isNotBlank() && it.name.isNotBlank()
                }
                android.util.Log.d("RadioBrowser", "After filtering: ${filtered.size} valid stations")

                // Cache it
                Prefs.setCachedStations(ctx, country, filtered)

                Result.success(filtered)
            } catch (e: Exception) {
                android.util.Log.e("RadioBrowser", "Error fetching stations", e)
                Result.failure(e)
            }
        }

    /**
     * Search for high-quality stations worldwide (FLAC, high bitrate)
     * Used as fallback when local country has no high-quality stations
     */
    suspend fun getHighQualityStations(codec: String? = null, minBitrate: Int? = null): Result<List<Station>> =
        withContext(Dispatchers.IO) {
            try {
                // Build URL based on search criteria
                val url = when {
                    codec != null -> "$BASE_URL/stations/bycodecexact/${codec}?limit=50&order=bitrate&reverse=true"
                    minBitrate != null -> "$BASE_URL/stations/search?limit=50&order=bitrate&reverse=true&bitrate_min=$minBitrate"
                    else -> "$BASE_URL/stations/search?limit=50&order=bitrate&reverse=true&bitrate_min=256"
                }

                android.util.Log.d("RadioBrowser", "Fetching global high-quality stations from: $url")

                val request = Request.Builder()
                    .url(url)
                    .build()

                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}"))
                }

                val body = response.body?.string() ?:
                    return@withContext Result.failure(Exception("Empty response"))

                val type = object : TypeToken<List<Station>>() {}.type
                val stations: List<Station> = gson.fromJson(body, type)

                // Filter valid stations
                val filtered = stations.filter {
                    it.url.isNotBlank() && it.name.isNotBlank()
                }

                android.util.Log.d("RadioBrowser", "Found ${filtered.size} global high-quality stations")

                Result.success(filtered)
            } catch (e: Exception) {
                android.util.Log.e("RadioBrowser", "Error fetching global stations", e)
                Result.failure(e)
            }
        }
}
