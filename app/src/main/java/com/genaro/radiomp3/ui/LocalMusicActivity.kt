package com.genaro.radiomp3.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import android.app.AlertDialog
import com.genaro.radiomp3.R
import com.genaro.radiomp3.data.local.AppDatabase
import com.genaro.radiomp3.data.local.Track
import com.genaro.radiomp3.scanner.ScanWorker
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import java.util.Stack

class LocalMusicActivity : BaseActivity() {

    companion object {
        private const val COLOR_ACTIVE_TEXT = "#FFFFFF"
        private const val COLOR_INACTIVE_TEXT = "#888888"
    }

    private lateinit var btnAddFolder: ImageButton
    private lateinit var recyclerTracks: RecyclerView
    private lateinit var txtEmpty: TextView
    private lateinit var txtBreadcrumb: TextView
    private lateinit var browserAdapter: MusicBrowserAdapter

    // Filter buttons
    private lateinit var btnFolders: Button
    private lateinit var btnAlbums: Button
    private lateinit var btnArtists: Button
    private lateinit var btnAll: Button

    // Cached text colors
    private val activeTextColor by lazy { android.graphics.Color.parseColor(COLOR_ACTIVE_TEXT) }
    private val inactiveTextColor by lazy { android.graphics.Color.parseColor(COLOR_INACTIVE_TEXT) }

    // Current view mode and navigation
    private var currentViewMode = ViewMode.FOLDERS
    private val navigationStack = Stack<NavigationState>()
    private var currentPath: String? = null  // For folder navigation

    // Current tracks list (for queue management)
    private var currentTracks = listOf<Track>()

    // Scan timing
    private var lastScanTime = 0L
    private val MIN_SCAN_INTERVAL = 6 * 60 * 60 * 1000L  // 6 hours

    // Manage Flow collection jobs
    private var currentCollectionJob: Job? = null

    private val requestAudioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Toast.makeText(this@LocalMusicActivity, "Permesso audio concesso", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this@LocalMusicActivity, "Permesso negato - alcune funzionalità potrebbero non funzionare", Toast.LENGTH_LONG).show()
        }
    }

    private val pickTree = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val flags = (Intent.FLAG_GRANT_READ_URI_PERMISSION
                    or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            contentResolver.takePersistableUriPermission(uri, flags)

            val root = DocumentFile.fromTreeUri(this, uri)

            // Salva nel database
            lifecycleScope.launch(Dispatchers.IO) {
                val db = AppDatabase.getInstance(applicationContext)
                val safRoot = com.genaro.radiomp3.data.local.SafRoot(
                    treeUri = uri.toString(),
                    displayName = root?.name,
                    takeFlags = flags
                )
                db.safRootDao().insert(safRoot)

                android.util.Log.d("LocalMusicActivity", "Saved folder to database: ${root?.name} - URI: $uri")

                // Verify it was saved
                val allRoots = db.safRootDao().getAll()
                android.util.Log.d("LocalMusicActivity", "Total folders in database: ${allRoots.size}")

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@LocalMusicActivity,
                        "Cartella aggiunta: ${root?.name}\nTotale cartelle: ${allRoots.size}",
                        Toast.LENGTH_LONG).show()
                    triggerScan()
                }
            }
        } else {
            Toast.makeText(this@LocalMusicActivity, "Nessuna cartella selezionata", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("LocalMusicActivity", "onCreate started")

        try {
            setContentView(R.layout.activity_local_music)
            android.util.Log.d("LocalMusicActivity", "Layout inflated")

            // Setup immersive mode with double-tap
            val tapArea = findViewById<View>(R.id.tapArea)
            setupImmersiveMode(tapArea)

            val btnBack = findViewById<ImageButton>(R.id.btnBack)
            btnAddFolder = findViewById(R.id.btnAddFolder)
            val fabScan = findViewById<FloatingActionButton>(R.id.fabScan)
            recyclerTracks = findViewById(R.id.recyclerTracks)
            txtEmpty = findViewById(R.id.txtEmpty)
            txtBreadcrumb = findViewById(R.id.txtBreadcrumb)
            android.util.Log.d("LocalMusicActivity", "Basic views found")

            // Initialize filter buttons
            btnFolders = findViewById(R.id.btnFolders)
            btnAlbums = findViewById(R.id.btnAlbums)
            btnArtists = findViewById(R.id.btnArtists)
            btnAll = findViewById(R.id.btnAll)
            android.util.Log.d("LocalMusicActivity", "Filter buttons found")

        // Setup RecyclerView with universal adapter
        browserAdapter = MusicBrowserAdapter { item ->
            onItemClick(item)
        }
        recyclerTracks.layoutManager = LinearLayoutManager(this)
        recyclerTracks.adapter = browserAdapter

        // Setup button listeners
        btnFolders.setOnClickListener { switchViewMode(ViewMode.FOLDERS) }
        btnAlbums.setOnClickListener { switchViewMode(ViewMode.ALBUMS) }
        btnArtists.setOnClickListener { switchViewMode(ViewMode.ARTISTS) }
        btnAll.setOnClickListener { switchViewMode(ViewMode.ALL) }

        btnBack.setOnClickListener { handleBackPress() }
        btnAddFolder.setOnClickListener { openFolderPicker() }
        fabScan.setOnClickListener { triggerScan() }

            // Auto-request permission if not granted (only first time)
            if (!hasAudioPermission()) {
                requestAudioPermission.launch(getAudioPermission())
            }
            android.util.Log.d("LocalMusicActivity", "Permission checked")

            // Load initial view (from cache/DB with Flow)
            android.util.Log.d("LocalMusicActivity", "About to load current view")
            loadCurrentView()

            // Check loading strategy and warn user if necessary
            checkLoadingStrategy()

            // Auto-trigger scan in background if needed
            autoTriggerScan()

            android.util.Log.d("LocalMusicActivity", "onCreate completed successfully")

        } catch (e: Exception) {
            android.util.Log.e("LocalMusicActivity", "CRASH in onCreate", e)
            Toast.makeText(this, "Errore: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        // Reload current view when returning to this activity
        loadCurrentView()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancel any pending collection job
        currentCollectionJob?.cancel()
        android.util.Log.d("LocalMusicActivity", "Activity destroyed, collection job cancelled")
    }

    // ==== VIEW MODE SWITCHING ====

    private fun switchViewMode(newMode: ViewMode) {
        if (currentViewMode == newMode) return

        currentViewMode = newMode
        navigationStack.clear()
        currentPath = null

        // Update button colors
        updateFilterButtons()

        loadCurrentView()
    }

    private fun updateFilterButtons() {
        // Reset all buttons to inactive state
        btnFolders.setBackgroundResource(R.drawable.button_filter_inactive)
        btnFolders.setTextColor(inactiveTextColor)

        btnAlbums.setBackgroundResource(R.drawable.button_filter_inactive)
        btnAlbums.setTextColor(inactiveTextColor)

        btnArtists.setBackgroundResource(R.drawable.button_filter_inactive)
        btnArtists.setTextColor(inactiveTextColor)

        btnAll.setBackgroundResource(R.drawable.button_filter_inactive)
        btnAll.setTextColor(inactiveTextColor)

        // Set active button with modern purple color
        when (currentViewMode) {
            ViewMode.FOLDERS -> {
                btnFolders.setBackgroundResource(R.drawable.button_filter_active)
                btnFolders.setTextColor(activeTextColor)
            }
            ViewMode.ALBUMS -> {
                btnAlbums.setBackgroundResource(R.drawable.button_filter_active)
                btnAlbums.setTextColor(activeTextColor)
            }
            ViewMode.ARTISTS -> {
                btnArtists.setBackgroundResource(R.drawable.button_filter_active)
                btnArtists.setTextColor(activeTextColor)
            }
            ViewMode.ALL -> {
                btnAll.setBackgroundResource(R.drawable.button_filter_active)
                btnAll.setTextColor(activeTextColor)
            }
        }
    }

    private fun loadCurrentView() {
        try {
            android.util.Log.d("LocalMusicActivity", "loadCurrentView: mode=$currentViewMode, currentPath=$currentPath")

            // Update breadcrumb display
            updateBreadcrumb()

            // Cancel any existing collection job
            currentCollectionJob?.cancel()

            when (currentViewMode) {
                ViewMode.FOLDERS -> {
                    android.util.Log.d("LocalMusicActivity", "Loading FOLDERS view")
                    loadFoldersView()
                }
                ViewMode.ALBUMS -> {
                    android.util.Log.d("LocalMusicActivity", "Loading ALBUMS view")
                    loadAlbumsView()
                }
                ViewMode.ARTISTS -> {
                    android.util.Log.d("LocalMusicActivity", "Loading ARTISTS view")
                    loadArtistsView()
                }
                ViewMode.ALL -> {
                    android.util.Log.d("LocalMusicActivity", "Loading ALL view")
                    loadAllTracksView()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("LocalMusicActivity", "CRASH in loadCurrentView", e)
            Toast.makeText(this, "Errore caricamento: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateBreadcrumb() {
        val text = when {
            currentViewMode == ViewMode.FOLDERS && currentPath != null -> {
                // Show folder path with hierarchy
                val parts = currentPath!!.split("/").filter { it.isNotEmpty() }
                if (parts.isEmpty()) {
                    "📁 Root"
                } else {
                    "📁 " + parts.joinToString(" / ")
                }
            }
            currentViewMode == ViewMode.ALBUMS -> "💿 Album"
            currentViewMode == ViewMode.ARTISTS -> "🎤 Artisti"
            currentViewMode == ViewMode.ALL -> "🎵 Tutti"
            else -> "📁 Root"
        }
        txtBreadcrumb.text = text
        android.util.Log.d("LocalMusicActivity", "Breadcrumb updated: $text")
    }

    // ==== FOLDER VIEW ====

    private fun loadFoldersView() {
        currentCollectionJob = lifecycleScope.launch {
            try {
                val db = AppDatabase.getInstance(applicationContext)
                db.trackDao().getAllTracksFlow().collect { tracks ->
                    try {
                        if (tracks.isEmpty()) {
                            displayItems(emptyList())
                        } else {
                            val items = buildFolderHierarchy(tracks, currentPath)
                            displayItems(items)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("LocalMusicActivity", "Error in folder collection", e)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("LocalMusicActivity", "Error loading folders view", e)
            }
        }
    }

    private fun buildFolderHierarchy(tracks: List<Track>, basePath: String?): List<BrowserItem> {
        val result = mutableListOf<BrowserItem>()

        // Helper: extract immediate child folders at current path level
        val immediateSubfolders = mutableSetOf<String>()

        // Helper: extract direct files in current path (not in subfolders)
        val directTracksInCurrentPath = mutableListOf<Track>()

        android.util.Log.d("LocalMusicActivity", "buildFolderHierarchy: basePath='$basePath', total tracks=${tracks.size}")

        // Filter and categorize tracks
        tracks.forEach { track ->
            val trackPath = track.folderPathDisplay ?: return@forEach

            // If basePath is set, only include tracks under this path
            if (basePath != null) {
                if (trackPath != basePath && !trackPath.startsWith("$basePath/")) {
                    return@forEach
                }
            }

            // Calculate relative path from basePath
            val relativePath = if (basePath != null) {
                trackPath.removePrefix(basePath).removePrefix("/")
            } else {
                trackPath
            }

            if (relativePath.isEmpty()) {
                // Track is directly in current path (no relative path)
                directTracksInCurrentPath.add(track)
            } else if (!relativePath.contains("/")) {
                // Track is in immediate subfolder (one level down, direct child)
                // Extract just the immediate folder name
                val fullSubfolderPath = if (basePath != null) {
                    "$basePath/$relativePath"
                } else {
                    relativePath
                }
                immediateSubfolders.add(fullSubfolderPath)
            } else {
                // Track is in deeper subfolder (multiple levels down)
                // Extract only the immediate child folder
                val firstLevelFolder = relativePath.split("/").first()
                val fullSubfolderPath = if (basePath != null) {
                    "$basePath/$firstLevelFolder"
                } else {
                    firstLevelFolder
                }
                immediateSubfolders.add(fullSubfolderPath)
            }
        }

        android.util.Log.d("LocalMusicActivity", "  Found ${immediateSubfolders.size} immediate subfolders and ${directTracksInCurrentPath.size} direct tracks")

        // Add folder items for immediate subfolders only
        val folderItems = immediateSubfolders.map { folderPath ->
            // Count direct children (files directly in this folder)
            val directFilesInFolder = tracks.count { track ->
                val trackPath = track.folderPathDisplay ?: ""
                val relativePath = trackPath.removePrefix(folderPath).removePrefix("/")
                trackPath == folderPath || (trackPath.startsWith("$folderPath/") && !relativePath.contains("/"))
            }

            // Check if this folder has subfolders
            val hasSubfolders = tracks.any { track ->
                val trackPath = track.folderPathDisplay ?: ""
                trackPath.startsWith("$folderPath/") && {
                    val relPath = trackPath.removePrefix(folderPath).removePrefix("/")
                    relPath.contains("/")
                }()
            }

            val folderName = folderPath.split("/").last()

            BrowserItem.FolderItem(
                id = folderPath,
                title = folderName,
                subtitle = when {
                    hasSubfolders && directFilesInFolder > 0 -> "$directFilesInFolder tracce + cartelle"
                    hasSubfolders -> "cartelle"
                    else -> "$directFilesInFolder tracce"
                },
                coverArtUri = tracks.find { it.folderPathDisplay == folderPath }?.uri,
                path = folderPath,
                level = folderPath.count { it == '/' },
                hasSubfolders = hasSubfolders,
                trackCount = directFilesInFolder,
                folderCount = immediateSubfolders.count { it.startsWith("$folderPath/") && it != folderPath }
            )
        }.sortedBy { it.title.lowercase() }

        result.addAll(folderItems)

        // Add track items for files directly in current path only
        val trackItems = directTracksInCurrentPath.map { track ->
            BrowserItem.TrackItem(
                id = track.id.toString(),
                title = track.title ?: track.displayName,
                subtitle = "${track.artistName ?: "Unknown"} - ${track.albumTitle ?: "Unknown"}",
                coverArtUri = track.uri,
                track = track
            )
        }.sortedBy { it.title.lowercase() }

        result.addAll(trackItems)

        android.util.Log.d("LocalMusicActivity", "  Result: ${folderItems.size} folders + ${trackItems.size} tracks = ${result.size} items")

        return result
    }

    // ==== ALBUM VIEW ====

    private fun loadAlbumsView() {
        currentCollectionJob = lifecycleScope.launch {
            try {
                val db = AppDatabase.getInstance(applicationContext)
                db.trackDao().getAllTracksFlow().collect { tracks ->
                    try {
                        val result = mutableListOf<BrowserItem>()

                        if (tracks.isNotEmpty()) {
                            // Separate orphaned tracks from proper albums
                            val (orphanedTracks, albumTracks) = tracks.partition {
                                it.albumTitle == null || it.albumTitle == "Unknown Album"
                            }

                            // Add proper albums
                            result.addAll(
                                albumTracks.groupBy { it.albumTitle!! }
                                    .map { (albumTitle, albumTracks) ->
                                        BrowserItem.AlbumItem(
                                            id = albumTitle,
                                            title = albumTitle,
                                            subtitle = "${albumTracks.firstOrNull()?.artistName ?: "Unknown"} • ${albumTracks.size} tracce",
                                            coverArtUri = albumTracks.firstOrNull()?.uri,
                                            albumId = albumTracks.firstOrNull()?.albumIdRef ?: 0,
                                            artistName = albumTracks.firstOrNull()?.artistName,
                                            year = null,
                                            trackCount = albumTracks.size
                                        )
                                    }
                            )

                            // Add orphaned tracks as individual items with filename as title
                            result.addAll(
                                orphanedTracks.map { track ->
                                    BrowserItem.TrackItem(
                                        id = track.id.toString(),
                                        title = track.displayName, // Use filename
                                        subtitle = track.artistName ?: "Unknown Artist",
                                        coverArtUri = track.uri,
                                        track = track
                                    )
                                }
                            )

                            result.sortBy { it.title.lowercase() }
                        }

                        displayItems(result)
                    } catch (e: Exception) {
                        android.util.Log.e("LocalMusicActivity", "Error in album collection", e)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("LocalMusicActivity", "Error loading albums view", e)
            }
        }
    }

    // ==== ARTIST VIEW ====

    private fun loadArtistsView() {
        currentCollectionJob = lifecycleScope.launch {
            try {
                val db = AppDatabase.getInstance(applicationContext)
                db.trackDao().getAllTracksFlow().collect { tracks ->
                    try {
                        val items = if (tracks.isEmpty()) {
                            emptyList()
                        } else {
                            tracks.groupBy { it.artistName ?: "Unknown Artist" }
                                .map { (artistName, artistTracks) ->
                                    val albumCount = artistTracks.mapNotNull { it.albumTitle }.distinct().size

                                    BrowserItem.ArtistItem(
                                        id = artistName,
                                        title = artistName,
                                        subtitle = "$albumCount album • ${artistTracks.size} tracce",
                                        coverArtUri = artistTracks.firstOrNull()?.uri,
                                        artistName = artistName,
                                        albumCount = albumCount,
                                        trackCount = artistTracks.size
                                    )
                                }
                                .sortedBy { it.title.lowercase() }
                        }

                        displayItems(items)
                    } catch (e: Exception) {
                        android.util.Log.e("LocalMusicActivity", "Error in artist collection", e)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("LocalMusicActivity", "Error loading artists view", e)
            }
        }
    }

    // ==== ALL TRACKS VIEW ====

    private fun loadAllTracksView() {
        currentCollectionJob = lifecycleScope.launch {
            try {
                val db = AppDatabase.getInstance(applicationContext)
                db.trackDao().getAllTracksOrderedFlow().collect { tracks ->
                    try {
                        val items = tracks.map { track ->
                            BrowserItem.TrackItem(
                                id = track.id.toString(),
                                title = track.title ?: track.displayName,
                                subtitle = "${track.artistName ?: "Unknown"} - ${track.albumTitle ?: "Unknown"}",
                                coverArtUri = track.uri,
                                track = track
                            )
                        }

                        displayItems(items)
                    } catch (e: Exception) {
                        android.util.Log.e("LocalMusicActivity", "Error in all tracks collection", e)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("LocalMusicActivity", "Error loading all tracks view", e)
            }
        }
    }

    // ==== DISPLAY & NAVIGATION ====

    private fun displayItems(items: List<BrowserItem>) {
        if (items.isEmpty()) {
            recyclerTracks.visibility = View.GONE
            txtEmpty.visibility = View.VISIBLE
        } else {
            recyclerTracks.visibility = View.VISIBLE
            txtEmpty.visibility = View.GONE
            browserAdapter.submitList(items)
        }
    }

    private fun onItemClick(item: BrowserItem) {
        when (item) {
            is BrowserItem.FolderItem -> {
                // Click on folder: navigate into folder (open the folder)
                navigationStack.push(NavigationState(currentViewMode, currentPath))
                currentPath = item.path
                loadFoldersView()
            }
            is BrowserItem.AlbumItem -> {
                // Click on album: show tracks in this album
                navigationStack.push(NavigationState(currentViewMode, currentAlbum = item.albumId))
                showAlbumTracks(item.title)
            }
            is BrowserItem.ArtistItem -> {
                // Click on artist: show albums by this artist
                navigationStack.push(NavigationState(currentViewMode, currentArtist = item.artistName))
                showArtistAlbums(item.artistName)
            }
            is BrowserItem.TrackItem -> {
                // Click on track: play it
                playTrack(item.track)
            }
        }
    }

    private fun showFolderTracks(folderPath: String) {
        lifecycleScope.launch {
            android.util.Log.d("LocalMusicActivity", "showFolderTracks: looking for folderPath='$folderPath'")
            val (items, tracks) = withContext(Dispatchers.IO) {
                val db = AppDatabase.getInstance(applicationContext)
                val allTracks = db.trackDao().getAllTracks()
                android.util.Log.d("LocalMusicActivity", "Total tracks in DB: ${allTracks.size}")

                // Log all unique folder paths for debugging
                val uniquePaths = allTracks.map { it.folderPathDisplay }.distinct()
                android.util.Log.d("LocalMusicActivity", "Unique folder paths in DB: ${uniquePaths.size}")
                uniquePaths.forEach { path ->
                    android.util.Log.d("LocalMusicActivity", "  - '$path'")
                }

                val filteredTracks = allTracks
                    .filter { track ->
                        val trackPath = track.folderPathDisplay ?: ""
                        // Try exact match first, then try startsWith for parent folders
                        trackPath == folderPath || trackPath.startsWith("$folderPath/")
                    }
                    .sortedBy { (it.title ?: it.displayName).lowercase() }

                android.util.Log.d("LocalMusicActivity", "Filtered tracks for '$folderPath': ${filteredTracks.size}")

                val items = filteredTracks.map { track ->
                    BrowserItem.TrackItem(
                        id = track.id.toString(),
                        title = track.title ?: track.displayName,
                        subtitle = "${track.artistName ?: "Unknown"} - ${track.albumTitle ?: "Unknown"}",
                        coverArtUri = track.uri,
                        track = track
                    )
                }

                Pair(items, filteredTracks)
            }

            currentTracks = tracks
            displayItems(items)
        }
    }

    private fun showAlbumTracks(albumTitle: String) {
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                val db = AppDatabase.getInstance(applicationContext)
                db.trackDao().getAllTracks()
                    .filter { it.albumTitle == albumTitle }
                    .map { track ->
                        BrowserItem.TrackItem(
                            id = track.id.toString(),
                            title = track.title ?: track.displayName,
                            subtitle = track.artistName ?: "Unknown",
                            coverArtUri = track.uri,
                            track = track
                        )
                    }
                    .sortedBy { it.title.lowercase() }
            }
            displayItems(items)
        }
    }

    private fun showArtistAlbums(artistName: String) {
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                val db = AppDatabase.getInstance(applicationContext)
                val tracks = db.trackDao().getAllTracks()
                    .filter { it.artistName == artistName }

                tracks.groupBy { it.albumTitle ?: "Unknown Album" }
                    .map { (albumTitle, albumTracks) ->
                        BrowserItem.AlbumItem(
                            id = albumTitle,
                            title = albumTitle,
                            subtitle = "${albumTracks.size} tracce",
                            coverArtUri = albumTracks.firstOrNull()?.uri,
                            albumId = albumTracks.firstOrNull()?.albumIdRef ?: 0,
                            artistName = artistName,
                            year = null,
                            trackCount = albumTracks.size
                        )
                    }
                    .sortedBy { it.title.lowercase() }
            }
            displayItems(items)
        }
    }

    private fun handleBackPress() {
        if (navigationStack.isNotEmpty()) {
            val previousState = navigationStack.pop()
            currentPath = previousState.currentPath
            loadCurrentView()
        } else {
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        handleBackPress()
        super.onBackPressed()
    }

    private fun playTrack(track: Track) {
        lifecycleScope.launch {
            try {
                // Connect to player service
                val sessionToken = androidx.media3.session.SessionToken(
                    this@LocalMusicActivity,
                    android.content.ComponentName(this@LocalMusicActivity, com.genaro.radiomp3.playback.MusicPlayerService::class.java)
                )

                val controllerFuture = androidx.media3.session.MediaController.Builder(this@LocalMusicActivity, sessionToken).buildAsync()
                controllerFuture.addListener({
                    lifecycleScope.launch {
                        try {
                            val controller = controllerFuture.get()

                            // Check if this track is already playing
                            val currentMediaId = controller.currentMediaItem?.mediaId
                            val trackMediaId = track.id.toString()

                            if (currentMediaId == trackMediaId) {
                                // Same track - just open Now Playing screen without restarting
                                android.util.Log.d("LocalMusicActivity", "Track already loaded, just opening player")
                                startActivity(Intent(this@LocalMusicActivity, NowPlayingActivity::class.java))
                            } else {
                                // Different track - load and play
                                android.util.Log.d("LocalMusicActivity", "Loading new track: ${track.displayName}")

                                // Load the entire folder/context of tracks
                                val queueManager = com.genaro.radiomp3.playback.QueueManager(applicationContext)

                                // Get all tracks from the current context (folder, album, artist, or all)
                                val contextTracks = withContext(Dispatchers.IO) {
                                    val db = AppDatabase.getInstance(applicationContext)

                                    // Get tracks based on current view mode and context
                                    when {
                                        currentPath != null && currentViewMode == ViewMode.FOLDERS -> {
                                            // Playing from folder view
                                            db.trackDao().getAllTracks()
                                                .filter { it.folderPathDisplay == currentPath }
                                                .sortedBy { (it.title ?: it.displayName).lowercase() }
                                        }
                                        else -> {
                                            // Playing from other views - use all visible tracks
                                            db.trackDao().getAllTracks()
                                                .sortedBy { (it.title ?: it.displayName).lowercase() }
                                        }
                                    }
                                }

                                // Find the index of the clicked track
                                val startIndex = contextTracks.indexOfFirst { it.id == track.id }
                                    .takeIf { it >= 0 } ?: 0

                                // Load the playlist starting from the selected track
                                queueManager.playTrackList(contextTracks, startIndex)

                                val mediaItems = queueManager.getMediaItems()

                                // Set playlist and play
                                controller.setMediaItems(mediaItems)
                                controller.seekToDefaultPosition(startIndex)
                                controller.prepare()
                                controller.play()

                                // Open Now Playing screen
                                startActivity(Intent(this@LocalMusicActivity, NowPlayingActivity::class.java))
                            }

                            // Release controller
                            androidx.media3.session.MediaController.releaseFuture(controllerFuture)
                        } catch (e: Exception) {
                            android.util.Log.e("LocalMusicActivity", "Error starting playback", e)
                            Toast.makeText(this@LocalMusicActivity, "Errore riproduzione: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }, com.google.common.util.concurrent.MoreExecutors.directExecutor())

            } catch (e: Exception) {
                android.util.Log.e("LocalMusicActivity", "Error preparing playback", e)
                Toast.makeText(this@LocalMusicActivity, "Errore: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun hasAudioPermission(): Boolean {
        val permission = getAudioPermission()
        return androidx.core.content.ContextCompat.checkSelfPermission(
            this, permission
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun getAudioPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE
    }

    private fun openFolderPicker() {
        pickTree.launch(null)
    }

    private fun triggerScan() {
        android.util.Log.d("LocalMusicActivity", "Starting scan...")
        lastScanTime = System.currentTimeMillis()

        val req = OneTimeWorkRequestBuilder<ScanWorker>().build()
        WorkManager.getInstance(this).enqueue(req)
        Toast.makeText(this@LocalMusicActivity, "Scansione avviata…", Toast.LENGTH_SHORT).show()

        // UI will update automatically via Flow from Room DB
        // No need to manually reload
    }

    private fun autoTriggerScan() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getInstance(applicationContext)
                val roots = db.safRootDao().getAll()

                // Also check legacy SharedPreferences
                val legacyUri = com.genaro.radiomp3.data.Prefs.getTreeUri(applicationContext)

                // Only auto-scan if folders are configured (either in Room or legacy)
                if (roots.isEmpty() && legacyUri == null) {
                    android.util.Log.d("LocalMusicActivity", "No folders configured, skipping auto-scan")
                    return@launch
                }

                android.util.Log.d("LocalMusicActivity", "Auto-scan triggered - folders found (Room: ${roots.size}, Legacy: ${if (legacyUri != null) "YES" else "NO"})")

                // Check if enough time has passed since last scan
                val now = System.currentTimeMillis()
                if (lastScanTime == 0L || (now - lastScanTime) > MIN_SCAN_INTERVAL) {
                    android.util.Log.d("LocalMusicActivity", "Auto-triggering differential scan...")
                    withContext(Dispatchers.Main) {
                        triggerScan()
                    }
                } else {
                    android.util.Log.d("LocalMusicActivity", "Scan recently completed, skipping auto-scan")
                }
            } catch (e: Exception) {
                android.util.Log.e("LocalMusicActivity", "Error in autoTriggerScan", e)
            }
        }
    }

    private fun checkLoadingStrategy() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getInstance(applicationContext)
                val fileCount = db.trackDao().getAllTracks().size

                val config = LoadingStrategyDecider.decideStrategy(fileCount)

                android.util.Log.d("LocalMusicActivity",
                    "Loading strategy detected: ${config.strategy} (fileCount=$fileCount, pageSize=${config.pageSize})")

                // Show warning if necessary
                if (config.shouldWarnUser) {
                    withContext(Dispatchers.Main) {
                        showLoadingWarning(config)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("LocalMusicActivity", "Error in checkLoadingStrategy", e)
            }
        }
    }

    private fun showLoadingWarning(config: LoadingStrategyDecider.LoadingConfig) {
        try {
            // Only show warning for PAGINATION_STRICT (> 10k files)
            if (config.strategy != LoadingStrategyDecider.LoadingStrategy.PAGINATION_STRICT) {
                return
            }

            AlertDialog.Builder(this)
                .setTitle("⚠️ Libreria molto grande")
                .setMessage(config.warningMessage)
                .setPositiveButton("OK, Procediamo") { _, _ ->
                    android.util.Log.d("LocalMusicActivity", "User confirmed loading large library")
                }
                .setNegativeButton("Torna indietro") { _, _ ->
                    android.util.Log.d("LocalMusicActivity", "User cancelled - library too large")
                    finish()
                }
                .setCancelable(false)
                .show()
        } catch (e: Exception) {
            android.util.Log.e("LocalMusicActivity", "Error showing loading warning", e)
        }
    }
}