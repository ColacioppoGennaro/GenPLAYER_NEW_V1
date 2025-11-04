package com.genaro.radiomp3.net

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Deezer API client for fetching album artwork
 * API Documentation: https://developers.deezer.com/api
 */
object DeezerApi {
    private const val BASE_URL = "https://api.deezer.com"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * Search for album artwork by artist and album name
     * @param artist Artist name
     * @param album Album name
     * @return URL of the album cover (high quality) or null if not found
     */
    suspend fun searchAlbumCover(artist: String?, album: String?): String? = withContext(Dispatchers.IO) {
        try {
            // Need at least artist or album to search
            if (artist.isNullOrBlank() && album.isNullOrBlank()) {
                android.util.Log.d("DeezerApi", "No artist or album provided")
                return@withContext null
            }

            // Build search query
            val query = buildSearchQuery(artist, album)
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$BASE_URL/search/album?q=$encodedQuery&limit=5"

            android.util.Log.d("DeezerApi", "Searching: $url")

            val request = Request.Builder()
                .url(url)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                android.util.Log.w("DeezerApi", "HTTP ${response.code}")
                return@withContext null
            }

            val body = response.body?.string()
            if (body.isNullOrBlank()) {
                android.util.Log.w("DeezerApi", "Empty response")
                return@withContext null
            }

            val searchResult = gson.fromJson(body, DeezerSearchResult::class.java)

            if (searchResult.data.isEmpty()) {
                android.util.Log.d("DeezerApi", "No results found")
                return@withContext null
            }

            // Get the best match (first result, highest quality cover)
            val bestMatch = searchResult.data.firstOrNull()
            val coverUrl = bestMatch?.coverXl ?: bestMatch?.coverBig ?: bestMatch?.coverMedium

            android.util.Log.d("DeezerApi", "Found cover: $coverUrl")
            coverUrl

        } catch (e: Exception) {
            android.util.Log.e("DeezerApi", "Error searching album cover", e)
            null
        }
    }

    /**
     * Build a search query from artist and album
     * Deezer supports: artist:"name" album:"name"
     */
    private fun buildSearchQuery(artist: String?, album: String?): String {
        val parts = mutableListOf<String>()

        if (!artist.isNullOrBlank()) {
            parts.add("artist:\"${artist.trim()}\"")
        }

        if (!album.isNullOrBlank()) {
            parts.add("album:\"${album.trim()}\"")
        }

        return parts.joinToString(" ")
    }

    // Data classes for Deezer API response
    data class DeezerSearchResult(
        val data: List<DeezerAlbum> = emptyList(),
        val total: Int = 0
    )

    data class DeezerAlbum(
        val id: Long,
        val title: String,
        @SerializedName("cover")
        val cover: String? = null,
        @SerializedName("cover_small")
        val coverSmall: String? = null,
        @SerializedName("cover_medium")
        val coverMedium: String? = null,
        @SerializedName("cover_big")
        val coverBig: String? = null,
        @SerializedName("cover_xl")
        val coverXl: String? = null,
        val artist: DeezerArtist? = null
    )

    data class DeezerArtist(
        val id: Long,
        val name: String
    )
}
