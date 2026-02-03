---
sidebar_position: 2
---

# Core Components

This document details the core components that form the foundation of ZippyPlayer's architecture. Each component plays a specific role in the application's functionality.

## Database Layer

### AppDatabase (Room)

The central database that stores all persistent app data.

**Location**: `media/db/AppDatabase.kt`

**Entities**:
- `MediaItem`: Video/audio file metadata
- `Playlist`: User-created playlists
- `PlaylistMediaCrossRef`: Many-to-many relationship
- `DownloadTask`: Download queue and status
- `FeedItem`: Content feed cache
- `WatchHistory`: Playback history tracking

**Features**:
- Auto-migrations for database schema changes
- Type converters for complex types
- Full-text search support
- Foreign key relationships
- Indexes for query optimization

```kotlin
@Database(
    entities = [
        MediaItem::class,
        Playlist::class,
        DownloadTask::class,
        // ...
    ],
    version = 5,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun downloadDao(): DownloadDao
    // ...
}
```

### Data Access Objects (DAOs)

#### MediaDao
**Purpose**: CRUD operations for media items

**Key Methods**:
- `getAll()`: Retrieve all media with pagination
- `getById(id)`: Get specific media item
- `insert(item)`: Add new media
- `delete(item)`: Remove media
- `search(query)`: Full-text search
- `getByPlaylist(playlistId)`: Playlist contents

#### PlaylistDao
**Purpose**: Manage user playlists

**Key Methods**:
- `getAllPlaylists()`: List all playlists
- `createPlaylist(name)`: New playlist
- `addMediaToPlaylist(playlistId, mediaId)`: Add item
- `removeMediaFromPlaylist(playlistId, mediaId)`: Remove item
- `deletePlaylist(id)`: Delete playlist

#### DownloadDao
**Purpose**: Track download tasks

**Key Methods**:
- `getAllDownloads()`: All download tasks
- `getActiveDownloads()`: In-progress downloads
- `updateProgress(id, progress)`: Update download progress
- `updateStatus(id, status)`: Change download state
- `deleteCompleted()`: Clean up finished downloads

## Repository Layer

### MediaRepository

**Purpose**: Single source of truth for media data

**Location**: `media/repository/MediaRepository.kt`

**Responsibilities**:
- Coordinate between Room and network sources
- Implement caching strategy
- Transform database entities to domain models
- Handle data synchronization

**Key Functions**:
```kotlin
interface MediaRepository {
    fun getMediaPagingSource(): PagingSource<Int, MediaItem>
    suspend fun getMediaById(id: String): MediaItem?
    suspend fun addMedia(item: MediaItem)
    suspend fun deleteMedia(id: String)
    suspend fun searchMedia(query: String): List<MediaItem>
    fun getRecentMedia(): Flow<List<MediaItem>>
}
```

### DownloadRepository

**Purpose**: Manage download operations

**Location**: `download/DownloadRepository.kt`

**Responsibilities**:
- Queue download tasks
- Track download progress
- Handle download lifecycle
- Coordinate between download workers
- Persist download state

**Key Functions**:
```kotlin
interface DownloadRepository {
    suspend fun startDownload(url: String, type: DownloadType)
    suspend fun pauseDownload(taskId: String)
    suspend fun resumeDownload(taskId: String)
    suspend fun cancelDownload(taskId: String)
    fun observeDownload(taskId: String): Flow<DownloadState>
    fun getAllDownloads(): Flow<List<DownloadTask>>
}
```

## ViewModel Components

### BaseViewModel

**Purpose**: Common ViewModel functionality

**Features**:
- Error handling
- Loading state management
- Coroutine scope management
- Shared state patterns

### Key ViewModels

#### FeedViewModel
**Location**: `ui/home/FeedViewModel.kt`

**Responsibilities**:
- Load feed content with pagination
- Handle pull-to-refresh
- Cache feed data
- Filter and sort content

**State**:
```kotlin
data class FeedUiState(
    val items: List<FeedItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isRefreshing: Boolean = false
)
```

#### LibraryViewModel
**Location**: `ui/library/LibraryViewModel.kt`

**Responsibilities**:
- Display media library
- Manage playlists
- Handle media operations (delete, share)
- Track storage statistics

**State**:
```kotlin
data class LibraryUiState(
    val media: List<MediaItem> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val selectedTab: LibraryTab = LibraryTab.ALL,
    val sortOrder: SortOrder = SortOrder.DATE_DESC,
    val storageInfo: StorageInfo? = null
)
```

#### PlayerViewModel
**Location**: `ui/player/PlayerViewModel.kt`

**Responsibilities**:
- Manage playback state
- Handle player controls
- Track playback position
- Manage subtitle and audio tracks
- Chromecast integration

**State**:
```kotlin
data class PlayerUiState(
    val isPlaying: Boolean = false,
    val duration: Long = 0,
    val position: Long = 0,
    val bufferedPosition: Long = 0,
    val currentMedia: MediaItem? = null,
    val subtitles: List<SubtitleTrack> = emptyList(),
    val audioTracks: List<AudioTrack> = emptyList()
)
```

## Download System Components

### DownloadCoordinator

**Purpose**: Orchestrate all download operations

**Location**: `download/DownloadCoordinator.kt`

**Responsibilities**:
- Route downloads to appropriate handlers
- Manage download queue
- Handle concurrent downloads
- Coordinate retry logic

### Download Types

#### HTTPDownloader
**Location**: `download/http/HTTPDownloader.kt`

**Features**:
- Direct HTTP/HTTPS downloads
- Resume support (Range headers)
- Progress tracking
- Parallel chunk downloading

#### HLSDownloader
**Location**: `download/hls/HLSDownloader.kt`

**Features**:
- HLS manifest parsing
- Segment downloading
- TS file merging
- Key decryption support

#### TorrentDownloader
**Location**: `download/torrent/TorrentDownloader.kt`

**Features**:
- LibTorrent4j integration
- Magnet link support
- Sequential downloading
- Torrent streaming
- DHT and peer exchange

### DownloadService

**Purpose**: Foreground service for active downloads

**Location**: `download/DownloadService.kt`

**Features**:
- Persistent notification
- Progress updates
- Wake lock management
- Network state monitoring

### Download Workers

#### DownloadWorker
**Location**: `download/worker/DownloadWorker.kt`

**Purpose**: Background download execution via WorkManager

**Features**:
- Constraint-based execution (network, battery)
- Automatic retry with exponential backoff
- Progress reporting
- Cancellation support

## Ad System Components

### AdManager

**Purpose**: Central ad management

**Location**: `ads/AdManager.kt`

**Responsibilities**:
- Initialize ad SDKs
- Manage ad lifecycle
- Track ad revenue
- Handle ad callbacks

### AdWaterfallManager

**Purpose**: Implement ad network waterfall

**Location**: `ads/AdWaterfallManager.kt`

**Features**:
- Priority-based ad network selection
- Automatic failover
- Performance tracking
- Network timeout handling

**Waterfall Flow**:
```
1. Try AdMob → Success ✓ | Fail ↓
2. Try IronSource → Success ✓ | Fail ↓
3. Try AppLovin → Success ✓ | Fail ↓
4. Try Unity Ads → Success ✓ | Fail ↓
5. Try Vungle → Success ✓ | Fail ↓
6. Show house ad (fallback)
```

### AdPreloadManager

**Purpose**: Preload ads for instant display

**Location**: `ads/AdPreloadManager.kt`

**Features**:
- Background ad loading
- Cache management
- Expiry handling
- Memory optimization

### Ad Providers

**Location**: `ads/providers/`

- `AdMobProvider`: Google AdMob integration
- `IronSourceProvider`: IronSource mediation
- `AppLovinProvider`: AppLovin MAX
- `UnityAdProvider`: Unity Ads
- `VungleProvider`: Vungle video ads
- `HouseAdProvider`: Fallback house ads

## Media Player Components

### MPVFragment

**Purpose**: Primary video player implementation

**Location**: `ui/player/mpv/MPVFragment.kt`

**Features**:
- Custom player controls
- Gesture support (brightness, volume, seek)
- Picture-in-Picture mode
- Subtitle rendering
- Audio track switching
- Playback speed control

### Player Controls

**Components**:
- Play/Pause button
- Seek bar with thumbnail preview
- Time indicators
- Quality selector
- Subtitle selector
- Audio track selector
- Playback speed selector
- Fullscreen toggle
- Chromecast button

## Utility Components

### Storage Utils

**Purpose**: File and storage management

**Key Features**:
- SAF (Storage Access Framework) integration
- File operations via SafeFile
- Storage space calculation
- Cache management

### Network Utils

**Purpose**: Network operations and monitoring

**Key Features**:
- Network connectivity detection
- Connection type identification (WiFi, Cellular)
- Bandwidth estimation
- HTTP utilities

### Thumbnail Generator

**Purpose**: Video thumbnail extraction

**Features**:
- Coil integration
- Frame extraction at specific timestamps
- Thumbnail caching
- Placeholder images

## Navigation Components

### Navigation Graph

**Structure**:
```
Home Graph
├─ Feed/Browse
├─ Library
│  ├─ All Media
│  ├─ Playlists
│  └─ Favorites
├─ Downloads
│  ├─ Active
│  ├─ Completed
│  └─ Failed
└─ Settings
   ├─ General
   ├─ Player
   ├─ Downloads
   └─ About

Player Graph (separate)
└─ Player Screen
```

### Bottom Navigation

**Tabs**:
- Home/Feed
- Library
- Downloads
- Settings

## Dependency Injection Modules

### DatabaseModule
**Provides**: Room database instance, DAOs

### NetworkModule
**Provides**: HTTP client, API services

### RepositoryModule
**Provides**: Repository implementations

### DownloadModule
**Provides**: Download coordinator, downloaders

### PlayerModule
**Provides**: Media player, controllers

### AdModule
**Provides**: Ad manager, providers

## Background Work Components

### WorkManager Workers

#### DownloadWorker
- Handles background downloads
- Respects battery and network constraints

#### CleanupWorker
- Periodic cleanup of cache
- Removes expired data

#### SyncWorker
- Synchronizes data with remote sources
- Updates feed content

## Summary

These core components work together to provide:
- ✅ Robust data persistence (Room)
- ✅ Clean architecture (Repository pattern)
- ✅ Reactive UI updates (LiveData/Flow)
- ✅ Efficient background operations (WorkManager)
- ✅ Advanced media playback (Custom player)
- ✅ Multi-protocol downloads (HTTP, HLS, Torrent)
- ✅ Monetization (Ad waterfall system)

Each component is designed to be:
- **Testable**: Easy to mock and test in isolation
- **Maintainable**: Clear responsibilities and interfaces
- **Scalable**: Easy to extend with new features
- **Efficient**: Optimized for performance and battery