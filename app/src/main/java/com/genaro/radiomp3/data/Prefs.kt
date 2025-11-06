package com.genaro.radiomp3.data

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Locale

object Prefs {
    private const val P = "prefs_radio_mp3"
    private val gson = Gson()

    // Existing keys
    private const val KEY_STREAM = "stream_url"
    private const val KEY_TREE = "mp3_tree"
    private const val KEY_KEEP_ON_CHARGING = "keep_on_charging"
    private const val KEY_AUTO_HIDE = "auto_hide_ui"

    // New keys
    private const val KEY_COUNTRY = "default_country"
    private const val KEY_FAVORITES = "favorites_json"
    private const val KEY_BUFFER_MODE = "buffer_mode"
    private const val KEY_COUNTRIES_CACHE = "countries_cache"
    private const val KEY_COUNTRIES_CACHE_TIME = "countries_cache_time"
    private const val KEY_STATIONS_CACHE_PREFIX = "stations_cache_"
    private const val KEY_STATIONS_CACHE_TIME_PREFIX = "stations_time_"
    private const val KEY_HOMEPAGE_BUTTONS = "homepage_buttons_json"

    private const val CACHE_DURATION = 24 * 60 * 60 * 1000L // 24h

    // Legacy stream URL (now unused, kept for migration)
    fun getStream(ctx: Context): String =
        ctx.getSharedPreferences(P, Context.MODE_PRIVATE)
            .getString(KEY_STREAM, "") ?: ""

    fun setStream(ctx: Context, url: String) {
        ctx.getSharedPreferences(P, Context.MODE_PRIVATE)
            .edit().putString(KEY_STREAM, url.trim()).apply()
    }

    // Audio folder
    fun getTreeUri(ctx: Context): Uri? {
        val s = ctx.getSharedPreferences(P, Context.MODE_PRIVATE).getString(KEY_TREE, null)
        return s?.let { Uri.parse(it) }
    }

    fun setTreeUri(ctx: Context, uri: Uri) {
        ctx.getSharedPreferences(P, Context.MODE_PRIVATE)
            .edit().putString(KEY_TREE, uri.toString()).apply()
    }

    // UI preferences
    fun setKeepOnCharging(ctx: Context, v: Boolean) =
        ctx.getSharedPreferences(P, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_KEEP_ON_CHARGING, v).apply()

    fun keepOnCharging(ctx: Context) =
        ctx.getSharedPreferences(P, Context.MODE_PRIVATE)
            .getBoolean(KEY_KEEP_ON_CHARGING, true)

    fun setAutoHide(ctx: Context, v: Boolean) =
        ctx.getSharedPreferences(P, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_HIDE, v).apply()

    fun autoHide(ctx: Context) =
        ctx.getSharedPreferences(P, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_HIDE, true)

    // Country
    fun getDefaultCountry(ctx: Context): String {
        val saved = ctx.getSharedPreferences(P, Context.MODE_PRIVATE)
            .getString(KEY_COUNTRY, null)
        return saved ?: Locale.getDefault().getDisplayCountry(Locale.ENGLISH).ifBlank { "Italy" }
    }

    fun setDefaultCountry(ctx: Context, country: String) {
        ctx.getSharedPreferences(P, Context.MODE_PRIVATE)
            .edit().putString(KEY_COUNTRY, country).apply()
    }

    // Favorites
    fun getFavorites(ctx: Context): List<Favorite> {
        val json = ctx.getSharedPreferences(P, Context.MODE_PRIVATE)
            .getString(KEY_FAVORITES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<Favorite>>() {}.type
            gson.fromJson<List<Favorite>>(json, type).sortedBy { it.order }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun setFavorites(ctx: Context, favorites: List<Favorite>) {
        val json = gson.toJson(favorites)
        ctx.getSharedPreferences(P, Context.MODE_PRIVATE)
            .edit().putString(KEY_FAVORITES, json).apply()
    }

    // Buffer mode
    fun getBufferMode(ctx: Context): BufferMode {
        val ordinal = ctx.getSharedPreferences(P, Context.MODE_PRIVATE)
            .getInt(KEY_BUFFER_MODE, BufferMode.MEDIUM.ordinal)
        return BufferMode.values()[ordinal]
    }

    fun setBufferMode(ctx: Context, mode: BufferMode) {
        ctx.getSharedPreferences(P, Context.MODE_PRIVATE)
            .edit().putInt(KEY_BUFFER_MODE, mode.ordinal).apply()
    }

    // Cache for countries
    fun getCachedCountries(ctx: Context): List<Country>? {
        val prefs = ctx.getSharedPreferences(P, Context.MODE_PRIVATE)
        val time = prefs.getLong(KEY_COUNTRIES_CACHE_TIME, 0L)
        if (System.currentTimeMillis() - time > CACHE_DURATION) return null

        val json = prefs.getString(KEY_COUNTRIES_CACHE, null) ?: return null
        return try {
            val type = object : TypeToken<List<Country>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            null
        }
    }

    fun setCachedCountries(ctx: Context, countries: List<Country>) {
        val json = gson.toJson(countries)
        ctx.getSharedPreferences(P, Context.MODE_PRIVATE).edit()
            .putString(KEY_COUNTRIES_CACHE, json)
            .putLong(KEY_COUNTRIES_CACHE_TIME, System.currentTimeMillis())
            .apply()
    }

    // Cache for stations by country
    fun getCachedStations(ctx: Context, country: String): List<Station>? {
        val prefs = ctx.getSharedPreferences(P, Context.MODE_PRIVATE)
        val timeKey = KEY_STATIONS_CACHE_TIME_PREFIX + country
        val time = prefs.getLong(timeKey, 0L)
        if (System.currentTimeMillis() - time > CACHE_DURATION) return null

        val cacheKey = KEY_STATIONS_CACHE_PREFIX + country
        val json = prefs.getString(cacheKey, null) ?: return null
        return try {
            val type = object : TypeToken<List<Station>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            null
        }
    }

    fun setCachedStations(ctx: Context, country: String, stations: List<Station>) {
        val json = gson.toJson(stations)
        val cacheKey = KEY_STATIONS_CACHE_PREFIX + country
        val timeKey = KEY_STATIONS_CACHE_TIME_PREFIX + country
        ctx.getSharedPreferences(P, Context.MODE_PRIVATE).edit()
            .putString(cacheKey, json)
            .putLong(timeKey, System.currentTimeMillis())
            .apply()
    }

    // HomePage Buttons
    fun getHomePageButtons(ctx: Context): List<HomePageButton> {
        val json = ctx.getSharedPreferences(P, Context.MODE_PRIVATE)
            .getString(KEY_HOMEPAGE_BUTTONS, null) ?: return HomePageButton.getDefaultButtons()
        return try {
            val type = object : TypeToken<List<HomePageButton>>() {}.type
            val buttons = gson.fromJson<List<HomePageButton>>(json, type).toMutableList()

            // Ensure mini_player is always present and enabled
            val miniPlayerExists = buttons.any { it.id == "mini_player" }
            if (!miniPlayerExists) {
                // Add mini_player if missing
                buttons.add(0, HomePageButton.createMiniPlayer())
            } else {
                // Update mini_player to be enabled if it's disabled
                buttons.replaceAll { button ->
                    if (button.id == "mini_player" && !button.isEnabled) {
                        button.copy(isEnabled = true)
                    } else {
                        button
                    }
                }
            }

            buttons.sortedBy { it.order }
        } catch (e: Exception) {
            HomePageButton.getDefaultButtons()
        }
    }

    fun setHomePageButtons(ctx: Context, buttons: List<HomePageButton>) {
        val json = gson.toJson(buttons.sortedBy { it.order })
        ctx.getSharedPreferences(P, Context.MODE_PRIVATE)
            .edit().putString(KEY_HOMEPAGE_BUTTONS, json).apply()
    }
}
