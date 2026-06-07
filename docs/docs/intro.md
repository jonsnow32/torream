---
sidebar_position: 1
---

# Introduction to Torream

Welcome to **Torream**, a powerful and feature-rich video player application designed for Android devices and Android TV. Torream combines advanced media playback capabilities with robust download management and torrent support to provide a comprehensive media consumption experience.

## What is Torream?

Torream is a modern Android application that serves as an all-in-one media solution, offering:

- **Advanced Video Playback**: Built on Media3 (ExoPlayer) for high-quality, adaptive streaming
- **Download Management**: Sophisticated multi-protocol download system supporting HTTP, HLS, and torrent downloads
- **Media Library**: Organized content management with playlist support and favorites
- **Android TV Support**: Fully optimized user interface for large screens with leanback mode
- **Chromecast Integration**: Stream your content to any Chromecast-enabled device
- **Torrent Support**: Built-in torrent client with streaming capabilities using LibTorrent4j

## Key Highlights

### 🎯 Multi-Platform Support
- **Mobile**: Optimized for smartphones and tablets (Android 6.0+)
- **Android TV**: Native support with D-pad navigation and optimized UI
- **Chromecast**: Cast your media to big screens seamlessly

### ⚡ Performance & Architecture
- Built with **Kotlin** using modern Android development practices
- **MVVM Architecture** with Repository pattern for clean code organization
- **Dependency Injection** with Hilt/Dagger for scalable architecture
- **Jetpack Components**: Lifecycle, LiveData, ViewModel, Navigation, Room, WorkManager

### 🚀 Advanced Features
- **Multiple Download Methods**: HTTP, HLS segmented downloads, and torrent support
- **Background Downloads**: WorkManager integration for reliable background operations
- **Video Thumbnails**: Coil-powered efficient thumbnail generation and caching
- **Adaptive Streaming**: Automatic quality adjustment based on network conditions
- **Subtitle Support**: Multiple subtitle formats with customization options

### 🎨 Modern UI/UX
- Material Design 3 components
- Edge-to-edge display support
- Customizable themes and color schemes
- Smooth animations and transitions
- Fast scrolling with optimized RecyclerView

## Target Audience

Torream is designed for:
- Media enthusiasts who want a feature-rich video player
- Users who need robust download management capabilities
- Android TV users seeking a comprehensive media center
- Users who want torrent streaming without waiting for complete downloads
- Developers looking to learn modern Android architecture patterns

## Technology Stack

Torream is built using cutting-edge Android technologies:

- **Language**: Kotlin with Java 21 compatibility
- **UI Framework**: Android Jetpack, Material Design 3
- **Media**: Media3 (ExoPlayer) for playback, Chromecast SDK
- **Architecture**: MVVM, Repository Pattern, Clean Architecture principles
- **DI**: Hilt/Dagger for dependency injection
- **Database**: Room for local data persistence
- **Networking**: OkHttp, Retrofit-like clients (NiceHttp)
- **Async**: Kotlin Coroutines, Flow, LiveData
- **Background Work**: WorkManager for downloads
- **Torrent**: LibTorrent4j with multi-architecture support
- **Image Loading**: Coil with video thumbnail support

## Getting Started

To start using or contributing to Torream:

1. **For Users**: Download the latest APK from releases
2. **For Developers**: Clone the repository and follow the [Getting Started Guide](./getting-started.md)
3. **For Documentation**: Explore the [Technical Stack](./technical-stack.md) and [Architecture](./architecture/overview.md) sections

## Project Status

**Current Version**: 1.1.9 (Build 119)
- **Target SDK**: Android 36
- **Minimum SDK**: Android 23 (Android 6.0)
- **Build Tool**: Gradle with Kotlin DSL
- **Supported Architectures**: ARM32, ARM64, x86, x86_64, Universal

## What's Next?

Explore the documentation to learn more about:
- [Technical Stack](./technical-stack.md) - Detailed breakdown of technologies used
- [Architecture](./architecture/overview.md) - System design and component structure
- [Features](./features/overview.md) - In-depth feature documentation
- [FAQ](./faq.md) - Common questions and troubleshooting

---

*Torream - Your Ultimate Media Companion for Android*