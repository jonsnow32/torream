---
sidebar_position: 1
---

# Architecture Overview

ZippyPlayer follows modern Android architecture patterns and best practices to create a scalable, maintainable, and testable application. This document provides a high-level overview of the application's architecture.

## Architecture Pattern

### MVVM (Model-View-ViewModel)

ZippyPlayer implements the **MVVM** (Model-View-ViewModel) architecture pattern, which provides clear separation of concerns and improves testability.

```
┌─────────────────────────────────────────────────────┐
│                    UI Layer                         │
│  (Activities, Fragments, Views, Adapters)          │
│  - Observes ViewModel                               │
│  - Displays data                                    │
│  - Handles user interactions                        │
└────────────────┬────────────────────────────────────┘
                 │
                 ↓
┌─────────────────────────────────────────────────────┐
│                 ViewModel Layer                      │
│  (ViewModels with LiveData/StateFlow)              │
│  - UI state management                              │
│  - Business logic coordination                      │
│  - Lifecycle awareness                              │
└────────────────┬────────────────────────────────────┘
                 │
                 ↓
┌─────────────────────────────────────────────────────┐
│               Repository Layer                       │
│  (Single Source of Truth)                           │
│  - Coordinates data sources                         │
│  - Caching strategy                                 │
│  - Data transformation                              │
└─────────┬────────────────────────┬──────────────────┘
          │                        │
          ↓                        ↓
┌──────────────────┐    ┌──────────────────────┐
│  Local Data      │    │  Remote Data         │
│  (Room Database) │    │  (Network/Downloads) │
│  - Cached data   │    │  - API calls         │
│  - User data     │    │  - File downloads    │
│  - Settings      │    │  - Torrent data      │
└──────────────────┘    └──────────────────────┘
```

## Layer Breakdown

### 1. UI Layer (`ui/` package)

The UI layer contains all user-facing components:

**Components:**
- **Activities**: Main container activities
- **Fragments**: Individual screens
  - `HomeFragment`: Main feed/browse
  - `LibraryFragment`: Media library management
  - `DownloadFragment`: Download management
  - `SettingsFragment`: App preferences
  - `PlayerFragment`: Video playback
- **Adapters**: RecyclerView adapters for lists
- **ViewHolders**: Item view rendering
- **Custom Views**: Reusable UI components

**Responsibilities:**
- Display data from ViewModels
- Handle user input
- Navigation between screens
- UI state rendering
- Animation and transitions

### 2. ViewModel Layer

ViewModels manage UI state and coordinate with repositories:

**Key ViewModels:**
- `FeedViewModel`: Manages home feed data
- `LibraryViewModel`: Media library state
- `DownloadViewModel`: Download operations state
- `PlayerViewModel`: Playback state and controls
- `SettingsViewModel`: User preferences

**Characteristics:**
- Survive configuration changes
- Lifecycle-aware
- No Android framework dependencies
- Expose data via LiveData/StateFlow
- Handle business logic

### 3. Repository Layer (`media/repository/`, `download/`)

Repositories act as the single source of truth:

**Main Repositories:**
- `MediaRepository`: Media library data
- `DownloadRepository`: Download management
- `FeedRepository`: Content feeds
- `FavoritesRepository`: User favorites
- `SettingsRepository`: App settings

**Responsibilities:**
- Coordinate between data sources
- Implement caching strategies
- Data transformation
- Handle data conflicts
- Error handling

### 4. Data Layer

#### Local Data (`media/db/`, `datastore/`)

**Room Database:**
- `AppDatabase`: Main database instance
- **DAOs** (Data Access Objects):
  - `MediaDao`: Media items CRUD
  - `PlaylistDao`: Playlist management
  - `DownloadDao`: Download tracking
  - `FeedDao`: Feed data caching
- **Entities**:
  - `MediaItem`: Video/audio metadata
  - `Playlist`: Playlist information
  - `DownloadTask`: Download state
  - `FeedItem`: Feed content

**DataStore:**
- User preferences
- App settings
- Account information

#### Remote Data (`network/`, `download/`)

**Network Operations:**
- HTTP requests via NiceHttp
- Content scraping
- Metadata fetching
- Thumbnail downloads

**Download System:**
- HTTP downloads
- HLS segmented downloads
- Torrent downloads
- Stream management

## Dependency Injection

### Hilt/Dagger Architecture

ZippyPlayer uses **Hilt** for dependency injection:

```
@HiltAndroidApp
Application
    │
    ├─► @AndroidEntryPoint
    │   Activities/Fragments
    │       │
    │       └─► @HiltViewModel
    │           ViewModels
    │               │
    │               └─► @Inject
    │                   Repositories
    │                       │
    │                       └─► @Inject
    │                           Data Sources
    │
    └─► @HiltWorker
        Background Workers
```

**DI Modules:**
- `DatabaseModule`: Room database instance
- `NetworkModule`: HTTP clients
- `RepositoryModule`: Repository bindings
- `DownloadModule`: Download system components
- `PlayerModule`: Media player components
- `AdModule`: Ad providers

**Benefits:**
- Compile-time dependency validation
- Automatic lifecycle management
- Easy testing with mock dependencies
- Reduced boilerplate

## Package Structure

```
com.tv.apps.zippy/
├── ads/                    # Ad integration
│   ├── AdManager
│   ├── AdPreloadManager
│   ├── AdWaterfallManager
│   └── providers/          # Ad network providers
├── datastore/              # Local preferences
│   ├── account/
│   └── app/
├── download/               # Download system
│   ├── http/               # HTTP downloads
│   ├── hls/                # HLS downloads
│   ├── torrent/            # Torrent downloads
│   └── worker/             # Background workers
├── media/                  # Media database
│   ├── dao/                # Room DAOs
│   ├── db/                 # Database setup
│   ├── entities/           # Database entities
│   ├── repository/         # Data repositories
│   └── di/                 # DI modules
├── model/                  # Data models
├── network/                # Networking utilities
├── ui/                     # UI components
│   ├── adapter/            # RecyclerView adapters
│   ├── browse/             # Browse screens
│   ├── dialog/             # Dialog fragments
│   ├── download/           # Download UI
│   ├── feed/               # Feed/home UI
│   ├── home/               # Home screen
│   ├── library/            # Library UI
│   ├── player/             # Player UI
│   ├── settings/           # Settings UI
│   └── widget/             # Custom widgets
└── utils/                  # Utility classes
```

## Key Architectural Decisions

### 1. Single Activity Architecture

ZippyPlayer primarily uses a single-activity architecture with the Navigation component:

**Benefits:**
- Simplified navigation
- Shared ViewModels
- Consistent transitions
- Reduced complexity

### 2. Reactive Programming

Uses Kotlin Flow and LiveData for reactive data streams:

**Advantages:**
- Automatic UI updates
- Lifecycle awareness
- Backpressure handling
- Composition of async operations

### 3. Repository Pattern

Centralized data access through repositories:

**Why?**
- Single source of truth
- Easier testing
- Centralized caching logic
- Clean API for ViewModels

### 4. Paging 3 Integration

For efficient large dataset handling:

**Implementation:**
- `PagingSource` for data loading
- `PagingData` for UI updates
- Automatic loading states
- Memory efficiency

### 5. WorkManager for Downloads

Background downloads use WorkManager:

**Reasons:**
- Guaranteed execution
- System optimizations
- Constraint-based scheduling
- Easy cancellation and retry

## Data Flow

### Typical Data Flow Example

**Scenario**: User opens the library screen

```
1. LibraryFragment created
   │
   ↓
2. LibraryViewModel injected via Hilt
   │
   ↓
3. ViewModel requests data from MediaRepository
   │
   ↓
4. Repository checks Room database
   │
   ├─► If data exists: Return from cache
   │   │
   │   └─► Transform to UI models
   │
   └─► If stale/missing: Fetch from network
       │
       ├─► Fetch data
       ├─► Store in Room
       └─► Return to ViewModel
   │
   ↓
5. ViewModel exposes data via LiveData
   │
   ↓
6. Fragment observes and updates UI
```

## State Management

### ViewModel State

Each ViewModel manages its screen state:

```kotlin
data class LibraryUiState(
    val media: List<MediaItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedTab: LibraryTab = LibraryTab.ALL
)
```

**State Updates:**
- Immutable state objects
- Single source of truth
- Reactive updates via StateFlow/LiveData

### Download State

Download system maintains separate state:

```kotlin
sealed class DownloadStatus {
    object Queued : DownloadStatus()
    data class Downloading(val progress: Int) : DownloadStatus()
    data class Paused(val progress: Int) : DownloadStatus()
    object Completed : DownloadStatus()
    data class Failed(val error: String) : DownloadStatus()
}
```

## Threading Model

### Coroutines & Dispatchers

```kotlin
// Main dispatcher: UI updates
viewModelScope.launch(Dispatchers.Main) {
    _uiState.value = Loading
}

// IO dispatcher: Network, database
withContext(Dispatchers.IO) {
    database.mediaDao().getAll()
}

// Default dispatcher: CPU-intensive work
withContext(Dispatchers.Default) {
    processLargeData()
}
```

**Threading Rules:**
- UI updates on Main dispatcher
- Database operations on IO dispatcher
- Network calls on IO dispatcher
- Heavy computations on Default dispatcher

## Error Handling

### Layered Error Handling

1. **Data Layer**: Catch and wrap exceptions
2. **Repository Layer**: Transform to domain errors
3. **ViewModel Layer**: Convert to UI states
4. **UI Layer**: Display user-friendly messages

```kotlin
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: Exception) : Result<Nothing>()
    object Loading : Result<Nothing>()
}
```

## Testing Strategy

### Unit Tests
- ViewModels: Business logic
- Repositories: Data coordination
- Utilities: Helper functions

### Integration Tests
- Room DAOs: Database operations
- Repositories: Full data flow

### UI Tests
- Espresso: User interactions
- Fragment scenarios
- Navigation testing

## Performance Considerations

### Optimizations

1. **Lazy Loading**: Paging 3 for large lists
2. **Image Caching**: Coil with memory/disk cache
3. **Database Indexing**: Optimized queries
4. **WorkManager**: Battery-efficient background work
5. **ViewBinding**: Fast view access
6. **Kotlin Coroutines**: Efficient async operations

### Memory Management

- Proper lifecycle handling
- Bitmap recycling
- Clearing resources in onDestroy
- Avoiding memory leaks with ViewModels

## Scalability

The architecture supports:
- Adding new features as modules
- Swapping implementations via DI
- Independent testing of components
- Multiple UI variants (phone/tablet/TV)

## Conclusion

ZippyPlayer's architecture provides:
- ✅ Clear separation of concerns
- ✅ Easy testing and maintenance
- ✅ Scalable code organization
- ✅ Efficient resource usage
- ✅ Modern Android best practices

The combination of MVVM, Repository pattern, and Hilt DI creates a robust foundation for a complex media application.