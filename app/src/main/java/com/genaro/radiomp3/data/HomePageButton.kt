package com.genaro.radiomp3.data

/**
 * Rappresenta un bottone nella homepage
 * Può essere un bottone built-in (Web Radio, MP3, etc) o custom (free)
 */
data class HomePageButton(
    val id: String,                  // "web_radio", "mp3", "youtube", "spotify", "vu_meter", "custom_1", etc
    val name: String,                // "Web Radio", "MP3 Player", "Custom App", etc
    val emoji: String,               // "📻", "🎵", etc
    val color: String,               // "#FFD700", "#03DAC5", etc (hex color)
    val link: String?,               // Package name o URL (null per built-in)
    val order: Int,                  // Posizione nella lista (per drag-to-reorder)
    val type: ButtonType,            // BUILT_IN, CUSTOM, VU_METER
    val isEnabled: Boolean = true    // Se visibile nella homepage
) {
    enum class ButtonType {
        BUILT_IN,   // Web Radio, MP3, YouTube, Spotify
        CUSTOM,     // Custom free button
        VU_METER    // VU Meter widget (doppia altezza)
    }

    companion object {
        // Factory per creare i bottoni default
        fun createWebRadio() = HomePageButton(
            id = "web_radio",
            name = "Web Radio",
            emoji = "📻",
            color = "#FFD700",
            link = null,
            order = 0,
            type = ButtonType.BUILT_IN,
            isEnabled = true
        )

        fun createMP3Player() = HomePageButton(
            id = "mp3",
            name = "MP3 Player",
            emoji = "🎵",
            color = "#03DAC5",
            link = null,
            order = 1,
            type = ButtonType.BUILT_IN,
            isEnabled = true
        )

        fun createYouTube() = HomePageButton(
            id = "youtube",
            name = "YouTube",
            emoji = "🎬",
            color = "#FF3B30",
            link = "com.google.android.youtube",
            order = 2,
            type = ButtonType.BUILT_IN,
            isEnabled = true
        )

        fun createSpotify() = HomePageButton(
            id = "spotify",
            name = "Spotify",
            emoji = "🎧",
            color = "#1DB954",
            link = "com.spotify.music",
            order = 3,
            type = ButtonType.BUILT_IN,
            isEnabled = true
        )

        fun createVUMeter() = HomePageButton(
            id = "vu_meter",
            name = "VU Meter",
            emoji = "📊",
            color = "#9C27B0",
            link = null,
            order = 4,
            type = ButtonType.VU_METER,
            isEnabled = true
        )

        fun getDefaultButtons(): List<HomePageButton> = listOf(
            createWebRadio(),
            createMP3Player(),
            createYouTube(),
            createSpotify(),
            createVUMeter()
        )
    }
}
