---
sidebar_position: 4
---

# Player Architecture

Torream's video player is built on top of modern media playback technologies, providing a feature-rich viewing experience with advanced controls and optimizations.

## Player Components

### MPVFragment - Primary Player

**Location**: `ui/player/mpv/MPVFragment.kt`

The main video player implementation that handles all playback functionality.

**Key Features**:
- Custom player controls overlay
- Gesture-based controls (swipe for volume, brightness, seeking)
- Picture-in-Picture (PiP) support
- Subtitle rendering with customization
- Multiple audio track support
- Playback speed control (0.25x - 2x)
- Background playback
- Lock screen controls

### Player ViewModel

**Location**: `ui/player/PlayerViewModel.kt`

Manages player state and coordinates playback operations.

**State Management**:
```kotlin
data class PlayerState(
    val isPlaying: Boolean,
    val currentPosition: Long,
    val duration: Long,
    val bufferedPosition: Long,
    val playbackSpeed: Float,
    val currentQuality: VideoQuality?,
    val availableQualities: List<VideoQuality>,
    val subtitleTracks: List<SubtitleTrack>,
    val audioTracks: List<AudioTrack>,
    val currentSubtitle: SubtitleTrack?,
    val currentAudio: AudioTrack?
)
```

## Media Source Handling

### Supported Formats

**Video Codecs**:
- H.264/AVC
- H.265/HEVC
- VP9
- AV1

**Audio Codecs**:
- AAC
- MP3
- Opus
- AC3/E-AC3

**Container Formats**:
- MP4
- MKV
- WebM
- AVI
- FLV

**Streaming Protocols**:
- HTTP/HTTPS
- HLS (m3u8)
- DASH
- Local files
- Torrent streaming (via local HTTP server)

## Player Controls

### Gesture Controls

**Horizontal Swipe**: Seek forward/backward
**Vertical Swipe (Left)**: Adjust brightness
**Vertical Swipe (Right)**: Adjust volume
**Double Tap (Left)**: Rewind 10 seconds
**Double Tap (Right)**: Forward 10 seconds
**Single Tap**: Show/hide controls
**Pinch**: Aspect ratio adjustment

### Control Bar

**Components**:
- Play/Pause button
- Seek bar with thumbnail preview (PreviewSeekBar)
- Current time / Total duration
- Quality selector
- Subtitle selector
- Audio track selector
- Playback speed selector
- Fullscreen toggle
- Chromecast button
- Settings menu
- Lock button (prevents accidental touches)

## Advanced Features

### 1. Thumbnail Preview

Uses `PreviewSeekBar` for seeking with thumbnail preview:

```kotlin
binding.seekBar.setPreviewLoader { position ->
    // Load thumbnail at specific position
    thumbnailGenerator.getThumbnail(videoUri, position)
}
```

### 2. Adaptive Streaming

Automatically adjusts quality based on:
- Network bandwidth
- Buffer status
- Device capabilities

### 3. Subtitle Support

**Features**:
- External subtitle files (SRT, ASS, VTT)
- Embedded subtitles
- Multiple subtitle tracks
- Customizable subtitle styling:
  - Font size
  - Font color
  - Background color
  - Text position

**Implementation**:
```kotlin
class SubtitleRenderer {
    fun applySubtitleStyling(style: SubtitleStyle) {
        // Apply custom styles to subtitle view
    }
    
    fun loadExternalSubtitle(file: File) {
        // Parse and load subtitle file
    }
}
```

### 4. Picture-in-Picture (PiP)

**Android 8.0+** support:

```kotlin
fun enterPiPMode() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .build()
        activity?.enterPictureInPictureMode(params)
    }
}
```

### 5. Chromecast Integration

**Media3 Cast Extension**:

```kotlin
class CastHelper {
    fun startCasting(mediaItem: MediaItem) {
        val castContext = CastContext.getSharedInstance(context)
        val session = castContext.sessionManager.currentCastSession
        
        session?.remoteMediaClient?.load(
            MediaLoadRequestData.Builder()
                .setMediaInfo(buildMediaInfo(mediaItem))
                .build()
        )
    }
}
```

## Player Settings

### User Preferences

**Playback Settings**:
- Default playback speed
- Auto-play next video
- Resume position on reopen
- Skip intro/outro duration

**Quality Settings**:
- Preferred quality
- Data saver mode
- Auto quality switching

**Subtitle Settings**:
- Default subtitle language
- Subtitle delay adjustment
- Font size and style

**Audio Settings**:
- Default audio language
- Audio boost
- Audio delay adjustment

### Settings Storage

Uses DataStore for persistent settings:

```kotlin
class PlayerPreferences(private val dataStore: DataStore<Preferences>) {
    val playbackSpeed = dataStore.data.map { it[PLAYBACK_SPEED] ?: 1.0f }
    val preferredQuality = dataStore.data.map { it[PREFERRED_QUALITY] }
    // ...
}
```

## Performance Optimizations

### 1. Efficient Buffering

**Strategies**:
- Adaptive buffer sizing
- Preload buffer for smooth playback
- Clear buffer on seek

### 2. Memory Management

**Techniques**:
- Release player on pause
- Recycle bitmaps for thumbnails
- Limit buffer cache size

### 3. Battery Optimization

**Features**:
- Reduce frame rate when on battery
- Lower quality in power saver mode
- Disable heavy processing

## Error Handling

### Playback Errors

```kotlin
sealed class PlayerError {
    object NetworkError : PlayerError()
    object SourceNotFound : PlayerError()
    object UnsupportedFormat : PlayerError()
    object DrmError : PlayerError()
    data class Unknown(val message: String) : PlayerError()
}
```

**Recovery Strategies**:
1. Automatic retry with exponential backoff
2. Quality downgrade on buffering issues
3. Switch to alternative source if available
4. User notification for unrecoverable errors

## Integration with Other Components

### Download System Integration

Play downloaded files directly:
```kotlin
fun playDownloadedFile(downloadTask: DownloadTask) {
    val uri = Uri.fromFile(File(downloadTask.filePath))
    playVideo(uri)
}
```

### Torrent Streaming Integration

Play while downloading:
```kotlin
fun playTorrentStream(torrentHandle: TorrentHandle) {
    val streamUrl = torrentStreamingService.getStreamUrl(torrentHandle)
    playVideo(Uri.parse(streamUrl))
}
```

## Testing

**Test Coverage**:
- Unit tests for PlayerViewModel
- UI tests for player controls
- Integration tests for playback scenarios
- Performance tests for buffer management

## Summary

Torream's player architecture provides:

✅ **Feature-Rich Controls**: Gestures, PiP, Chromecast
✅ **Format Support**: Wide range of codecs and containers
✅ **Adaptive Streaming**: Automatic quality adjustment
✅ **Subtitle Support**: Multiple formats with styling
✅ **Performance**: Optimized for smooth playback
✅ **Reliability**: Robust error handling and recovery
✅ **Integration**: Seamless with download and streaming
