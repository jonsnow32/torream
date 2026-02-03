---
sidebar_position: 3
---

# Download System

ZippyPlayer features a sophisticated multi-protocol download system that supports HTTP, HLS, and torrent downloads with advanced features like resume support, parallel downloading, and background execution.

## Architecture Overview

```
┌─────────────────────────────────────────┐
│          UI Layer                       │
│  (DownloadFragment, ViewModel)         │
└──────────────┬──────────────────────────┘
               │
               ↓
┌─────────────────────────────────────────┐
│      DownloadRepository                 │
│  (Coordinates download operations)      │
└──────────────┬──────────────────────────┘
               │
               ↓
┌─────────────────────────────────────────┐
│     DownloadCoordinator                 │
│  (Routes to appropriate downloader)     │
└───┬──────────┬──────────┬───────────────┘
    │          │          │
    ↓          ↓          ↓
┌────────┐ ┌────────┐ ┌──────────┐
│ HTTP   │ │  HLS   │ │ Torrent  │
│Download│ │Download│ │ Download │
└────────┘ └────────┘ └──────────┘
    │          │          │
    └──────────┴──────────┘
               │
               ↓
┌─────────────────────────────────────────┐
│          Storage Layer                  │
│  (File System + Download Database)      │
└─────────────────────────────────────────┘
```

## Download Types

### 1. HTTP Downloads

**Location**: `download/http/HTTPDownloader.kt`

#### Features
- Direct HTTP/HTTPS downloads
- Resume support via Range headers
- Parallel chunk downloading
- Progress tracking
- Automatic retry on failure
- Speed limiting (optional)

#### Implementation

```kotlin
class HTTPDownloader(
    private val httpClient: OkHttpClient
) {
    suspend fun download(
        url: String,
        destination: File,
        onProgress: (Long, Long) -> Unit
    ): Result<File> {
        // Check if file exists (resume support)
        val existingSize = if (destination.exists()) {
            destination.length()
        } else 0L
        
        val request = Request.Builder()
            .url(url)
            .apply {
                if (existingSize > 0) {
                    addHeader("Range", "bytes=$existingSize-")
                }
            }
            .build()
        
        // Download with progress tracking
        // ...
    }
}
```

#### Use Cases
- Direct video file downloads
- Subtitle file downloads
- Thumbnail downloads
- APK updates

### 2. HLS Downloads

**Location**: `download/hls/HLSDownloader.kt`

#### Features
- M3U8 manifest parsing
- Segment downloading (TS files)
- Parallel segment downloads
- Segment merging
- AES-128 decryption support
- Quality selection

#### Implementation Flow

```
1. Parse M3U8 manifest
   ├─> Extract segment URLs
   ├─> Detect encryption (if any)
   └─> Get total segments

2. Download segments in parallel
   ├─> Download worker pool
   ├─> Track progress per segment
   └─> Handle encryption keys

3. Merge segments
   ├─> Concatenate TS files
   ├─> Generate final MP4/MKV
   └─> Cleanup temporary files

4. Verify integrity
   └─> Check file size and playability
```

#### Code Example

```kotlin
class HLSDownloader {
    suspend fun downloadHLS(
        manifestUrl: String,
        outputFile: File
    ): Result<File> {
        // 1. Parse manifest
        val manifest = parseM3U8(manifestUrl)
        val segments = manifest.segments
        
        // 2. Download segments in parallel
        val tempDir = createTempDir()
        segments.mapIndexed { index, segment ->
            async {
                downloadSegment(segment, tempDir, index)
            }
        }.awaitAll()
        
        // 3. Merge segments
        mergeSegments(tempDir, outputFile)
        
        // 4. Cleanup
        tempDir.deleteRecursively()
        
        return Result.success(outputFile)
    }
}
```

#### Use Cases
- Streaming video downloads
- Live stream recording
- Adaptive bitrate content

### 3. Torrent Downloads

**Location**: `download/torrent/TorrentDownloader.kt`

#### Features
- LibTorrent4j integration
- Magnet link support
- Torrent file support
- Sequential downloading (for streaming)
- DHT (Distributed Hash Table)
- Peer exchange (PEX)
- Torrent streaming via NanoHTTPD
- Resume/pause support
- Speed limits

#### Architecture

```kotlin
class TorrentDownloader(
    private val sessionManager: SessionManager
) {
    fun startDownload(
        magnetOrFile: String,
        saveDir: File,
        sequential: Boolean = false
    ): TorrentHandle {
        val params = TorrentParams().apply {
            savePath = saveDir.absolutePath
            
            if (sequential) {
                // Download pieces in order for streaming
                flags = flags or TorrentFlags.SEQUENTIAL_DOWNLOAD
            }
        }
        
        val handle = when {
            magnetOrFile.startsWith("magnet:") -> {
                sessionManager.download(magnetOrFile, params)
            }
            else -> {
                val torrentInfo = TorrentInfo(File(magnetOrFile))
                sessionManager.download(torrentInfo, params)
            }
        }
        
        return handle
    }
}
```

#### Torrent Streaming

**Location**: `download/TorrentStreamingService.kt`

Enables playback before download completion:

```kotlin
class TorrentStreamingService : Service() {
    private val httpServer = NanoHTTPD(8080)
    
    fun streamTorrent(handle: TorrentHandle): String {
        // Enable sequential download
        handle.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
        
        // Prioritize first and last pieces
        prioritizeStreamingPieces(handle)
        
        // Serve via HTTP
        val streamUrl = "http://127.0.0.1:8080/stream"
        
        httpServer.start()
        return streamUrl
    }
}
```

#### Use Cases
- Large video file downloads
- Distributed content
- Streaming before complete download
- P2P content sharing

## Download Management

### DownloadCoordinator

**Purpose**: Route downloads to appropriate handlers

**Location**: `download/DownloadCoordinator.kt`

```kotlin
class DownloadCoordinator @Inject constructor(
    private val httpDownloader: HTTPDownloader,
    private val hlsDownloader: HLSDownloader,
    private val torrentDownloader: TorrentDownloader,
    private val downloadDao: DownloadDao
) {
    suspend fun startDownload(
        url: String,
        title: String
    ): String {
        val type = detectDownloadType(url)
        val taskId = UUID.randomUUID().toString()
        
        // Create download task
        val task = DownloadTask(
            id = taskId,
            url = url,
            title = title,
            type = type,
            status = DownloadStatus.Queued
        )
        
        downloadDao.insert(task)
        
        // Route to appropriate downloader
        when (type) {
            DownloadType.HTTP -> enqueueHttpDownload(task)
            DownloadType.HLS -> enqueueHlsDownload(task)
            DownloadType.TORRENT -> enqueueTorrentDownload(task)
        }
        
        return taskId
    }
    
    private fun detectDownloadType(url: String): DownloadType {
        return when {
            url.endsWith(".m3u8") -> DownloadType.HLS
            url.startsWith("magnet:") -> DownloadType.TORRENT
            url.endsWith(".torrent") -> DownloadType.TORRENT
            else -> DownloadType.HTTP
        }
    }
}
```

### DownloadRepository

**Purpose**: Single source of truth for downloads

**Location**: `download/DownloadRepository.kt`

```kotlin
interface DownloadRepository {
    // Start/Control
    suspend fun startDownload(url: String, title: String): String
    suspend fun pauseDownload(taskId: String)
    suspend fun resumeDownload(taskId: String)
    suspend fun cancelDownload(taskId: String)
    suspend fun deleteDownload(taskId: String)
    
    // Observe
    fun observeDownload(taskId: String): Flow<DownloadState>
    fun observeAllDownloads(): Flow<List<DownloadTask>>
    fun getActiveDownloads(): Flow<List<DownloadTask>>
    
    // Query
    suspend fun getDownloadById(taskId: String): DownloadTask?
    suspend fun getDownloadsByStatus(status: DownloadStatus): List<DownloadTask>
}
```

### Download State

**Location**: `download/DownloadState.kt`

```kotlin
sealed class DownloadStatus {
    object Queued : DownloadStatus()
    
    data class Downloading(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val speed: Long // bytes per second
    ) : DownloadStatus()
    
    data class Paused(
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : DownloadStatus()
    
    object Completed : DownloadStatus()
    
    data class Failed(
        val error: String,
        val canRetry: Boolean = true
    ) : DownloadStatus()
}

data class DownloadTask(
    val id: String,
    val url: String,
    val title: String,
    val type: DownloadType,
    val status: DownloadStatus,
    val createdAt: Long,
    val completedAt: Long? = null,
    val filePath: String? = null
)
```

## Background Execution

### DownloadService

**Purpose**: Foreground service for active downloads

**Location**: `download/DownloadService.kt`

**Features**:
- Persistent notification with progress
- Wake lock for uninterrupted downloads
- Network state monitoring
- Battery optimization handling

```kotlin
class DownloadService : Service() {
    private val notification: NotificationCompat.Builder
    private var wakeLock: PowerManager.WakeLock? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Acquire wake lock
        acquireWakeLock()
        
        // Start foreground with notification
        startForeground(NOTIFICATION_ID, buildNotification())
        
        // Start download
        val taskId = intent?.getStringExtra("taskId")
        taskId?.let { executeDownload(it) }
        
        return START_STICKY
    }
    
    private fun updateProgress(progress: Int, speed: Long) {
        val notification = buildNotification()
            .setProgress(100, progress, false)
            .setContentText("${progress}% • ${formatSpeed(speed)}")
        
        notificationManager.notify(NOTIFICATION_ID, notification.build())
    }
}
```

### WorkManager Integration

**Location**: `download/worker/DownloadWorker.kt`

**Features**:
- Constraint-based execution
- Automatic retry with backoff
- Long-running work support

```kotlin
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val downloadRepository: DownloadRepository
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        val taskId = inputData.getString("taskId") ?: return Result.failure()
        
        return try {
            // Set foreground for long-running work
            setForeground(createForegroundInfo())
            
            // Execute download
            downloadRepository.executeDownload(taskId)
            
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                Result.retry() // Automatic retry
            } else {
                Result.failure()
            }
        }
    }
    
    private fun createForegroundInfo(): ForegroundInfo {
        val notification = createNotification()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }
}
```

**Enqueue Download Worker**:

```kotlin
fun enqueueDownload(taskId: String) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()
    
    val downloadWork = OneTimeWorkRequestBuilder<DownloadWorker>()
        .setConstraints(constraints)
        .setInputData(workDataOf("taskId" to taskId))
        .setBackoffCriteria(
            BackoffPolicy.EXPONENTIAL,
            WorkRequest.MIN_BACKOFF_MILLIS,
            TimeUnit.MILLISECONDS
        )
        .build()
    
    WorkManager.getInstance(context)
        .enqueueUniqueWork(
            "download_$taskId",
            ExistingWorkPolicy.KEEP,
            downloadWork
        )
}
```

## Advanced Features

### 1. Download Recovery

**Location**: `download/DownloadRecoveryManager.kt`

**Purpose**: Recover interrupted downloads on app restart

```kotlin
class DownloadRecoveryManager @Inject constructor(
    private val downloadDao: DownloadDao,
    private val downloadCoordinator: DownloadCoordinator
) {
    suspend fun recoverInterruptedDownloads() {
        val interrupted = downloadDao.getDownloadsByStatus(
            DownloadStatus.Downloading
        )
        
        interrupted.forEach { task ->
            // Resume download
            downloadCoordinator.resumeDownload(task.id)
        }
    }
}
```

### 2. Parallel Downloads

Support for multiple concurrent downloads:

```kotlin
class DownloadScheduler {
    private val maxConcurrentDownloads = 3
    private val activeDownloads = mutableSetOf<String>()
    
    fun canStartDownload(): Boolean {
        return activeDownloads.size < maxConcurrentDownloads
    }
    
    suspend fun scheduleDownload(taskId: String) {
        if (canStartDownload()) {
            activeDownloads.add(taskId)
            startDownload(taskId)
        } else {
            queueDownload(taskId)
        }
    }
}
```

### 3. Speed Limiting

**User-controlled download speed**:

```kotlin
class SpeedLimiter(private val maxBytesPerSecond: Long) {
    private var lastCheckTime = System.currentTimeMillis()
    private var bytesThisSecond = 0L
    
    suspend fun acquire(bytes: Long) {
        val now = System.currentTimeMillis()
        
        if (now - lastCheckTime >= 1000) {
            // Reset counter
            lastCheckTime = now
            bytesThisSecond = 0
        }
        
        bytesThisSecond += bytes
        
        if (bytesThisSecond > maxBytesPerSecond) {
            // Delay to limit speed
            val delayMs = 1000 - (now - lastCheckTime)
            delay(delayMs)
        }
    }
}
```

### 4. Network Monitoring

**Auto-pause on network loss**:

```kotlin
class NetworkMonitor @Inject constructor(
    private val connectivityManager: ConnectivityManager
) {
    fun observeNetworkState(): Flow<NetworkState> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(NetworkState.Available)
            }
            
            override fun onLost(network: Network) {
                trySend(NetworkState.Lost)
            }
        }
        
        connectivityManager.registerDefaultNetworkCallback(callback)
        
        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }
}
```

## UI Integration

### DownloadViewModel

**Location**: `ui/download/DownloadViewModel.kt`

```kotlin
@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository
) : ViewModel() {
    
    val downloads = downloadRepository
        .observeAllDownloads()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )
    
    fun startDownload(url: String, title: String) {
        viewModelScope.launch {
            downloadRepository.startDownload(url, title)
        }
    }
    
    fun pauseDownload(taskId: String) {
        viewModelScope.launch {
            downloadRepository.pauseDownload(taskId)
        }
    }
    
    fun resumeDownload(taskId: String) {
        viewModelScope.launch {
            downloadRepository.resumeDownload(taskId)
        }
    }
    
    fun cancelDownload(taskId: String) {
        viewModelScope.launch {
            downloadRepository.cancelDownload(taskId)
        }
    }
}
```

## Summary

ZippyPlayer's download system provides:

✅ **Multi-Protocol Support**: HTTP, HLS, Torrent
✅ **Resume Capability**: Pick up where you left off
✅ **Background Execution**: Download while using other apps
✅ **Parallel Downloads**: Multiple downloads at once
✅ **Torrent Streaming**: Watch before download completes
✅ **Network Awareness**: Auto-pause on connection loss
✅ **Progress Tracking**: Real-time download status
✅ **Reliability**: Automatic retry and recovery
✅ **Efficiency**: Optimized for battery and data usage

The system is designed to be:
- **Robust**: Handles network failures gracefully
- **Efficient**: Minimal battery and data usage
- **User-friendly**: Clear progress and controls
- **Extensible**: Easy to add new download protocols