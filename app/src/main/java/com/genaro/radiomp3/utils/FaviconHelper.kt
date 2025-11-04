package com.genaro.radiomp3.utils

import android.util.Log
import java.net.URL

object FaviconHelper {

    /**
     * Generates a list of possible favicon URLs for a station
     * Priority:
     * 1. Direct favicon URL from API
     * 2. Favicon from homepage
     * 3. Favicon from stream URL domain (using Google service)
     */
    fun getFaviconUrls(
        apiIconUrl: String?,
        homepage: String?,
        streamUrl: String?
    ): List<String> {
        val urls = mutableListOf<String>()

        // 1. Direct favicon from API
        if (!apiIconUrl.isNullOrBlank()) {
            urls.add(apiIconUrl)
            Log.d("FaviconHelper", "Added API favicon: $apiIconUrl")
        }

        // 2. Homepage favicon
        if (!homepage.isNullOrBlank()) {
            val homeUrl = if (homepage.endsWith("/")) "${homepage}favicon.ico" else "$homepage/favicon.ico"
            urls.add(homeUrl)
            Log.d("FaviconHelper", "Added homepage favicon: $homeUrl")
        }

        // 3. Extract domain from stream URL and use Google Favicon Service
        if (!streamUrl.isNullOrBlank()) {
            extractDomain(streamUrl)?.let { domain ->
                val googleFavicon = "https://www.google.com/s2/favicons?domain=$domain&sz=128"
                urls.add(googleFavicon)
                Log.d("FaviconHelper", "Added Google favicon for domain: $domain")
            }
        }

        // 4. Extract domain from homepage and use Google Favicon Service (as final fallback)
        if (!homepage.isNullOrBlank()) {
            extractDomain(homepage)?.let { domain ->
                val googleFavicon = "https://www.google.com/s2/favicons?domain=$domain&sz=128"
                if (!urls.contains(googleFavicon)) {
                    urls.add(googleFavicon)
                    Log.d("FaviconHelper", "Added Google favicon for homepage domain: $domain")
                }
            }
        }

        Log.d("FaviconHelper", "Generated ${urls.size} favicon URLs")
        return urls
    }

    /**
     * Extracts domain from URL
     * Example: "http://icy.unitedradio.it/RMC.mp3" -> "unitedradio.it"
     */
    private fun extractDomain(url: String): String? {
        return try {
            val parsedUrl = URL(url)
            parsedUrl.host
        } catch (e: Exception) {
            Log.w("FaviconHelper", "Failed to extract domain from: $url", e)
            null
        }
    }
}
