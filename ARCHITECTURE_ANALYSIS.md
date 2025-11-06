# GenPlayer Android Music Player - Project Architecture Analysis

## Overview
GenPlayer is a sophisticated Android music player application with support for both internet radio streaming and local music file browsing. The application uses Kotlin with modern Android Architecture Components (Room, LiveData/Flow, WorkManager).

---

## 1. MP3 Player UI/Activities Location

### Primary Activities:

| Activity | File Path | Purpose |
|----------|-----------|---------|
| **LocalMusicActivity** | `/app/src/main/java/com/genaro/radiomp3/ui/LocalMusicActivity.kt` | Main local music browser with folder/album/artist/all tracks views |
| **FileBrowserActivity** | `/app/src/main/java/com/genaro/radiomp3/ui/FileBrowserActivity.kt` | Simple file browser for direct file selection |
| **NowPlayingActivity** | `/app/src/main/java/com/genaro/radiomp3/ui/NowPlayingActivity.kt` | Music playback display with player controls |
| **SettingsActivity** | `/app/src/main/java/com/genaro/radiomp3/ui/SettingsActivity.kt` | Settings including home folder selection |
| **RadioPlayerActivity** | `/app/src/main/java/com/genaro/radiomp3/ui/RadioPlayerActivity.kt` | Internet radio streaming player |
| **MainActivity** | `/app/src/main/java/com/genaro/radiomp3/ui/MainActivity.kt` | Main home/dashboard activity |

---

## 2. File Browsing/Selection Implementation

### Two Parallel Systems:

#### A. **Simple File Browser (FileBrowserActivity)**
- **Location**: `/app/src/main/java/com/genaro/radiomp3/ui/FileBrowserActivity.kt`
- **Architecture**: Single Activity with embedded adapter
- **Features**:
  - Uses SAF (Storage Access Framework) with DocumentFile API
  - Navigates folder hierarchy using a Stack-based navigation
  - Filters audio files by extension: `.mp3|.flac|.wav|.m4a|.aac|.ogg|.opus`
  - Sorts folders first (alphabetically), then files (alphabetically)
  - Shows folder/file icons based on type
  - Plays files directly when selected

**Code Flow**:
```kotlin
navigateToDirectory(DocumentFile) 
  → listFiles() 
  → filter by isDirectory or audio extensions 
  → sort folders first 
  → submit to adapter
```

#### B. **Advanced Music Browser (LocalMusicActivity)**
- **Location**: `/app/src/main/java/com/genaro/radiomp3/ui/LocalMusicActivity.kt`
- **Architecture**: Database-driven with reactive Flow updates
- **Features**:
  - Multiple view modes (FOLDERS, ALBUMS, ARTISTS, ALL)
  - Folder picker using SAF (ActivityResultContracts.OpenDocumentTree)
  - Stored folder roots in Room database (SafRoot table)
  - Scans folders automatically via WorkManager background job
  - Breadcrumb navigation
  - Filter buttons to switch between views
  - Pagination support for large libraries

**Storage Permissions**:
- Uses SAF for persistent folder access
- Requests `READ_MEDIA_AUDIO` (API 33+) or `READ_EXTERNAL_STORAGE`
- Stores TreeUri with persistent permissions via `takePersistableUriPermission`

**Data Sources**:
1. **Legacy SharedPreferences** (`Prefs.getTreeUri()`)
2. **Room Database** (SafRoot table for multiple folders)

---

## 3. UI Components for File/Folder Display

### Layout Files:

| Layout | Path | Used By | Purpose |
|--------|------|---------|---------|
| **activity_local_music.xml** | `/res/layout/` | LocalMusicActivity | Main browse layout with filter buttons, breadcrumb, RecyclerView |
| **activity_file_browser.xml** | `/res/layout/` | FileBrowserActivity | Simple file list layout |
| **item_folder.xml** | `/res/layout/` | MusicBrowserAdapter | Individual folder/album/artist item display |
| **item_file.xml** | `/res/layout/` | FileAdapter (FileBrowserActivity) | Individual file item display |

### Item Display Components:

#### **item_folder.xml** (MusicBrowserAdapter - Universal)
- **Cover Art Image**: 64dp x 64dp with centerCrop scaling
- **Title Text**: Bold white, handles multi-line with ellipsize
- **Subtitle Text**: Gray text showing track count or metadata
- **Navigation Arrow**: Right-pointing icon with 270° rotation

#### **item_file.xml** (FileBrowserActivity - Simple)
- **Icon**: Folder or file icon with white tint
- **Name Text**: Bold white
- **Details Text**: File extension or "Folder"

### Adapter Implementations:

#### **MusicBrowserAdapter** (Primary - Universal)
- **File**: `/app/src/main/java/com/genaro/radiomp3/ui/MusicBrowserAdapter.kt`
- **Features**:
  - Uses sealed class `BrowserItem` with 4 subtypes:
    - `FolderItem` - Physical folder with path and hierarchy info
    - `AlbumItem` - Music album
    - `ArtistItem` - Music artist
    - `TrackItem` - Individual track
  - Cover art loading with fallback strategies:
    - Embedded metadata extraction using MediaMetadataRetriever
    - HTTP URL loading via Glide with crossfade transition
    - Folder/default icon fallback
  - Arrow visibility depends on item type (hidden for tracks)

#### **FileAdapter** (Simple - FileBrowserActivity)
- Inline adapter in FileBrowserActivity
- Simpler than MusicBrowserAdapter
- No cover art loading
- Basic icon/text display

---

## 4. Existing Filter/Search Implementations

### Search Features:

#### **TrackDao Query Method**
```kotlin
@Query("SELECT * FROM Track WHERE 
    title LIKE :query OR 
    artistName LIKE :query OR 
    albumTitle LIKE :query OR 
    displayName LIKE :query")
suspend fun searchTracks(query: String): List<Track>
```

### View Mode Filters (LocalMusicActivity):

**4 Filter Buttons**:
1. **📁 Cartelle (Folders)** - Browse by file system hierarchy
2. **💿 Album** - Group by album metadata
3. **🎤 Artisti (Artists)** - Group by artist name
4. **🎵 Tutti (All)** - Flat list of all tracks

**Implementation**:
- Buttons toggle `ViewMode` enum (FOLDERS, ALBUMS, ARTISTS, ALL)
- Active button: Purple background (#03DAC5), white text
- Inactive button: Dark background (#888888), gray text
- Switching view clears navigation stack and reloads data

**View-Specific Logic**:
- **FOLDERS**: Builds hierarchy from `folderPathDisplay`, shows subfolders only
- **ALBUMS**: Groups tracks by `albumTitle`, shows album count
- **ARTISTS**: Groups by `artistName`, shows album+track counts
- **ALL**: Flat list sorted by title

---

## 5. Project Structure - app/src/main/java/com/genaro/radiomp3

```
radiomp3/
├── RadioApp.kt                                    # Application class
├── api/
│   └── RadioApiService.kt                        # REST API definitions
├── data/
│   ├── Prefs.kt                                  # SharedPreferences manager
│   ├── Prefs.kt                                  # Settings storage
│   ├── RadioModels.kt                            # Data classes (Country, Station, Favorite)
│   ├── HomePageButton.kt                         # Home page button model
│   ├── prefs/
│   │   └── PreferenceManager.kt
│   └── local/
│       ├── AppDatabase.kt                        # Room database singleton
│       ├── Track.kt                              # Track entity
│       ├── Album.kt                              # Album entity
│       ├── Artist.kt                             # Artist entity
│       ├── ArtworkCache.kt                       # Artwork cache entity
│       ├── SafRoot.kt                            # SAF folder root entity
│       ├── QueueItem.kt                          # Playback queue item
│       ├── PreferenceEntry.kt                    # Key-value preferences
│       └── dao/
│           ├── TrackDao.kt                       # Track queries + Flow
│           ├── AlbumDao.kt
│           ├── ArtistDao.kt
│           ├── ArtworkDao.kt
│           ├── SafRootDao.kt
│           ├── QueueItemDao.kt
│           └── PreferenceDao.kt
├── domain/
│   └── library/
│       └── LibraryUseCases.kt
├── logging/
│   └── AudioLog.kt
├── net/
│   ├── RadioBrowser.kt                          # Radio API client
│   └── DeezerApi.kt                             # Deezer API client
├── playback/
│   ├── PlayerService.kt
│   ├── MusicPlayerService.kt                    # Media3 service
│   ├── PlayerRepo.kt
│   ├── PlayerHolder.kt
│   ├── QueueManager.kt
│   ├── ArtworkProvider.kt
│   ├── audio/
│   │   ├── USBAudioAnalyzer.kt
│   │   └── ResamplingMonitor.kt
│   └── services/...
├── scanner/
│   ├── ScanWorker.kt                            # WorkManager background task
│   ├── SAFScanner.kt                            # SAF-based folder scanner
│   ├── MediaStoreScanner.kt                     # MediaStore scanner (disabled)
│   ├── MusicScanner.kt                          # Base scanner
│   └── TagEnrichment.kt                         # Metadata enrichment
├── ui/
│   ├── LocalMusicActivity.kt                    # Main music browser
│   ├── FileBrowserActivity.kt                   # Simple file browser
│   ├── NowPlayingActivity.kt                    # Music playback screen
│   ├── SettingsActivity.kt                      # Settings
│   ├── MainActivity.kt                          # Home screen
│   ├── RadioPlayerActivity.kt                   # Radio streaming
│   ├── MusicBrowserAdapter.kt                   # Universal adapter
│   ├── MusicBrowserModels.kt                    # BrowserItem sealed class
│   ├── TrackAdapter.kt
│   ├── HomePageButtonAdapter.kt
│   ├── RadioPickerActivity.kt
│   ├── RadioFavoritesActivity.kt
│   ├── HomePageSetupActivity.kt
│   ├── CoverController.kt
│   ├── CoverViewModel.kt
│   ├── UiStateViewModel.kt
│   ├── TechnicalDetailsBottomSheet.kt
│   ├── vu/                                      # VU meter components
│   │   ├── RetroVuMeterView.kt
│   │   ├── VuMeterDialogFragment.kt
│   │   ├── VuMeterPanelController.kt
│   │   └── ...
│   ├── widgets/
│   │   └── FeedbackBanner.kt
│   └── ...
├── utils/
│   ├── FaviconHelper.kt
│   └── ImageLoaderHelper.kt
├── logging/
└── work/
    └── ScanWorker.kt
```

---

## 6. Home Folder Selection Implementation

### Storage in Preferences:

#### **SharedPreferences (Legacy)**
- **File**: `/app/src/main/java/com/genaro/radiomp3/data/Prefs.kt`
- **Key**: `mp3_tree`
- **Methods**:
  ```kotlin
  fun getTreeUri(ctx: Context): Uri?
  fun setTreeUri(ctx: Context, uri: Uri)
  ```
- **Used By**: FileBrowserActivity, SettingsActivity

#### **Room Database (Modern)**
- **Entity**: `SafRoot` in Track entity
- **DAO**: `SafRootDao` 
- **Storage**: Supports multiple folder roots
- **Methods**:
  ```kotlin
  suspend fun insert(safRoot: SafRoot)
  suspend fun getAll(): List<SafRoot>
  ```
- **Used By**: LocalMusicActivity, ScanWorker

### UI for Folder Selection:

#### **SettingsActivity.kt**
- **Button**: `btnPickFolder` - Opens SAF directory picker
- **Display**: `txtFolder` TextView shows selected folder path
- **Persistence**: 
  1. User picks folder via `ActivityResultContracts.OpenDocumentTree()`
  2. Calls `contentResolver.takePersistableUriPermission()`
  3. Saves to Prefs via `Prefs.setTreeUri()`

#### **LocalMusicActivity.kt**
- **Button**: `btnAddFolder` - Opens SAF directory picker
- **Persistence**:
  1. User picks folder
  2. Takes persistent URI permission
  3. Creates SafRoot entity with URI, displayName, takeFlags
  4. Inserts into Room database
  5. Logs to console for verification
  6. Triggers scan immediately
- **Auto-scan**: 
  - `autoTriggerScan()` runs on resume
  - 6-hour cooldown between scans
  - Scans only if folders configured

---

## 7. Key Data Structures & Models

### BrowserItem Sealed Class
```kotlin
sealed class BrowserItem {
    abstract val id: String
    abstract val title: String
    abstract val subtitle: String
    abstract val coverArtUri: String?
    
    data class FolderItem(
        val path: String,
        val level: Int,
        val hasSubfolders: Boolean,
        val trackCount: Int,
        val folderCount: Int
    )
    
    data class AlbumItem(
        val albumId: Long,
        val artistName: String?,
        val year: Int?,
        val trackCount: Int
    )
    
    data class ArtistItem(
        val artistName: String,
        val albumCount: Int,
        val trackCount: Int
    )
    
    data class TrackItem(
        val track: Track
    )
}
```

### Track Entity
```kotlin
@Entity
data class Track(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val displayName: String,
    val title: String?,
    val artistName: String?,
    val albumTitle: String?,
    val folderPathDisplay: String?,
    val durationMs: Long?,
    val bitrateKbps: Int?,
    val mimeType: String?,
    // ... other metadata fields
)
```

### ViewMode Enum
```kotlin
enum class ViewMode {
    FOLDERS,    // Browse by folder structure
    ALBUMS,     // Browse by albums
    ARTISTS,    // Browse by artists
    ALL         // All tracks (flat list)
}
```

### NavigationState
```kotlin
data class NavigationState(
    val viewMode: ViewMode,
    val currentPath: String? = null,      // For FOLDERS mode
    val currentArtist: String? = null,    // For ARTISTS mode
    val currentAlbum: Long? = null        // For ALBUMS mode
)
```

---

## 8. Scanning & Metadata Enrichment

### ScanWorker (WorkManager Task)
- **Location**: `/app/src/main/java/com/genaro/radiomp3/scanner/ScanWorker.kt`
- **Triggers**: 
  - Manual: FAB in LocalMusicActivity
  - Auto: 6-hour interval check in LocalMusicActivity
- **Steps**:
  1. **SAFScanner** - Scans only user-selected folders (not all MediaStore)
  2. **TagEnrichment** - Extracts ID3 tags and artwork from files missing metadata
  3. **Album/Artist Stats** - (TODO) Update statistics

### Database Queries for Deduplication
```kotlin
// Remove duplicates (same URI scanned twice)
fun getAllTracksDistinctFlow(): Flow<List<Track>>
  // Returns MIN(id) for each URI

fun getTracksByFolderDistinctFlow(folderPath: String): Flow<List<Track>>
  // Same for folder-specific queries
```

---

## 9. Key Configuration & Dependencies

### Database
- **Name**: `genplayer.db`
- **Version**: 3
- **Migration**: `fallbackToDestructiveMigration()` (clear on version change)

### Supported Audio Formats
```regex
.*\.(mp3|flac|wav|m4a|aac|ogg|opus)$
```

### Pagination Configuration
- **LoadingStrategyDecider** determines strategy based on file count
- Large libraries (>10k) get warning and pagination

### Colors (Dark Theme)
- Background: #000000 (black)
- Primary Accent: #03DAC5 (cyan)
- Secondary: #1DB954 (Spotify green for FAB)
- Text: #FFFFFF (white), #AAAAAA (gray)
- Folder Icon: #FFA500 (orange)

---

## 10. Architecture Patterns

### MVVM with Room Database
- **Data Layer**: Room entities + DAOs + Database
- **Repository Layer**: No explicit repos, direct DAO access
- **UI Layer**: Activities with lifecycleScope for coroutines
- **Flow-Based Reactive**: getAllTracksDistinctFlow() drives UI updates

### Navigation Pattern
- **Stack-Based**: `Stack<NavigationState>` for back navigation
- **ViewMode Switching**: Clears stack when switching between FOLDERS/ALBUMS/ARTISTS
- **Breadcrumb Display**: Shows current path in FOLDERS mode

### Error Handling
- Try-catch in critical sections
- Logging via `android.util.Log`
- Toast notifications for user feedback
- Graceful fallbacks (default icons, empty states)

---

## Summary

The GenPlayer music player is a well-architected application combining:
1. **Modern Android components** (Room, WorkManager, Flow, Media3)
2. **Dual file browsing** (simple FileBrowser + advanced LocalMusicBrowser)
3. **Multiple organization** (by folder, album, artist, or all)
4. **SAF-based security** (user-selected folders only)
5. **Reactive UI** (Flow-driven updates from database)
6. **Background scanning** (WorkManager for metadata enrichment)
7. **Dark theme** with accent colors for visual clarity

The application is production-ready with proper error handling, logging, and user feedback mechanisms.
