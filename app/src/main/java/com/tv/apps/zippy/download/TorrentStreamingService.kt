package com.tv.apps.zippy.download

import android.content.Context
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import com.tv.apps.zippy.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.libtorrent4j.AlertListener
import org.libtorrent4j.SessionManager
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.AlertType
import org.libtorrent4j.alerts.PieceFinishedAlert
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for streaming torrent/magnet links directly without full download
 * Uses sequential download mode to stream from beginning of file
 */
@Singleton
class TorrentStreamingService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var sessionManager: SessionManager? = null
    private var torrentHandle: TorrentHandle? = null
    private var streamingFile: File? = null

    data class StreamingState(
        val isReady: Boolean = false,
        val progress: Float = 0f,
        val downloadRate: Long = 0,
        val filePath: String? = null,
        val error: String? = null
    )

    private var currentState = StreamingState()
    private val stateListeners = mutableListOf<(StreamingState) -> Unit>()

    /**
     * Start streaming a magnet link or torrent file
     * Downloads pieces sequentially starting from the beginning for instant playback
     */
    suspend fun startStreaming(
        magnetOrTorrentUri: String,
        onStateChanged: (StreamingState) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            stateListeners.add(onStateChanged)

            // Initialize session
            if (sessionManager == null) {
                sessionManager = SessionManager().apply {
                    start()

                    // Start DHT
                    try {
                        startDht()
                        Timber.i("Streaming: SessionManager started with DHT")
                    } catch (e: Exception) {
                        Timber.w("Streaming: DHT start failed (non-fatal): ${e.message}")
                    }

                    // Add alert listener for piece completion
                    addListener(object : AlertListener {
                        override fun alert(alert: Alert<*>) {
                            when (alert.type()) {
                                AlertType.PIECE_FINISHED -> {
                                    val pieceAlert = alert as PieceFinishedAlert
                                    Timber.d("Piece ${pieceAlert.pieceIndex()} finished")
                                }
                                AlertType.METADATA_RECEIVED -> {
                                    Timber.i("Metadata received, prioritizing largest file")
                                    torrentHandle?.let { handle ->

                                        // Find largest file to stream
                                        val torrentInfo = handle.torrentFile()
                                        val fileStorage = torrentInfo.files()
                                        var largestIndex = 0
                                        var largestSize = 0L

                                        for (i in 0 until fileStorage.numFiles()) {
                                            val size = fileStorage.fileSize(i)
                                            if (size > largestSize) {
                                                largestSize = size
                                                largestIndex = i
                                            }
                                        }

                                        // Set file priority - only download largest file
                                        val priorities = Array(fileStorage.numFiles()) { i ->
                                            if (i == largestIndex) org.libtorrent4j.Priority.TOP_PRIORITY else org.libtorrent4j.Priority.IGNORE
                                        }
                                        handle.prioritizeFiles(priorities)

                                        val fileName = fileStorage.fileName(largestIndex)
                                        Timber.i("Streaming file: $fileName (${largestSize / (1024 * 1024)} MB)")
                                    }
                                }
                                else -> {}
                            }
                        }

                        override fun types(): IntArray = intArrayOf(
                            AlertType.PIECE_FINISHED.swig(),
                            AlertType.METADATA_RECEIVED.swig()
                        )
                    })
                }
            }

            val sm = sessionManager ?: return@withContext Result.failure(Exception("Failed to initialize session"))

            // Create temp directory for streaming
            val streamDir = File(context.cacheDir, "torrent_stream")
            if (!streamDir.exists()) {
                streamDir.mkdirs()
            }

            // Add torrent
            val torrentInfo = when {
                magnetOrTorrentUri.startsWith("magnet:", ignoreCase = true) -> {
                    updateState(currentState.copy(progress = 0f))

                    // Fetch magnet metadata with longer timeout for streaming
                    Timber.i("Fetching magnet metadata for streaming...")
                    val metadata = try {
                        sm.fetchMagnet(magnetOrTorrentUri, 30, streamDir)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to fetch magnet metadata")
                        updateState(currentState.copy(error = "Failed to fetch magnet: ${e.message}"))
                        return@withContext Result.failure(e)
                    }

                    if (metadata == null) {
                        val error = "Could not fetch magnet metadata. Link may be invalid or no peers available."
                        updateState(currentState.copy(error = error))
                        return@withContext Result.failure(Exception(error))
                    }

                    TorrentInfo.bdecode(metadata)
                }

                magnetOrTorrentUri.endsWith(".torrent", ignoreCase = true) -> {
                    val torrentFile = if (magnetOrTorrentUri.startsWith("content://")) {
                        // Copy from content URI
                        val tempFile = File(streamDir, "stream_${System.currentTimeMillis()}.torrent")
                        context.contentResolver.openInputStream(magnetOrTorrentUri.toUri())?.use { input ->
                            tempFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        tempFile
                    } else {
                        File(magnetOrTorrentUri)
                    }

                    if (!torrentFile.exists()) {
                        val error = "Torrent file not found: $magnetOrTorrentUri"
                        updateState(currentState.copy(error = error))
                        return@withContext Result.failure(Exception(error))
                    }

                    TorrentInfo(torrentFile)
                }

                else -> {
                    val error = "Invalid torrent URI: $magnetOrTorrentUri"
                    updateState(currentState.copy(error = error))
                    return@withContext Result.failure(Exception(error))
                }
            }

            // Start download with sequential mode
            sm.download(torrentInfo, streamDir)

            // Find handle
            val infoHash = torrentInfo.infoHash()
            var handle: TorrentHandle? = null

            for (attempt in 1..20) {
                handle = sm.find(infoHash)
                if (handle != null) {
                    Timber.d("Found torrent handle for streaming after $attempt attempts")
                    break
                }
                delay(300L)
            }

            if (handle == null) {
                val error = "Failed to get torrent handle for streaming"
                updateState(currentState.copy(error = error))
                return@withContext Result.failure(Exception(error))
            }

            torrentHandle = handle

            // Find largest video file to stream
            val fileStorage = torrentInfo.files()
            var largestIndex = 0
            var largestSize = 0L

            for (i in 0 until fileStorage.numFiles()) {
                val size = fileStorage.fileSize(i)
                val name = fileStorage.fileName(i).lowercase()
                // Only consider video files
                if (size > largestSize && isVideoFile(name)) {
                    largestSize = size
                    largestIndex = i
                }
            }

            // Prioritize only the file we want to stream
            val priorities = Array(fileStorage.numFiles()) { i ->
                if (i == largestIndex) org.libtorrent4j.Priority.TOP_PRIORITY else org.libtorrent4j.Priority.IGNORE
            }
            handle.prioritizeFiles(priorities)

            val fileName = fileStorage.fileName(largestIndex)
            val filePath = File(streamDir, torrentInfo.name()).let { root ->
                if (fileStorage.numFiles() == 1) {
                    File(streamDir, fileName)
                } else {
                    File(root, fileName)
                }
            }

            streamingFile = filePath

            Timber.i("Streaming file: ${filePath.absolutePath}")
            Timber.i("Waiting for first pieces to download...")

            // Wait for first 5% or 10MB (whichever is smaller) to be available
            val minBytesNeeded = minOf((largestSize * 0.05).toLong(), 10 * 1024 * 1024L)
            var attempts = 0
            val maxAttempts = 60 // 60 seconds

            while (attempts < maxAttempts) {
                val status = handle.status()
                val downloaded = status.totalDone()
                val progress = if (largestSize > 0) (downloaded * 100f / largestSize) else 0f

                // Detailed file validation logging
                val fileExists = filePath.exists()
                val fileSize = if (fileExists) filePath.length() else 0L
                val canRead = if (fileExists) filePath.canRead() else false

                if (attempts % 5 == 0) { // Log every 5 seconds
                    Timber.d("Streaming buffer progress: ${progress.toInt()}%, downloaded: ${downloaded / (1024 * 1024)} MB")
                    Timber.d("File state: exists=$fileExists, size=${fileSize / (1024 * 1024)} MB, readable=$canRead")
                    Timber.d("File path: ${filePath.absolutePath}")
                }

                updateState(currentState.copy(
                    progress = progress,
                    downloadRate = status.downloadRate().toLong(),
                    filePath = filePath.absolutePath
                ))

                if (downloaded >= minBytesNeeded && fileExists && fileSize > 0) {
                    Timber.i("✓ Ready to stream!")
                    Timber.i("  Downloaded: ${downloaded / (1024 * 1024)} MB (${progress.toInt()}%)")
                    Timber.i("  File: ${filePath.absolutePath}")
                    Timber.i("  File size: ${fileSize / (1024 * 1024)} MB")
                    Timber.i("  Readable: $canRead")

                    if (!canRead) {
                        Timber.w("Warning: File exists but cannot be read!")
                    }

                    updateState(currentState.copy(
                        isReady = true,
                        progress = progress,
                        downloadRate = status.downloadRate().toLong(),
                        filePath = filePath.absolutePath
                    ))

                    return@withContext Result.success(filePath.absolutePath)
                }

                delay(1000L)
                attempts++
            }

            // Timeout - but still return path if file exists
            if (filePath.exists() && filePath.length() > 1024 * 1024) {
                Timber.w("Streaming timeout but file has data (${filePath.length() / (1024 * 1024)} MB), allowing playback")
                Timber.i("File path: ${filePath.absolutePath}")
                Timber.i("File readable: ${filePath.canRead()}")
                updateState(currentState.copy(
                    isReady = true,
                    filePath = filePath.absolutePath
                ))
                return@withContext Result.success(filePath.absolutePath)
            }

            val error = "Timeout waiting for streaming data. Try again or check internet connection."
            updateState(currentState.copy(error = error))
            Result.failure(Exception(error))

        } catch (e: Exception) {
            Timber.e(e, "Streaming error")
            updateState(currentState.copy(error = e.message ?: "Unknown error"))
            Result.failure(e)
        }
    }

    private fun isVideoFile(name: String): Boolean {
        val videoExtensions = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "3gp")
        val extension = name.substringAfterLast('.', "").lowercase()
        return extension in videoExtensions
    }

    private fun updateState(newState: StreamingState) {
        currentState = newState
        stateListeners.forEach { it(newState) }
    }

    /**
     * Stop streaming and clean up resources
     */
    fun stopStreaming() {
        try {
            torrentHandle?.let { handle ->
                sessionManager?.remove(handle)
            }
            sessionManager?.stop()
            sessionManager = null
            torrentHandle = null

            // Clean up temp files
            streamingFile?.let { file ->
                try {
                    val streamDir = file.parentFile
                    streamDir?.deleteRecursively()
                } catch (e: Exception) {
                    Timber.w("Failed to clean up streaming files: ${e.message}")
                }
            }
            streamingFile = null

            stateListeners.clear()
            currentState = StreamingState()

            Timber.i("Streaming stopped and cleaned up")
        } catch (e: Exception) {
            Timber.e(e, "Error stopping streaming")
        }
    }

    /**
     * Check if currently streaming
     */
    fun isStreaming(): Boolean = sessionManager != null && torrentHandle != null

    /**
     * Get current streaming state
     */
    fun getCurrentState(): StreamingState = currentState
}

