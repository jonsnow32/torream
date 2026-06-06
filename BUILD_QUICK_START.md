# Quick Start: Build All Release Flavors
## Fastest Way to Build All Releases
```bash
./build-all-releases.sh
```
This will:
1. Clean previous builds
2. Build all 5 product flavors (arm64, arm32, x86, x86_64, universal)
3. Show you where the APKs are located
## Expected Output
```
================================================
  ZippyPlayer - Build All Release Flavors
================================================
Cleaning previous builds...
✓ Clean completed
================================================
Building arm64Release...
================================================
[...build output...]
✓ arm64Release build succeeded
[...continues for all flavors...]
================================================
  Build Summary
================================================
Successful builds (5):
  ✓ arm64
  ✓ arm32
  ✓ x86
  ✓ x86_64
  ✓ universal
================================================
  APK Locations
================================================
arm64:
  /path/to/app/build/outputs/apk/arm64/release/app-arm64-release.apk
  Size: XX MB
[...etc for all flavors...]
All builds completed successfully!
```
## Build Times (Approximate)
- **Clean build**: ~10-15 minutes for all flavors
- **Incremental build**: ~5-10 minutes for all flavors
- **Single flavor**: ~2-3 minutes
## Alternative: Parallel Build (Faster but more resource-intensive)
```bash
./gradlew assembleArm64Release assembleArm32Release assembleX86Release assembleX86_64Release assembleUniversalRelease --parallel
```
## Need Help?
See [BUILD_RELEASES_README.md](BUILD_RELEASES_README.md) for complete documentation.
