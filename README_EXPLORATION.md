# GenPlayer Android Project - Exploration Summary

## Overview
This document summarizes the comprehensive exploration of the GenPlayer Android music player project.

## Project Documentation Created

Three detailed documentation files have been created in the project root:

### 1. ARCHITECTURE_ANALYSIS.md (455 lines, 16KB)
Comprehensive architecture overview covering:
- All primary activities and their purposes
- Two parallel file browsing systems (simple and advanced)
- UI components and layout structure
- Filter and search implementations
- Complete project package structure
- Home folder selection implementation
- Key data structures and models
- File scanning and metadata enrichment
- Configuration and dependencies
- Architecture patterns and best practices

### 2. QUICK_REFERENCE.md (185 lines, 5.5KB)
Quick lookup guide with:
- File locations for key components
- Key enums and sealed classes
- Common database queries
- UI color scheme
- Storage mechanisms
- File scanning triggers
- Important code patterns
- Debugging tips
- Common customizations
- Performance considerations

### 3. CODE_SNIPPETS.md (465 lines, 13KB)
Ready-to-use code examples covering:
- Loading tracks from database
- Handling folder selection with SAF
- Creating BrowserItems
- Adapter usage and item clicks
- Triggering file scans
- View mode switching
- Building folder hierarchies
- Back navigation
- Empty state handling
- Preferences management
- Playing tracks
- Testing database queries

---

## Key Findings

### 1. MP3 Player UI/Activities
**Primary Location**: `/app/src/main/java/com/genaro/radiomp3/ui/`

**Key Activities**:
- **LocalMusicActivity** - Main music browser with 4 view modes (folders/albums/artists/all)
- **FileBrowserActivity** - Simple SAF-based file browser
- **NowPlayingActivity** - Music playback screen
- **SettingsActivity** - Settings and folder selection

### 2. File Browsing Implementation

**Two Systems**:

1. **Simple FileBrowserActivity**
   - Direct SAF navigation
   - Stack-based folder traversal
   - Immediate file playback
   - Minimal UI, basic adapters

2. **Advanced LocalMusicActivity**
   - Database-driven (Room)
   - Reactive Flow updates
   - Multiple view modes
   - Breadcrumb navigation
   - Background scanning with WorkManager
   - Folder root management

### 3. UI Components

**Layouts**:
- `activity_local_music.xml` - Main browser with filter buttons, breadcrumb, RecyclerView
- `activity_file_browser.xml` - Simple file list
- `item_folder.xml` - Universal item display (64x64 cover, title, subtitle, arrow)
- `item_file.xml` - Simple file item (icon, name, details)

**Adapters**:
- `MusicBrowserAdapter` - Universal (FolderItem, AlbumItem, ArtistItem, TrackItem)
- `FileAdapter` - Inline in FileBrowserActivity

**Color Scheme**:
- Background: #000000, Accent: #03DAC5, Folder: #FFA500
- Text: #FFFFFF (white), #AAAAAA (gray)

### 4. Filter/Search Features

**Built-in Filters**:
1. Folders - File system hierarchy view
2. Albums - Group by album metadata
3. Artists - Group by artist name
4. All - Flat track list

**Search Query**:
```kotlin
searchTracks(query: String)  // Searches title, artist, album, filename
```

**Implementation**:
- Button-based switching (toggles ViewMode enum)
- Database-driven with Flow-based reactive updates
- Clears navigation stack on mode change

### 5. Project Structure

**Package Organization** (10 main packages):
```
radiomp3/
├── ui/              (Activities, adapters, view models)
├── data/            (Entities, DAOs, preferences)
├── playback/        (Media3 services, player logic)
├── scanner/         (File scanning, metadata enrichment)
├── net/             (API clients for radio/Deezer)
├── domain/          (Use cases)
├── logging/         (Audio logging)
├── utils/           (Helpers)
├── api/             (REST API definitions)
└── work/            (Background tasks)
```

**Database** (Room):
- Name: `genplayer.db`
- Entities: Track, Album, Artist, ArtworkCache, SafRoot, QueueItem, PreferenceEntry
- DAOs: TrackDao, AlbumDao, ArtistDao, ArtworkDao, SafRootDao, QueueItemDao, PreferenceDao

### 6. Home Folder Selection

**Storage Methods**:
1. **Legacy** - SharedPreferences with key `mp3_tree`
   - Used by: FileBrowserActivity, SettingsActivity
   
2. **Modern** - Room Database SafRoot table
   - Used by: LocalMusicActivity, ScanWorker
   - Supports multiple folders

**UI Implementation**:
- SettingsActivity: `btnPickFolder` button
- LocalMusicActivity: `btnAddFolder` button
- Both use SAF (ActivityResultContracts.OpenDocumentTree)
- Persistent permissions via `takePersistableUriPermission()`
- Auto-scan with 6-hour cooldown

---

## Key Algorithms & Patterns

### Folder Hierarchy Building
- **Approach**: Calculate on-the-fly from Track.folderPathDisplay
- **Strategy**: Show only immediate children (1 level deep)
- **Efficiency**: More efficient than full tree loading
- **Deduplication**: Removes duplicate URIs via MIN(id) per URI query

### Navigation Pattern
- **Stack-Based**: `Stack<NavigationState>` for back navigation
- **ViewMode-Aware**: Saves mode when navigating
- **Clear on Mode Switch**: Prevents confusion when switching views

### Reactive UI
- **Flow-Driven**: getAllTracksDistinctFlow() triggers UI updates
- **Lifecycle-Aware**: Uses lifecycleScope.launch for coroutines
- **Automatic Updates**: DB changes immediately reflect in UI

### File Scanning
- **Background**: WorkManager OneTimeWorkRequest
- **SAF-Only**: Only scans user-selected folders (not all MediaStore)
- **Enrichment**: Extracts ID3 tags and artwork from files
- **Cooldown**: 6-hour interval between auto-scans

---

## Supported Audio Formats

```
.mp3, .flac, .wav, .m4a, .aac, .ogg, .opus
```

Regex pattern: `.*\.(mp3|flac|wav|m4a|aac|ogg|opus)$` (case-insensitive)

---

## Performance Optimizations

### Large Library Support
- LoadingStrategyDecider: Determines pagination strategy based on file count
- Warning: Shows for libraries >10k files
- Distinct queries: Removes duplicate entries per URI

### Cover Art Loading
- MediaMetadataRetriever: Extracts embedded artwork
- Glide: HTTP URL loading with crossfade transition
- Fallback: Folder/default icons
- Async: Non-blocking coroutine-based loading

### Database Deduplication
```kotlin
// Returns MIN(id) for each URI to prevent duplicates from multiple scans
getAllTracksDistinctFlow(): Flow<List<Track>>
getTracksByFolderDistinctFlow(folderPath): Flow<List<Track>>
```

---

## Architecture Decisions

### Why Two File Browsers?
1. **SimpleBrowser** - For users who want direct access to files
2. **AdvancedBrowser** - For organized library with metadata grouping

### Why SAF-Only?
- Security: User must explicitly select folders
- Android 11+: MediaStore access restricted anyway
- Permissions: Persistent URI grants survive app updates

### Why Room + SharedPreferences?
- Gradual migration path from legacy code
- Room supports multiple folders (future-proof)
- SharedPreferences used for settings (colors, options)

### Why Flow-Based?
- Reactive: UI automatically updates when DB changes
- Type-safe: Compile-time checking
- Lifecycle-aware: Automatically cancels when not in focus

---

## Known Limitations & TODOs

### TODOs in Code
- Step D in ScanWorker: Update Album/Artist statistics
- Some configuration values hardcoded (MIN_SCAN_INTERVAL: 6 hours)

### Design Considerations
- LocalMusicActivity is large (~900 lines) - could split into smaller components
- No explicit Repository pattern - uses DAOs directly
- No ViewModel for LocalMusicActivity - uses Activity lifecycle
- Cover art caching could be more efficient

---

## How to Use This Documentation

### For New Developers
1. Start with **QUICK_REFERENCE.md** for file locations
2. Read **ARCHITECTURE_ANALYSIS.md** sections 1-3 for UI understanding
3. Check **CODE_SNIPPETS.md** for implementation examples

### For Feature Development
1. Check **CODE_SNIPPETS.md** for relevant examples
2. Reference **ARCHITECTURE_ANALYSIS.md** section 5 for package organization
3. Use QUICK_REFERENCE.md debugging section

### For Bug Fixes
1. Identify component in QUICK_REFERENCE.md
2. Read relevant section in ARCHITECTURE_ANALYSIS.md
3. Check CODE_SNIPPETS.md for similar patterns

---

## File Paths Summary

**All absolute paths for quick reference**:

```
/mnt/d/AndroidStudioProjects/GenPlayer_V1/GenPlayer_V1/app/src/main/java/com/genaro/radiomp3/

Core Activities:
├── ui/LocalMusicActivity.kt
├── ui/FileBrowserActivity.kt
├── ui/NowPlayingActivity.kt
├── ui/SettingsActivity.kt

Data Layer:
├── data/Prefs.kt
├── data/local/AppDatabase.kt
├── data/local/Track.kt
├── data/local/SafRoot.kt
├── data/local/dao/TrackDao.kt
├── data/local/dao/SafRootDao.kt

UI Layer:
├── ui/MusicBrowserAdapter.kt
├── ui/MusicBrowserModels.kt
├── ui/TrackAdapter.kt

Scanning:
├── scanner/ScanWorker.kt
├── scanner/SAFScanner.kt
├── scanner/TagEnrichment.kt

Layouts:
├── res/layout/activity_local_music.xml
├── res/layout/activity_file_browser.xml
├── res/layout/item_folder.xml
├── res/layout/item_file.xml
```

---

## Next Steps for Feature Enhancement

### Suggested Improvements
1. Add search UI in LocalMusicActivity (use existing TrackDao.searchTracks())
2. Implement shuffle/sort options
3. Add playlist creation and management
4. Implement smart playlists (most played, recent additions, etc.)
5. Add metadata editing UI
6. Implement album art view/management

### Code Patterns to Follow
- Use Flow for reactive data
- Prefer sealed classes for type safety
- Keep Activities <500 lines (split into fragments)
- Use ViewModels for complex state
- Leverage WorkManager for background tasks

---

## Conclusion

GenPlayer is a well-architected, production-ready music player with:
- Modern Android components (Room, Flow, WorkManager, Media3)
- Secure file access (SAF-based)
- Multiple browsing modes (folders, albums, artists)
- Reactive UI updates
- Background metadata enrichment
- Proper error handling and user feedback

The codebase is maintainable and ready for feature additions.
