package com.genaro.radiomp3.ui

import com.genaro.radiomp3.data.local.Track

/**
 * View modes for the music browser
 */
enum class ViewMode {
    FOLDERS,    // Browse by folder structure
    ALBUMS,     // Browse by albums
    ARTISTS,    // Browse by artists
    ALL,        // All tracks (flat list)
    SEARCH      // Search results mode
}

/**
 * Sealed class representing different types of browsable items
 */
sealed class BrowserItem {
    abstract val id: String
    abstract val title: String
    abstract val subtitle: String
    abstract val coverArtUri: String?

    /**
     * Folder item - represents a physical folder in the file system
     */
    data class FolderItem(
        override val id: String,           // Folder path
        override val title: String,         // Folder name
        override val subtitle: String,      // "N cartelle" or "N tracce"
        override val coverArtUri: String?,  // Cover from first track
        val path: String,                   // Full path
        val level: Int,                     // Depth level (0 = root)
        val hasSubfolders: Boolean,         // Has subfolders
        val trackCount: Int,                // Number of tracks
        val folderCount: Int                // Number of subfolders
    ) : BrowserItem()

    /**
     * Album item - represents a music album
     */
    data class AlbumItem(
        override val id: String,            // Album ID
        override val title: String,          // Album name
        override val subtitle: String,       // Artist name
        override val coverArtUri: String?,   // Album cover
        val albumId: Long,                   // Database album ID
        val artistName: String?,             // Artist
        val year: Int?,                      // Release year
        val trackCount: Int                  // Number of tracks
    ) : BrowserItem()

    /**
     * Artist item - represents a music artist
     */
    data class ArtistItem(
        override val id: String,            // Artist name (used as ID)
        override val title: String,          // Artist name
        override val subtitle: String,       // "N album • M tracce"
        override val coverArtUri: String?,   // Cover from first album
        val artistName: String,              // Artist name
        val albumCount: Int,                 // Number of albums
        val trackCount: Int                  // Total tracks
    ) : BrowserItem()

    /**
     * Track item - represents a single music file
     */
    data class TrackItem(
        override val id: String,            // Track ID
        override val title: String,          // Track title
        override val subtitle: String,       // Artist - Album
        override val coverArtUri: String?,   // Track cover
        val track: Track                     // Full track data
    ) : BrowserItem()
}

/**
 * Navigation state to support back navigation
 */
data class NavigationState(
    val viewMode: ViewMode,
    val currentPath: String? = null,      // For FOLDERS mode
    val currentArtist: String? = null,    // For ARTISTS mode
    val currentAlbum: Long? = null        // For ALBUMS mode when showing tracks
)
