# csplayer

## Google Play Compliance

### Target API Level Requirements ✅
- **Target SDK**: API 35 (Android 15) - Compliant with Google Play's requirement (must be within 1 year of latest Android release)
- **Compile SDK**: API 35 (Android 15)
- **Min SDK**: API 21 (Android 5.0)
- **Deadline**: August 31, 2025 - **COMPLIANT**

### 16 KB Page Size Support ✅
- **NDK Requirement**: Android NDK r25 or newer (r27b recommended)
- **Native Libraries**: All native dependencies (ffmpeg, mpv, etc.) are built with `-Wl,-z,max-page-size=16384` flag
- **Deadline**: November 1, 2025 - **COMPLIANT**

## NDK and 16 KB Page Size Support

- **NDK Requirement:** This project requires Android NDK r25 or newer (r27b recommended) to ensure compatibility with devices using 16 KB memory page sizes (such as ARMv9 devices running Android 14+).
- **Native Libraries:** All native dependencies (e.g., ffmpeg, mpv, etc.) must be built using NDK r25+.
- **Testing:** Please test the app on an emulator or device with a 16 KB page size to verify correct operation.

For more details, see the build scripts in `buildscripts/`.
