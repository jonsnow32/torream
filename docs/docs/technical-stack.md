---
sidebar_position: 3
---

# Technical Stack

Torream is built using modern Android development technologies and best practices. This document provides a comprehensive breakdown of all technologies, libraries, and frameworks used in the project.

## Project Overview

- **Package Name**: `cloud.streamless.torream`
- **Version**: 1.1.9 (Build 119)
- **Min SDK**: 23 (Android 6.0 Marshmallow)
- **Target SDK**: 36
- **Compile SDK**: 36
- **Language**: Kotlin
- **Java Version**: 21

## Platform & Build Configuration

### Build System
- **Gradle**: Kotlin DSL (build.gradle.kts)
- **Android Gradle Plugin**: Latest version
- **Kotlin Gradle Plugin**: With KSP (Kotlin Symbol Processing)
- **Parcelize Plugin**: For efficient Parcelable implementations

### Multi-Architecture Support
The app builds separate APKs for different CPU architectures to optimize size:
- **ARM 32-bit** (armeabi-v7a): For older devices
- **ARM 64-bit** (arm64-v8a): For modern devices (primary)
- **x86**: For Intel emulators and tablets
- **x86_64**: For Intel 64-bit devices
- **Universal**: All architectures combined

### Localization
- Supported languages: English, Spanish, Chinese
- Resource filtering to reduce APK size

## Core Technologies

### Programming Language
- **Kotlin**: 100% Kotlin codebase with modern language features
  - Coroutines for async operations
  - Flow for reactive streams
  - Extension functions
  - Data classes
  - Sealed classes for type-safe state management

### Dependency Injection
- **Hilt (Dagger)** `2.57.2`: Modern DI framework
  - Application-level components
  - ViewModel injection
  - WorkManager integration with Hilt
  - Module organization by feature

## Architecture Components

Complete AndroidX Jetpack library integration:

### Core Libraries
- **Core KTX** `1.17.0`: Kotlin extensions for Android APIs
- **AppCompat** `1.7.1`: Backward compatibility
- **Activity KTX** `1.12.2`: Modern activity APIs with edge-to-edge support
- **Fragment** (via Navigation): Fragment management
- **Material Components** `1.13.0`: Material Design 3

### Lifecycle & ViewModels
- **Lifecycle LiveData KTX** `2.10.0`: Lifecycle-aware observables
- **Lifecycle ViewModel KTX** `2.10.0`: Scoped ViewModels
- **Lifecycle ViewModel SavedState** `2.10.0`: Persistent ViewModel state

### Navigation
- **Navigation Fragment KTX** `2.9.6`: Fragment navigation
- **Navigation UI KTX** `2.9.6`: UI integration with navigation

### Data Persistence
- **Room** `2.8.3`: SQLite database abstraction
  - `room-runtime`: Core Room library
  - `room-ktx`: Kotlin extensions with Coroutines support
  - `room-compiler` (KSP): Compile-time code generation

### Data Loading & Pagination
- **Paging 3** `3.3.6`: Efficient large dataset loading
  - PagingSource for data loading
  - PagingData for reactive updates
  - Integration with Room and network sources

### Preferences & Settings
- **Preference KTX** `1.2.1`: Modern preference screens
- **DataStore**: Type-safe data storage (via custom implementation)

### Background Processing
- **WorkManager** `2.10.0`: Deferrable, guaranteed background tasks
  - Used for download management
  - Hilt integration for dependency injection
  - Constraint-based task execution
- **Hilt Work** `1.2.0`: Hilt integration for WorkManager
- **Hilt Compiler** `1.2.0`: Code generation for Workers

### File Management
- **DocumentFile** `1.1.0`: Modern file access
- **SafeFile** `0.0.8`: Wrapper for Storage Access Framework
  - Simplified file operations
  - SAF compatibility layer

### UI Components
- **SwipeRefreshLayout** `1.2.0`: Pull-to-refresh pattern
- **FastScroll** `1.3.0`: Fast scrolling for large lists
- **RecyclerView** (via AndroidX): Efficient list rendering

## Media & Video Playback

### Media3 (ExoPlayer) `1.9.0`
Modern media playback framework:
- **media3-ui**: Player UI components
- **media3-cast**: Chromecast integration
- **media3-common**: Core media types and utilities
- **media3-session**: Media session management
- ~~media3-exoplayer~~: Core player (commented in dependencies)
- ~~media3-exoplayer-hls~~: HLS streaming (commented)
- ~~media3-exoplayer-dash~~: DASH streaming (commented)
- ~~media3-datasource-okhttp~~: OkHttp integration (commented)

Note: Some Media3 dependencies are commented out, suggesting custom implementations or alternative approaches.

### Custom Player Components
- **PreviewSeekBar Media3** `1.1.1.0`: Thumbnail preview during seeking
- **MPV Player**: Alternative player implementation (custom)
- **YouTube Player**: Custom YouTube integration

## Networking & Downloads

### HTTP Client
- **NiceHttp** `0.4.13`: Custom HTTP client based on OkHttp
  - Cookie management
  - Custom headers
  - Request/response interceptors

### Download System
Custom implementation supporting:
- HTTP direct downloads
- HLS segmented downloads
- Torrent downloads
- Parallel download management

### Serialization
- **Kotlinx Serialization JSON** `1.9.0`: Type-safe JSON parsing
  - Compile-time serialization
  - Better performance than Gson/Moshi

## Torrent Support

### LibTorrent4j `2.1.0-38`
Complete torrent client implementation:
- **libtorrent4j**: Core Java bindings
- **libtorrent4j-android-arm**: ARM 32-bit native library
- **libtorrent4j-android-arm64**: ARM 64-bit native library
- **libtorrent4j-android-x86**: x86 native library
- **libtorrent4j-android-x86_64**: x86_64 native library

### Torrent Streaming
- **NanoHTTPD** `2.3.1`: Lightweight HTTP server
  - Serves torrent content as HTTP stream
  - Enables playback before download completion
  - Local streaming server for player integration

## Network Share Browsing

### Protocol Clients
- **smbj** `0.14.0`: Pure-Java SMB2/3 client
  - No native code — avoids cross-compiling `libsmbclient`
  - Used for both folder listing and file reads
  - Backed by a local NanoHTTPD proxy (mirrors the torrent streaming server pattern) so the player can read SMB files without native protocol support
- **Apache Commons Net** `3.11.1`: FTP client for folder listing
  - FTP playback itself goes directly through the app's media engine (built-in protocol support)
- **WebDAV**: Custom lightweight client (OkHttp + `XmlPullParser`)
  - PROPFIND-based folder listing, no extra dependency
  - Playback goes directly over `http(s)://` with Basic Auth headers

### Credential Storage
- **Android Keystore**: Hardware-backed AES/GCM encryption for saved share passwords
  - Keys never leave secure hardware; passwords decrypted only transiently in memory

## Image Loading

### Coil `2.7.0`
Modern image loading library:
- **coil**: Core library with Kotlin Coroutines
- **coil-video**: Video frame extraction for thumbnails
  - Efficient thumbnail generation
  - Memory caching
  - Disk caching with LRU eviction

## Advertising SDKs

Multiple ad network integrations:
1. **Google AdMob** `24.9.0`: Google's ad platform
2. **IronSource SDK** `9.2.0`: Ad mediation platform
3. **AppLovin SDK** `13.5.1`: Ad monetization
4. **Unity Ads** `4.16.5`: Unity's ad network
5. **Vungle Ads** `7.6.3`: Video ad platform

### Ad Management System
Custom ad waterfall implementation:
- Ad preloading
- Network failover
- Placement-based display
- Frequency capping

## Utilities

### JavaScript Engine
- **Rhino** `1.8.0`: JavaScript engine for Android
  - Web scraping support
  - Dynamic content extraction

### Character Detection
- **JUniversalChardet** `2.5.0`: Character encoding detection
  - Automatic encoding detection for subtitles
  - International content support

## Testing

### Unit Testing
- **JUnit** `4.13.2`: Unit testing framework
  - Test coroutines
  - Test Room DAOs
  - Test ViewModels

### Instrumentation Testing
- **AndroidX Test JUnit** `1.3.0`: Android testing extensions
- **Espresso Core** `3.7.0`: UI testing framework
  - UI automation
  - View interactions
  - IdlingResources for async operations

## Logging

### Timber `5.0.1`
- Extensible logging API
- Tag-based filtering
- Debug/release log differentiation
- Crash reporting integration ready

## Key Features Enabled by Tech Stack

### 1. Advanced Video Playback
- **Media3**: Adaptive streaming, subtitles, audio tracks
- **Custom controls**: Gestures, brightness/volume control
- **Picture-in-Picture**: Background playback support
- **Chromecast**: Remote media casting

### 2. Robust Download Management
- **WorkManager**: Reliable background downloads
- **Multi-protocol support**: HTTP, HLS, torrents
- **Progress tracking**: Real-time download status
- **Auto-retry**: Network failure recovery

### 3. Efficient Data Management
- **Room**: Local media library
- **Paging 3**: Memory-efficient list loading
- **Coil**: Fast image/thumbnail loading

### 4. Modern Architecture
- **MVVM pattern**: Separation of concerns
- **Repository pattern**: Centralized data access
- **Single source of truth**: Consistent state management
- **Reactive programming**: Flow/LiveData for UI updates

### 5. Torrent Streaming
- **LibTorrent4j**: Full torrent client
- **NanoHTTPD**: Sequential streaming
- **Partial downloads**: Play while downloading

### 6. Cross-Device Support
- **Android TV**: Leanback UI, D-pad navigation
- **Mobile**: Touch-optimized interfaces
- **Tablets**: Adaptive layouts

## Architecture Pattern

### MVVM with Repository Pattern
```
UI Layer (Fragments/Activities)
    ↓
ViewModel Layer
    ↓
Repository Layer (Single Source of Truth)
    ↓ ↓
Local Data Source (Room) + Remote Data Source (Network)
```

### Clean Architecture Principles
- **Separation of concerns**: Each layer has specific responsibility
- **Dependency rule**: Inner layers don't depend on outer layers
- **Testability**: Easy to mock dependencies
- **Maintainability**: Clear structure and organization

## Code Quality & Optimization

### Release Build Optimization
- **ProGuard**: Code obfuscation and minification
- **Resource shrinking**: Removes unused resources
- **ABI splits**: Separate APKs per architecture
- **Locale filtering**: Reduces APK size

### Debug Build Features
- **Timber logging**: Detailed logs for debugging
- **No obfuscation**: Readable stack traces
- **Debug symbols**: Native crash reporting
- **ARM64 only**: Faster build times

### Performance Features
- **Kotlin Coroutines**: Efficient async operations
- **Flow**: Backpressure-aware streams
- **ViewBinding**: Type-safe view access (no findViewById)
- **R8/ProGuard**: Dead code elimination

## Compliance & Standards

### Google Play Compliance
- **16 KB page size support**: Future Android compatibility
- **Scoped storage**: Privacy-compliant file access
- **Background restrictions**: Battery-friendly operations
- **Notification permissions**: User-controlled notifications

### Security
- **HTTPS**: Secure network communications
- **ProGuard**: Code obfuscation in release builds
- **Permission runtime requests**: User privacy
- **SafeFile**: Secure file operations
- **Android Keystore**: Encrypted storage for network share credentials

## Summary

Torream leverages a comprehensive technology stack that combines:
- **Modern Android development** with Jetpack components
- **Efficient media playback** with Media3/ExoPlayer
- **Advanced download capabilities** with WorkManager and custom implementations
- **Torrent support** with LibTorrent4j for streaming
- **Clean architecture** for maintainability and testability
- **Multi-platform optimization** for phones, tablets, and Android TV

This stack enables Torream to deliver a feature-rich, performant, and reliable media experience across all Android devices.

## Version Information

**Last Updated**: Build 119 (Version 1.1.9)
**Gradle**: Kotlin DSL
**Build Date**: Embedded in BuildConfig
**ABI Support**: Universal (all architectures)
