package com.genaro.radiomp3.playback

import android.util.LruCache
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class ArtworkProvider {

    private val client = OkHttpClient()
    private val gson = Gson()
    private val artworkCache = LruCache<String, String>(100)

    suspend fun findArtwork(artist: String, title: String): String? {
        val query = "$artist $title"
        val cached = artworkCache.get(query)
        if (cached != null) {
            android.util.Log.d("ArtworkProvider", "ART hit [cache]: $query")
            return cached
        }

        return withContext(Dispatchers.IO) {
            try {
                val url = "https://itunes.apple.com/search?term=$query&entity=song&limit=1"
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    android.util.Log.w("ArtworkProvider", "ART miss [http_error=${response.code}]: $query")
                    return@withContext null
                }

                val result = gson.fromJson(response.body?.string(), ITunesResult::class.java)
                val artworkUrl = result.results.firstOrNull()?.artworkUrl100
                    ?.replace("100x100bb.jpg", "512x512bb.jpg")

                if (artworkUrl != null) {
                    android.util.Log.d("ArtworkProvider", "ART hit [itunes]: $artworkUrl")
                    artworkCache.put(query, artworkUrl)
                } else {
                    android.util.Log.d("ArtworkProvider", "ART miss [no_match]: $query")
                }

                artworkUrl
            } catch (e: IOException) {
                android.util.Log.e("ArtworkProvider", "ART fail [io_exception]: $query", e)
                null
            }
        }
    }

    private data class ITunesResult(
        val results: List<SongResult>
    )

    private data class SongResult(
        @SerializedName("artworkUrl100")
        val artworkUrl100: String?
    )
}