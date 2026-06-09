---
sidebar_position: 2
---

# Getting Started

This guide will help you set up the Torream development environment and get the project running on your machine.

## Prerequisites

Before you begin, ensure you have the following installed:

### Required Tools
- **Android Studio**: Latest version (Electric Eel or newer recommended)
- **JDK**: Java 21 (as specified in the project configuration)
- **Android SDK**: 
  - Minimum SDK: 23 (Android 6.0)
  - Target/Compile SDK: 36
- **Git**: For version control

### Recommended
- **Physical Android Device** or **Android TV device** for testing
- At least **8GB RAM** for smooth development experience
- **SSD storage** for faster builds

## Project Setup

### 1. Clone the Repository

```bash
git clone https://github.com/jonsnow32/torream.git
cd torream
```

### 2. Configure Local Properties

Create a `local.properties` file in the project root if it doesn't exist:

```properties
# Android SDK location
sdk.dir=/path/to/your/Android/sdk

# Test ID for ads (optional)
test.id=YOUR_TEST_ID_HERE
```

### 3. Open in Android Studio

1. Launch Android Studio
2. Click **File → Open**
3. Navigate to the cloned `torream` directory
4. Click **OK** and wait for Gradle sync to complete

### 4. Sync Project Dependencies

Android Studio should automatically sync Gradle dependencies. If not:
- Click **File → Sync Project with Gradle Files**
- Or use the elephant icon in the toolbar

## Build Configurations

Torream supports multiple build variants:

### ABI Flavors

The project is configured to build separate APKs for different CPU architectures:

- **arm64**: ARM 64-bit (most modern devices)
- **arm32**: ARM 32-bit (older devices)
- **x86**: Intel 32-bit (emulators)
- **x86_64**: Intel 64-bit (emulators)
- **universal**: All architectures (larger APK)

### Build Types

- **debug**: Development build with debugging enabled
  - Application ID suffix: `.debug`
  - Includes debug symbols
  - No code obfuscation
  - Only includes ARM64 ABI for faster builds
  
- **release**: Production build
  - Code minification enabled (ProGuard)
  - Resource shrinking enabled
  - Optimized for size and performance

## Building the App

### From Android Studio

1. Select build variant: **Build → Select Build Variant**
2. Choose your desired combination (e.g., `arm64Debug`)
3. Click **Run** (▶️) or use `Shift + F10`

### From Command Line

```bash
# Build debug APK (ARM64)
./gradlew assembleArm64Debug

# Build release APK (ARM64)
./gradlew assembleArm64Release

# Build all variants
./gradlew assembleRelease

# Build universal APK
./gradlew assembleUniversalRelease

# Install debug build to connected device
./gradlew installArm64Debug
```

## Project Structure

```
torream/
├── app/                          # Main application module
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/cloud/streamless/torream/
│   │   │   │   ├── ads/          # Ad integration
│   │   │   │   ├── datastore/    # Data storage
│   │   │   │   ├── download/     # Download system
│   │   │   │   ├── media/        # Media database & repository
│   │   │   │   ├── model/        # Data models
│   │   │   │   ├── network/      # Networking utilities
│   │   │   │   ├── ui/           # UI components
│   │   │   │   └── utils/        # Utility classes
│   │   │   ├── res/              # Resources
│   │   │   └── AndroidManifest.xml
│   │   └── test/                 # Unit tests
│   └── build.gradle.kts          # App-level build config
├── build.gradle.kts               # Project-level build config
├── settings.gradle.kts            # Gradle settings
└── gradle.properties              # Gradle properties
```

## Development Workflow

### 1. Running on Emulator

1. Create an Android Virtual Device (AVD):
   - Go to **Tools → Device Manager**
   - Click **Create Device**
   - Select a device definition (Pixel 4 or Android TV recommended)
   - Choose a system image (API 23+)
   - Click **Finish**

2. Start the emulator and run the app

### 2. Running on Physical Device

1. Enable Developer Options on your Android device:
   - Go to **Settings → About Phone**
   - Tap **Build Number** 7 times

2. Enable USB Debugging:
   - Go to **Settings → Developer Options**
   - Enable **USB Debugging**

3. Connect device via USB and run the app

### 3. Testing on Android TV

For Android TV testing:
- Use Android TV emulator (recommended: API 29+)
- Or use a physical Android TV device/box
- Enable **ADB over network** for wireless debugging

## Common Issues & Solutions

### Gradle Sync Failed

**Issue**: Gradle sync fails with dependency errors

**Solution**:
```bash
# Clear Gradle cache
./gradlew clean
./gradlew --stop

# Invalidate caches in Android Studio
# File → Invalidate Caches → Invalidate and Restart
```

### Build Fails with "Execution failed for task"

**Issue**: Build fails during compilation

**Solution**:
- Check your JDK version (must be Java 21)
- Ensure Android SDK is properly installed
- Update Android Studio to the latest version
- Check `local.properties` file paths

### Out of Memory Error

**Issue**: Build fails with OutOfMemoryError

**Solution**: Edit `gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx4096m -XX:MaxMetaspaceSize=512m
```

### LibTorrent4j Native Library Issues

**Issue**: Runtime crashes related to LibTorrent4j

**Solution**:
- Ensure you're building/running on a supported architecture
- For emulators, use x86_64 flavor
- Check that JNI libraries are properly packaged

## Next Steps

Now that you have the project running:

1. **Explore the Architecture**: Read [Architecture Overview](./architecture/overview.md)
2. **Understand Components**: Check [Core Components](./architecture/core-components.md)
3. **Review Features**: Browse [Features Documentation](./features/overview.md)
4. **Code Style**: Follow Kotlin coding conventions and Android best practices

## Development Tools

### Recommended Plugins

Install these Android Studio plugins for better productivity:
- **Kotlin Fill Class** - Quick property initialization
- **ADB Idea** - ADB commands from IDE
- **Rainbow Brackets** - Bracket pair colorization
- **Material Theme UI** - Better IDE appearance

### Debugging Tips

- Use **Logcat** with filters: `cloud.streamless.torream`
- Enable **Timber** logging in debug builds
- Use **Layout Inspector** for UI debugging
- Profile with **Android Profiler** for performance issues

## Contributing

Ready to contribute? Check out:
- Code style guidelines
- Pull request process
- Testing requirements
- Documentation standards

---

**Need Help?** Check the [FAQ](./faq.md) or open an issue on GitHub.