package cloud.app.csplayer.torrent

import android.content.Context
import cloud.app.csplayer.media.model.TorrentDownloadStatus
import cloud.app.csplayer.media.model.TorrentState
import cloud.app.csplayer.media.repository.TorrentRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.libtorrent4j.AlertListener
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.Sha1Hash
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.TorrentStatus
import org.libtorrent4j.alerts.AddTorrentAlert
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.AlertType
import org.libtorrent4j.alerts.MetadataReceivedAlert
import org.libtorrent4j.alerts.StateChangedAlert
import org.libtorrent4j.alerts.TorrentErrorAlert
import org.libtorrent4j.alerts.TorrentFinishedAlert
import org.libtorrent4j.swig.settings_pack
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages torrent downloads using libtorrent4j
 */
@Singleton
class TorrentManager @Inject constructor(
  @param:ApplicationContext private val context: Context,
  private val torrentRepository: TorrentRepository
) {
  private var sessionManager: SessionManager? = null
  private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
  private val _torrentStates = MutableStateFlow<Map<String, TorrentState>>(emptyMap())
  val torrentStates: StateFlow<Map<String, TorrentState>> = _torrentStates.asStateFlow()

  private val downloadDir: File by lazy {
    File(context.getExternalFilesDir(null), "torrents").apply {
      if (!exists()) mkdirs()
    }
  }

  init {
    initializeSession()
    loadTorrentsFromDatabase()
  }

  private fun loadTorrentsFromDatabase() {
    scope.launch {
      try {
        val savedTorrents = torrentRepository.getAllTorrents()
        Timber.d("TorrentManager: Loaded ${savedTorrents.size} torrents from database")

        // Update in-memory state
        val statesMap = savedTorrents.associateBy { it.infoHash }
        _torrentStates.value = statesMap

        // Resume active torrents
        savedTorrents.filter {
          it.status == TorrentDownloadStatus.DOWNLOADING ||
            it.status == TorrentDownloadStatus.SEEDING
        }.forEach { torrent ->
          // Re-add torrent to session if it was active
          Timber.d("TorrentManager: Resuming torrent: ${torrent.name}")
        }
      } catch (e: Exception) {
        Timber.e(e, "TorrentManager: Failed to load torrents from database")
      }
    }
  }

  private fun initializeSession() {
    scope.launch {
      try {
        val config = SettingsPack()
        config.setString(
          settings_pack.string_types.user_agent.swigValue(),
          "CSPlayer/1.0"
        )
        config.setInteger(
          settings_pack.int_types.active_downloads.swigValue(),
          4
        )
        config.setInteger(
          settings_pack.int_types.active_seeds.swigValue(),
          4
        )
        config.setInteger(
          settings_pack.int_types.active_limit.swigValue(),
          8
        )

        val params = SessionParams(config)
        sessionManager = SessionManager().apply {
          addListener(object : AlertListener {
            override fun types(): IntArray? = null

            override fun alert(alert: Alert<*>) {
              handleAlert(alert)
            }
          })
          start(params)
          startDht()
          Timber.d("TorrentManager: Session initialized successfully")
        }
      } catch (e: Exception) {
        Timber.e(e, "TorrentManager: Failed to initialize session")
      }
    }
  }

  private fun handleAlert(alert: Alert<*>) {
    when (alert.type()) {
      AlertType.ADD_TORRENT -> {
        val a = alert as AddTorrentAlert
        val handle = a.handle()
        if (handle.isValid) {
          Timber.d("TorrentManager: Torrent added: ${handle.status().name()}")
        }
      }

      AlertType.TORRENT_FINISHED -> {
        val a = alert as TorrentFinishedAlert
        val hash = a.handle().infoHash().toHex()
        updateTorrentState(hash) {
          it.copy(status = TorrentDownloadStatus.FINISHED)
        }
        // Save to database
        scope.launch {
          torrentRepository.updateTorrentStatus(hash, TorrentDownloadStatus.FINISHED)
        }
        Timber.d("TorrentManager: Torrent finished")
      }

      AlertType.TORRENT_ERROR -> {
        val a = alert as TorrentErrorAlert
        val hash = a.handle().infoHash().toHex()
        val errorCode = a.error()
        val errorMsg = errorCode.message ?: "Unknown error"
        updateTorrentState(hash) {
          it.copy(status = TorrentDownloadStatus.ERROR, error = errorMsg)
        }
        // Save to database
        scope.launch {
          torrentRepository.updateTorrentStatus(hash, TorrentDownloadStatus.ERROR, errorMsg)
        }
        Timber.e("TorrentManager: Torrent error: $errorMsg")
      }

      AlertType.STATE_CHANGED -> {
        val a = alert as StateChangedAlert
        val handle = a.handle()
        if (handle.isValid) {
          updateTorrentStateFromHandle(handle)
        }
      }

      AlertType.METADATA_RECEIVED -> {
        val a = alert as MetadataReceivedAlert
        Timber.d("TorrentManager: Metadata received")
        updateTorrentStateFromHandle(a.handle())
      }

      else -> {
        // Ignore other alerts
      }
    }
  }

  /**
   * Add a torrent from a magnet link
   */
  fun addMagnet(magnetUri: String, callback: (Result<String>) -> Unit) {
    scope.launch {
      try {
        val sm = sessionManager ?: run {
          withContext(Dispatchers.Main) {
            callback(Result.failure(Exception("Session not initialized")))
          }
          return@launch
        }

        // Extract info hash from magnet URI
        val infoHash = extractInfoHashFromMagnet(magnetUri) ?: run {
          withContext(Dispatchers.Main) {
            callback(Result.failure(Exception("Invalid magnet link")))
          }
          return@launch
        }

        // Download the torrent using fetch
        val tempDir = File(context.cacheDir, "torrents_temp").apply {
          if (!exists()) mkdirs()
        }
        val fetchedData = sm.fetchMagnet(magnetUri, 30, tempDir) // 30 seconds timeout
        if (fetchedData == null) {
          withContext(Dispatchers.Main) {
            callback(Result.failure(Exception("Failed to fetch magnet metadata")))
          }
          return@launch
        }

        sm.download(TorrentInfo.bdecode(fetchedData), downloadDir)

        // Initialize state
        val initialState = TorrentState(
          infoHash = infoHash,
          name = "Fetching metadata...",
          status = TorrentDownloadStatus.DOWNLOADING,
          progress = 0f,
          downloadSpeed = 0,
          uploadSpeed = 0,
          totalSize = 0,
          downloadedSize = 0
        )

        updateTorrentState(infoHash) { initialState }

        // Save to database
        torrentRepository.insertTorrent(
          infoHash = infoHash,
          name = "Fetching metadata...",
          magnetUri = magnetUri,
          savePath = downloadDir.absolutePath,
          status = TorrentDownloadStatus.DOWNLOADING,
          totalSize = 0
        )

        withContext(Dispatchers.Main) {
          callback(Result.success(infoHash))
        }
      } catch (e: Exception) {
        Timber.e(e, "TorrentManager: Failed to add magnet")
        withContext(Dispatchers.Main) {
          callback(Result.failure(e))
        }
      }
    }
  }

  private fun extractInfoHashFromMagnet(magnetUri: String): String? {
    val btihRegex = "urn:btih:([a-fA-F0-9]{40})".toRegex()
    return btihRegex.find(magnetUri)?.groupValues?.get(1)
  }

  /**
   * Add a torrent from a .torrent file
   */
  fun addTorrentFile(torrentFilePath: String, callback: (Result<String>) -> Unit) {
    scope.launch {
      try {
        val sm = sessionManager ?: run {
          withContext(Dispatchers.Main) {
            callback(Result.failure(Exception("Session not initialized")))
          }
          return@launch
        }

        val torrentFile = File(torrentFilePath)
        if (!torrentFile.exists()) {
          withContext(Dispatchers.Main) {
            callback(Result.failure(Exception("Torrent file not found")))
          }
          return@launch
        }

        val ti = TorrentInfo(torrentFile)
        val infoHash = ti.infoHash().toHex()

        sm.download(ti, downloadDir)

        val initialState = TorrentState(
          infoHash = infoHash,
          name = ti.name(),
          status = TorrentDownloadStatus.DOWNLOADING,
          progress = 0f,
          downloadSpeed = 0,
          uploadSpeed = 0,
          totalSize = ti.totalSize(),
          downloadedSize = 0
        )

        updateTorrentState(infoHash) { initialState }

        // Save to database
        torrentRepository.insertTorrent(
          infoHash = infoHash,
          name = ti.name(),
          torrentFilePath = torrentFilePath,
          savePath = downloadDir.absolutePath,
          status = TorrentDownloadStatus.DOWNLOADING,
          totalSize = ti.totalSize()
        )

        withContext(Dispatchers.Main) {
          callback(Result.success(infoHash))
        }
      } catch (e: Exception) {
        Timber.e(e, "TorrentManager: Failed to add torrent file")
        withContext(Dispatchers.Main) {
          callback(Result.failure(e))
        }
      }
    }
  }

  /**
   * Pause a torrent
   */
  fun pauseTorrent(infoHash: String) {
    scope.launch {
      try {
        val handle = getTorrentHandle(infoHash)
        handle?.pause()
        updateTorrentState(infoHash) {
          it.copy(status = TorrentDownloadStatus.PAUSED)
        }
        // Save to database
        torrentRepository.updateTorrentStatus(infoHash, TorrentDownloadStatus.PAUSED)
      } catch (e: Exception) {
        Timber.e(e, "TorrentManager: Failed to pause torrent")
      }
    }
  }

  /**
   * Resume a torrent
   */
  fun resumeTorrent(infoHash: String) {
    scope.launch {
      try {
        val handle = getTorrentHandle(infoHash)
        handle?.resume()
        updateTorrentState(infoHash) {
          it.copy(status = TorrentDownloadStatus.DOWNLOADING)
        }
        // Save to database
        torrentRepository.updateTorrentStatus(infoHash, TorrentDownloadStatus.DOWNLOADING)
      } catch (e: Exception) {
        Timber.e(e, "TorrentManager: Failed to resume torrent")
      }
    }
  }

  /**
   * Remove a torrent
   */
  fun removeTorrent(infoHash: String, deleteFiles: Boolean = false) {
    scope.launch {
      try {
        val handle = getTorrentHandle(infoHash)
        if (handle != null) {
          sessionManager?.remove(handle)
          if (deleteFiles) {
            // Delete files manually if needed
            val files = getTorrentFiles(infoHash)
            files.forEach { file ->
              try {
                File(file.path).delete()
              } catch (e: Exception) {
                Timber.e(e, "Failed to delete file: ${file.path}")
              }
            }
          }
        }
        removeFromState(infoHash)
        // Delete from database
        torrentRepository.deleteTorrent(infoHash)
      } catch (e: Exception) {
        Timber.e(e, "TorrentManager: Failed to remove torrent")
      }
    }
  }

  /**
   * Get the file path of a downloaded torrent
   */
  fun getTorrentFiles(infoHash: String): List<TorrentFile> {
    val handle = getTorrentHandle(infoHash) ?: return emptyList()
    val ti = handle.torrentFile() ?: return emptyList()

    return (0 until ti.numFiles()).map { i ->
      val path = ti.files().filePath(i)
      val size = ti.files().fileSize(i)
      TorrentFile(
        path = File(downloadDir, path).absolutePath,
        size = size,
        priority = handle.filePriority(i)
      )
    }
  }

  /**
   * Update torrent status periodically
   */
  fun startPeriodicUpdates() {
    scope.launch {
      while (isActive) {
        updateAllTorrentStates()
        delay(1000) // Update every second
      }
    }
  }

  private fun updateAllTorrentStates() {
    sessionManager?.let { sm ->
      val handles = sm.swig().get_torrents()
      val size = handles.size
      for (i in 0 until size) {
        val handle = TorrentHandle(handles.get(i))
        if (handle.isValid) {
          updateTorrentStateFromHandle(handle)
        }
      }
    }
  }

  private fun updateTorrentStateFromHandle(handle: TorrentHandle) {
    val status = handle.status()
    val infoHash = handle.infoHash().toHex()

    val newState = TorrentState(
      infoHash = infoHash,
      name = status.name() ?: "Unknown",
      status = when (status.state()) {
        TorrentStatus.State.FINISHED, TorrentStatus.State.SEEDING -> TorrentDownloadStatus.FINISHED
        TorrentStatus.State.DOWNLOADING, TorrentStatus.State.DOWNLOADING_METADATA -> TorrentDownloadStatus.DOWNLOADING
        TorrentStatus.State.CHECKING_FILES, TorrentStatus.State.CHECKING_RESUME_DATA -> TorrentDownloadStatus.DOWNLOADING
        else -> TorrentDownloadStatus.PAUSED
      },
      progress = status.progress(),
      downloadSpeed = status.downloadRate().toLong(),
      uploadSpeed = status.uploadRate().toLong(),
      totalSize = status.total(),
      downloadedSize = status.totalDone(),
      numPeers = status.numPeers(),
      numSeeds = status.numSeeds()
    )

    updateTorrentState(infoHash) { newState }

    // Save to database periodically
    scope.launch {
      try {
        torrentRepository.updateTorrentState(newState)
      } catch (e: Exception) {
        Timber.e(e, "Failed to update torrent state in database")
      }
    }
  }

  private fun getTorrentHandle(infoHash: String): TorrentHandle? {
    val sha1 = Sha1Hash.parseHex(infoHash)
    return sessionManager?.find(sha1)
  }

  private fun updateTorrentState(infoHash: String, update: (TorrentState) -> TorrentState) {
    val currentStates = _torrentStates.value.toMutableMap()
    val currentState = currentStates[infoHash] ?: TorrentState(
      infoHash = infoHash,
      name = "Unknown",
      status = TorrentDownloadStatus.DOWNLOADING
    )
    currentStates[infoHash] = update(currentState)
    _torrentStates.value = currentStates
  }

  private fun removeFromState(infoHash: String) {
    val currentStates = _torrentStates.value.toMutableMap()
    currentStates.remove(infoHash)
    _torrentStates.value = currentStates
  }

  /**
   * Shutdown the torrent manager
   */
  fun shutdown() {
    scope.cancel()
    sessionManager?.stop()
    sessionManager = null
  }
}


data class TorrentFile(
  val path: String,
  val size: Long,
  val priority: Priority
)

