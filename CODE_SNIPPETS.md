# GenPlayer Code Snippets & Examples

## 1. Loading Tracks from Database

### Get All Tracks (Reactive)
```kotlin
lifecycleScope.launch {
    val db = AppDatabase.getInstance(applicationContext)
    db.trackDao().getAllTracksDistinctFlow().collect { tracks ->
        displayItems(tracks.map { track ->
            BrowserItem.TrackItem(
                id = track.id.toString(),
                title = track.title ?: track.displayName,
                subtitle = "${track.artistName ?: "Unknown"} - ${track.albumTitle ?: "Unknown"}",
                coverArtUri = track.uri,
                track = track
            )
        })
    }
}
```

### Search Tracks
```kotlin
lifecycleScope.launch {
    val db = AppDatabase.getInstance(applicationContext)
    val results = withContext(Dispatchers.IO) {
        db.trackDao().searchTracks("%$query%")
    }
    displayItems(results)
}
```

### Get Tracks by Folder
```kotlin
val db = AppDatabase.getInstance(applicationContext)
db.trackDao().getTracksByFolderDistinctFlow(folderPath)
    .collect { tracks -> displayItems(tracks) }
```

---

## 2. Handling Folder Selection (SAF)

### Register Activity Contract
```kotlin
private val pickTree = registerForActivityResult(
    ActivityResultContracts.OpenDocumentTree()
) { uri: Uri? ->
    if (uri != null) {
        // Take persistent permission
        val flags = (Intent.FLAG_GRANT_READ_URI_PERMISSION
                or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        contentResolver.takePersistableUriPermission(uri, flags)
        
        // Save to Room Database
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(applicationContext)
            val root = DocumentFile.fromTreeUri(this@MyActivity, uri)
            db.safRootDao().insert(SafRoot(
                treeUri = uri.toString(),
                displayName = root?.name,
                takeFlags = flags
            ))
            
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MyActivity, 
                    "Folder added: ${root?.name}", 
                    Toast.LENGTH_SHORT).show()
            }
        }
    }
}

// Launch in a button click
btnAddFolder.setOnClickListener {
    pickTree.launch(null)
}
```

---

## 3. Creating BrowserItems

### From Folder
```kotlin
BrowserItem.FolderItem(
    id = folderPath,
    title = folderName,
    subtitle = "$trackCount tracce",
    coverArtUri = firstTrackUri,
    path = folderPath,
    level = folderPath.count { it == '/' },
    hasSubfolders = true,
    trackCount = trackCount,
    folderCount = subfolderCount
)
```

### From Album
```kotlin
BrowserItem.AlbumItem(
    id = albumTitle,
    title = albumTitle,
    subtitle = "$artistName • ${tracks.size} tracce",
    coverArtUri = firstTrackUri,
    albumId = albumIdRef,
    artistName = artistName,
    year = null,
    trackCount = tracks.size
)
```

### From Artist
```kotlin
BrowserItem.ArtistItem(
    id = artistName,
    title = artistName,
    subtitle = "$albumCount album • ${trackCount} tracce",
    coverArtUri = firstTrackUri,
    artistName = artistName,
    albumCount = albumCount,
    trackCount = trackCount
)
```

### From Track
```kotlin
BrowserItem.TrackItem(
    id = track.id.toString(),
    title = track.title ?: track.displayName,
    subtitle = "${track.artistName ?: "Unknown"} - ${track.albumTitle ?: "Unknown"}",
    coverArtUri = track.uri,
    track = track
)
```

---

## 4. Adapter Usage

### Submit List to Adapter
```kotlin
private val browserAdapter = MusicBrowserAdapter { item ->
    onItemClick(item)
}

// In onCreate
recyclerTracks.adapter = browserAdapter
recyclerTracks.layoutManager = LinearLayoutManager(this)

// When data is ready
browserAdapter.submitList(items)
```

### Handle Item Clicks
```kotlin
private fun onItemClick(item: BrowserItem) {
    when (item) {
        is BrowserItem.FolderItem -> {
            navigationStack.push(NavigationState(currentViewMode, currentPath))
            currentPath = item.path
            loadFoldersView()
        }
        is BrowserItem.AlbumItem -> {
            navigationStack.push(NavigationState(currentViewMode, currentAlbum = item.albumId))
            showAlbumTracks(item.title)
        }
        is BrowserItem.ArtistItem -> {
            navigationStack.push(NavigationState(currentViewMode, currentArtist = item.artistName))
            showArtistAlbums(item.artistName)
        }
        is BrowserItem.TrackItem -> {
            playTrack(item.track)
        }
    }
}
```

---

## 5. Trigger File Scanning

### Manual Scan (From Activity)
```kotlin
private fun triggerScan() {
    val req = OneTimeWorkRequestBuilder<ScanWorker>().build()
    WorkManager.getInstance(this).enqueue(req)
    Toast.makeText(this, "Scanning...", Toast.LENGTH_SHORT).show()
}
```

### Auto-Scan on Resume
```kotlin
private fun autoTriggerScan() {
    lifecycleScope.launch(Dispatchers.IO) {
        val db = AppDatabase.getInstance(applicationContext)
        val roots = db.safRootDao().getAll()
        val legacyUri = Prefs.getTreeUri(applicationContext)
        
        if (roots.isEmpty() && legacyUri == null) {
            Log.d("ScanTask", "No folders configured")
            return@launch
        }
        
        val now = System.currentTimeMillis()
        if (lastScanTime == 0L || (now - lastScanTime) > (6 * 60 * 60 * 1000L)) {
            withContext(Dispatchers.Main) {
                triggerScan()
            }
        }
    }
}
```

---

## 6. View Mode Switching

### Switch Between Views
```kotlin
private fun switchViewMode(newMode: ViewMode) {
    if (currentViewMode == newMode) return
    
    currentViewMode = newMode
    navigationStack.clear()
    currentPath = null
    
    updateFilterButtons()
    loadCurrentView()
}

private fun updateFilterButtons() {
    btnFolders.setBackgroundResource(R.drawable.button_filter_inactive)
    btnAlbums.setBackgroundResource(R.drawable.button_filter_inactive)
    btnArtists.setBackgroundResource(R.drawable.button_filter_inactive)
    btnAll.setBackgroundResource(R.drawable.button_filter_inactive)
    
    when (currentViewMode) {
        ViewMode.FOLDERS -> btnFolders.setBackgroundResource(R.drawable.button_filter_active)
        ViewMode.ALBUMS -> btnAlbums.setBackgroundResource(R.drawable.button_filter_active)
        ViewMode.ARTISTS -> btnArtists.setBackgroundResource(R.drawable.button_filter_active)
        ViewMode.ALL -> btnAll.setBackgroundResource(R.drawable.button_filter_active)
    }
}
```

### Load View Based on Mode
```kotlin
private fun loadCurrentView() {
    when (currentViewMode) {
        ViewMode.FOLDERS -> loadFoldersView()
        ViewMode.ALBUMS -> loadAlbumsView()
        ViewMode.ARTISTS -> loadArtistsView()
        ViewMode.ALL -> loadAllTracksView()
    }
}
```

---

## 7. Folder Hierarchy Building

### Build Immediate Subfolders Only
```kotlin
private fun buildFolderHierarchy(tracks: List<Track>, basePath: String?): List<BrowserItem> {
    val result = mutableListOf<BrowserItem>()
    
    val immediateSubfolders = mutableSetOf<String>()
    val directTracks = mutableListOf<Track>()
    
    tracks.forEach { track ->
        val trackPath = track.folderPathDisplay ?: return@forEach
        
        if (basePath != null) {
            if (trackPath != basePath && !trackPath.startsWith("$basePath/")) {
                return@forEach
            }
        }
        
        val relativePath = if (basePath != null) {
            trackPath.removePrefix(basePath).removePrefix("/")
        } else {
            trackPath
        }
        
        if (relativePath.isEmpty()) {
            directTracks.add(track)
        } else if (!relativePath.contains("/")) {
            val fullPath = if (basePath != null) "$basePath/$relativePath" else relativePath
            immediateSubfolders.add(fullPath)
        } else {
            val firstLevel = relativePath.split("/").first()
            val fullPath = if (basePath != null) "$basePath/$firstLevel" else firstLevel
            immediateSubfolders.add(fullPath)
        }
    }
    
    // Add folder items
    result.addAll(immediateSubfolders.map { folderPath ->
        BrowserItem.FolderItem(
            id = folderPath,
            title = folderPath.split("/").last(),
            subtitle = "${tracks.count { it.folderPathDisplay == folderPath }} tracce",
            coverArtUri = tracks.find { it.folderPathDisplay == folderPath }?.uri,
            path = folderPath,
            level = folderPath.count { it == '/' },
            hasSubfolders = false,
            trackCount = tracks.count { it.folderPathDisplay == folderPath },
            folderCount = 0
        )
    })
    
    return result
}
```

---

## 8. Back Navigation

### Back Stack Management
```kotlin
private val navigationStack = Stack<NavigationState>()

private fun handleBackPress() {
    if (navigationStack.isNotEmpty()) {
        val previousState = navigationStack.pop()
        currentPath = previousState.currentPath
        loadCurrentView()
    } else {
        finish()
    }
}

override fun onBackPressed() {
    handleBackPress()
    super.onBackPressed()
}
```

---

## 9. Empty State Handling

### Show/Hide Empty Message
```kotlin
private fun displayItems(items: List<BrowserItem>) {
    if (items.isEmpty()) {
        recyclerTracks.visibility = View.GONE
        txtEmpty.visibility = View.VISIBLE
        txtEmpty.text = "No tracks found"
    } else {
        recyclerTracks.visibility = View.VISIBLE
        txtEmpty.visibility = View.GONE
        browserAdapter.submitList(items)
    }
}
```

---

## 10. Preferences Management

### Save & Load Folder URI
```kotlin
// Legacy way (SharedPreferences)
fun saveFolderUri(context: Context, uri: Uri) {
    Prefs.setTreeUri(context, uri)
}

fun loadFolderUri(context: Context): Uri? {
    return Prefs.getTreeUri(context)
}

// Modern way (Room Database)
fun saveFolderToDatabase(context: Context, uri: Uri, displayName: String?) {
    lifecycleScope.launch(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        db.safRootDao().insert(SafRoot(
            treeUri = uri.toString(),
            displayName = displayName,
            takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        ))
    }
}

fun loadFoldersFromDatabase(context: Context) {
    lifecycleScope.launch(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        val roots = db.safRootDao().getAll()
        Log.d("FolderStorage", "Found ${roots.size} folders")
    }
}
```

---

## 11. Playing a Track

### Simple Play
```kotlin
private fun playTrack(track: Track) {
    lifecycleScope.launch {
        try {
            val sessionToken = androidx.media3.session.SessionToken(
                this@LocalMusicActivity,
                ComponentName(this@LocalMusicActivity, MusicPlayerService::class.java)
            )
            
            val controllerFuture = androidx.media3.session.MediaController.Builder(
                this@LocalMusicActivity, 
                sessionToken
            ).buildAsync()
            
            controllerFuture.addListener({
                lifecycleScope.launch {
                    val controller = controllerFuture.get()
                    
                    // Load queue
                    val queueManager = QueueManager(applicationContext)
                    queueManager.playTrackList(listOf(track), 0)
                    
                    val mediaItems = queueManager.getMediaItems()
                    controller.setMediaItems(mediaItems)
                    controller.prepare()
                    controller.play()
                    
                    startActivity(Intent(this@LocalMusicActivity, NowPlayingActivity::class.java))
                }
            }, MoreExecutors.directExecutor())
        } catch (e: Exception) {
            Log.e("PlayTrack", "Error", e)
            Toast.makeText(this@LocalMusicActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
```

---

## 12. Testing Queries

### Test Database Access
```kotlin
// In onCreate or a test method
lifecycleScope.launch(Dispatchers.IO) {
    val db = AppDatabase.getInstance(applicationContext)
    
    // Get all tracks
    val allTracks = db.trackDao().getAllTracks()
    Log.d("DbTest", "Total tracks: ${allTracks.size}")
    
    // Get folders
    val roots = db.safRootDao().getAll()
    Log.d("DbTest", "Total roots: ${roots.size}")
    roots.forEach { root ->
        Log.d("DbTest", "  - ${root.displayName}: ${root.treeUri}")
    }
    
    // Search
    val results = db.trackDao().searchTracks("%query%")
    Log.d("DbTest", "Search results: ${results.size}")
}
```
