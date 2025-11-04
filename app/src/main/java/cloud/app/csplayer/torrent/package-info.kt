package cloud.app.csplayer.torrent

/**
 * Torrent Package - libtorrent4j Integration
 *
 * This package provides complete torrent download functionality for CSPlayer.
 *
 * ## Quick Start
 *
 * ### Option 1: Use the pre-built Fragment
 * ```kotlin
 * supportFragmentManager.beginTransaction()
 *     .replace(R.id.container, TorrentFragment())
 *     .commit()
 * ```
 *
 * ### Option 2: Use TorrentManager directly
 * ```kotlin
 * val torrentManager = TorrentManager(context)
 * torrentManager.startPeriodicUpdates()
 *
 * torrentManager.addMagnet("magnet:?xt=urn:btih:...") { result ->
 *     result.onSuccess { infoHash ->
 *         // Torrent added successfully
 *     }
 * }
 * ```
 *
 * ### Option 3: Use with ViewModel
 * ```kotlin
 * class MyActivity : AppCompatActivity() {
 *     private val viewModel: TorrentViewModel by viewModels()
 *
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *
 *         viewModel.addMagnet("magnet:?xt=urn:btih:...")
 *
 *         lifecycleScope.launch {
 *             viewModel.torrents.collect { torrents ->
 *                 // Update UI with torrent states
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * ## Core Classes
 *
 * - **TorrentManager**: Main class for torrent operations
 * - **TorrentService**: Background foreground service for downloads
 * - **TorrentViewModel**: ViewModel for UI state management
 * - **TorrentUtils**: Helper utilities and validators
 * - **TorrentFragment**: Pre-built UI fragment
 *
 * ## Data Classes
 *
 * - **TorrentState**: Current state of a torrent (progress, speed, peers, etc.)
 * - **TorrentStatus**: Enum for torrent status (DOWNLOADING, PAUSED, FINISHED, etc.)
 * - **TorrentFile**: Information about a file in a torrent
 * - **TorrentUiState**: UI state (Loading, Success, Error, Idle)
 *
 * ## Features
 *
 * ✓ Download via magnet links or .torrent files
 * ✓ Real-time progress tracking
 * ✓ Pause/Resume/Remove operations
 * ✓ Background downloads with notification
 * ✓ Automatic peer discovery (DHT)
 * ✓ Video/audio file detection
 * ✓ Multi-file torrent support
 *
 * ## Test Torrents (Legal, Public Domain)
 *
 * Big Buck Bunny (158 MB):
 * ```
 * magnet:?xt=urn:btih:dd8255ecdc7ca55fb0bbf81323d87062db1f6d1c&dn=Big+Buck+Bunny
 * ```
 *
 * Sintel (129 MB):
 * ```
 * magnet:?xt=urn:btih:08ada5a7a6183aae1e09d831df6748d566095a10&dn=Sintel
 * ```
 *
 * ## Documentation
 *
 * - Full Guide: See LIBTORRENT_IMPLEMENTATION.md in project root
 * - Quick Start: See TORRENT_QUICKSTART.md in project root
 * - Summary: See IMPLEMENTATION_SUMMARY.md in project root
 *
 * ## Download Location
 *
 * Torrents are saved to:
 * `/Android/data/cloud.app.csplayer/files/torrents/`
 *
 * ## Example: Play Downloaded Video
 *
 * ```kotlin
 * lifecycleScope.launch {
 *     torrentManager.torrentStates.collect { torrents ->
 *         torrents.values
 *             .filter { it.status == TorrentStatus.FINISHED }
 *             .forEach { torrent ->
 *                 val files = torrentManager.getTorrentFiles(torrent.infoHash)
 *                 val videoFile = files.firstOrNull { TorrentUtils.isVideoFile(it.path) }
 *                 if (videoFile != null) {
 *                     playVideo(videoFile.path)
 *                 }
 *             }
 *     }
 * }
 * ```
 *
 * @see TorrentManager
 * @see TorrentService
 * @see cloud.app.csplayer.ui.library.download.TorrentViewModel
 * @see TorrentFragment
 */

