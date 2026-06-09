# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Behavioral Guidelines

1. **Think Before Coding** — State assumptions explicitly. Surface tradeoffs. If multiple interpretations exist, present them. If something is unclear, ask before implementing.
2. **Simplicity First** — Minimum code that solves the problem. No abstractions for single-use code. No speculative features or "flexibility" that wasn't requested. If you write 200 lines and it could be 50, rewrite it.
3. **Surgical Changes** — Touch only what you must. Don't refactor adjacent code. Match existing style. Remove only imports/variables that YOUR changes made unused.
4. **Goal-Driven Execution** — Define success criteria before starting. For multi-step tasks, state a brief plan with verifiable checkpoints.
5. **No Documentation** — Do not create explainer documents, READMEs, or other documentation unless explicitly asked.

---

## App Overview

**Torream** is an Android video/media player app targeting phones, tablets, and Android TV/Fire TV.

- **Package:** `cloud.streamless.torream` | **App ID:** `cloud.streamless.torream`
- **Version:** 1.0.0 (versionCode 100)
- **SDK:** minSdk 23 (Android 6) / targetSdk 36 / compileSdk 36 / JVM 21
- **Language:** Kotlin + native MPV via JNI (`.so` in `app/src/main/libs/`)

---

## Common Commands

### Build
```bash
./gradlew assembleArm64Debug          # debug APK (fast, arm64 only)
./gradlew assembleArm64Release        # single flavor release APK
./gradlew bundleUniversalRelease      # AAB for Play Store upload
./build-all-releases.sh               # all 5 ABI release APKs
```

### Test & Lint
```bash
./gradlew testUniversalDebugUnitTest                          # all unit tests
./gradlew testUniversalDebugUnitTest --tests "*.FooTest"      # single test class
./gradlew lintUniversalDebug                                  # lint (has baseline)
```

> Use `lintUniversalDebug` not `lintDebug` — the latter is ambiguous with 5 flavors.
> Lint issues are baselined in `app/lint-baseline.xml`; only new issues fail the build.

### Signing (local)
Create `key.properties` in the project root (git-ignored):
```
storeFile=/path/to/keystore.jks
storePassword=...
keyAlias=...
keyPassword=...
```
For CI, pass as env vars: `STORE_FILE`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

---

## Fastlane

Ruby 3.4+ required. The macOS system Ruby (2.6) is too old — use Homebrew's:
```bash
export PATH="/opt/homebrew/opt/ruby/bin:$PATH"   # add to ~/.zshrc to make permanent
bundle install                                    # first time only
```

Always run fastlane via `bundle exec`:
```bash
PLAY_KEY_FILE=/path/to/service-account.json bundle exec fastlane <lane>
```

| Lane | Purpose |
|---|---|
| `test` | Unit tests + lint |
| `build_debug` | Debug APK |
| `build_release` | Signed universal AAB |
| `upload_internal` | Upload AAB → internal track (no build) |
| `upload_alpha` | Upload AAB → alpha track (no build) |
| `deploy_internal` | test + build_release + upload_internal |
| `deploy_alpha` | test + build_release + upload_alpha |
| `deploy_production` | Promote internal → production |
| `upload_metadata` | Upload store images only (no APK/AAB) |

Store assets live in `fastlane/metadata/android/en-US/images/`. The `PLAY_KEY_FILE` env var points to the Google service account JSON key.

---

## Architecture

**Pattern:** Single-Activity MVVM with Navigation Component.

- `MainActivity` hosts all UI; bottom nav (portrait) and nav rail (landscape/TV).
- Jetpack Navigation graph: `app/src/main/res/navigation/mobile_navigation.xml`
- DI: Dagger/Hilt 2.57.2 — entry point is `@AndroidEntryPoint` on all Activities/Fragments.
- Database: Room (`MediaDatabase`) — tables: Media, Folder, Playlist, PlaylistItem, MediaPlayback, Download, Torrent, Favorite, Http.
- Settings/prefs: Jetpack DataStore (`datastore/`) + SharedPreferences for simpler keys.
- Networking: NiceHttp (OkHttp wrapper) with DoH support; global `app` instance in `MainActivity.kt`.
- Serialization: `kotlinx.serialization` (JSON).
- Paging: Paging 3 for feed and library lists.
- Logging: Timber throughout.

---

## Key Screens & Packages

| Screen | Fragment | ViewModel |
|---|---|---|
| Home feed | `ui/home/FeedFragment` | `FeedViewModel` |
| Library | `ui/library/LibraryFragment` | `LibraryViewModel` |
| Browse | `ui/browse/BrowseFragment` | `BrowseViewModel` |
| Settings | `ui/settings/SettingsFragment` | — |
| Player | `ui/player/MPVFragment` | `PlayerViewModel` |

Player hides the nav bar entirely while active (`navView.isGone = true`).

---

## Player System

The core player is **MPV** (native, via JNI in `ui/player/mpv/`).

- `MPVView` / `BaseMPVView` — SurfaceView wrapping the native mpv render context.
- `MPVFragment` delegates to sub-managers to keep the class manageable:
  - `PlayerUIController` — controls visibility, overlays, animations
  - `PlayerGestureHandler` — swipe for brightness/volume/seek, double-tap
  - `PlayerAudioManager` — audio track selection, volume
  - `PlayerDialogManager` — subtitle/audio/speed dialogs
  - `PlayerMediaManager` — playlist, episode navigation
  - `PipActionManager` — Picture-in-Picture lifecycle
- Background audio: `BackgroundPlaybackService` (foreground service + MediaSession).
- Double-tap seek overlay: `YouTubeOverlay` in `ui/player/youtube/`.
- Chromecast playback: `media3-cast` + `ChromecastSubtitlesFragment`.
- Inter-app launch: `ACTION_VIEW` with `content://` URI carrying a `PlaybackData` JSON file; key `KEY_PLAYBACK_JSON_URI` in bundle.

---

## Download System

Downloads are WorkManager workers coordinated by `DownloadCoordinator`.

| Worker | Purpose |
|---|---|
| `HttpDownloadWorker` | Plain HTTP/HTTPS file download |
| `HlsDownloadWorker` | HLS stream download |
| `TorrentDownloadWorker` | Torrent download via libtorrent4j |

- `TorrentStreamServer` (NanoHTTPD) enables in-app torrent streaming before full download.
- `DownloadRecoveryManager` resumes downloads interrupted by process death.
- Notifications: `DownloadNotificationHelper` + `DownloadNotificationReceiver`.
- Intent `cloud.streamless.torream.OPEN_DOWNLOADS` navigates to the Downloads section in Library.
- Intent `.action.DOWNLOAD_URL` opens `UrlInputDialog` with URL + custom headers.

---

## Ads System

Waterfall mediation via `AdWaterfallManager`.

- Providers: `AdMobProvider`, `UnityAdProvider`, `VungleProvider`, `HouseAdProvider`.
- `AdPreloadManager` / `AdPreloader` pre-loads ads before they are needed.
- `AdPlacementHelper` decides when/where ads appear.
- SDKs: Google AdMob 24, IronSource 9.2, AppLovin 13.5, Unity Ads 4.16, Vungle 7.6.
- AdMob IDs injected at build time via `local.properties` or env vars (`ADMOB_APP_ID`, etc.); test IDs used as fallback.

---

## Subtitle System

- `MPVSubtitleFragment` — internal subtitle picker for MPV player.
- `ChromecastSubtitlesFragment` — subtitle picker for cast sessions.
- `SubtitleHelper` — loads and converts subtitle files.
- Caption styling persisted in `SaveCaptionStyle`.
- External subtitle sources: `AbstractSubtitleEntities` / `SubtitleFile`.

---

## TV / Remote Control Support

- Detect TV/emulator: `isTvOrEmulator()` in `utils/`.
- Nav rail replaces bottom nav on TV.
- Custom DPAD focus routing in `MainActivity.dispatchKeyEvent()`.
- Player key bindings (handled in `MainActivity.onKeyDown()`):
  - D/Forward/FastForward → seek forward
  - A/Rewind → seek back
  - N/R1 → next episode; B/L1 → prev episode
  - P/Space/Enter → play/pause
  - L/7 → lock; H/Menu → toggle HUD; M → mute
  - S/9 → mirrors; O/8 → subtitles; E/3 → speed; R/0 → resize; C/4 → skip OP

---

## Build & Flavors

5 ABI product flavors, each producing a separate APK:

| Flavor | ABI | versionCode |
|---|---|---|
| arm64 | arm64-v8a | 1002 |
| arm32 | armeabi-v7a | 1001 |
| x86 | x86 | 1003 |
| x86_64 | x86_64 | 1004 |
| universal | all | 100 (base) |

- Debug: arm64-v8a only, ID suffix `.debug`, app name "Torream-Debug".
- Release: ProGuard + resource shrinking enabled.
- `universal` flavor is used for Play Store AAB uploads; it inherits the base versionCode (100) with no suffix.

---

## Utility Highlights

- `CommonActivitty` (note spelling) — static listeners/helpers shared across activities via lambdas (`playerEventListener`, `keyEventListener`, `activityResultEvent`).
- `UIHelper` — navigation helper, theme utils, focus helpers.
- `GlobalEvent` — app-wide event callbacks (color selection, dialog dismissal).
- `SafeFile` (LagradOst) — SAF-compatible file abstraction.
- `ThumbnailLoader` + `ThumbnailCache` — video thumbnail loading via Coil.
- `FcastHelper` / `CastHelper` — FCAST protocol + Chromecast helpers.
- `InAppUpdater` — auto-update from GitHub releases.
- Rhino JS engine (`org.mozilla:rhino`) — used for scripting/parsing.
- juniversalchardet — charset detection for subtitle files.

---

## Custom Skills

### /video-architect
Invoke with `/video-architect <question>` to activate the elite Video Player Architect persona.
Covers: ExoPlayer/Media3, HLS/DASH, adaptive bitrate, subtitle systems, Android TV, DRM, OTT architecture, performance optimization, monetization.

---

## Subagents

Pick the cheapest model that can do the subtask well: Haiku for bulk mechanical work, Sonnet for scoped research/synthesis, Opus for subtasks needing real planning. Parent owns final output.

## Preferred Tools

**WebFetch** for public pages; **agent-browser CLI** (`npm i -g agent-browser && agent-browser install`) for dynamic pages or auth-walled content — returns accessibility tree with element refs, ~82% fewer tokens than screenshot-based tools.
