package cloud.app.csplayer.torrent

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for managing torrent downloads
 */
@HiltViewModel
class TorrentViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @Suppress("unused") private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    @Suppress("StaticFieldLeak")
    private var torrentService: TorrentService? = null
    private var isBound = false

    private val _torrents = MutableStateFlow<Map<String, TorrentState>>(emptyMap())
    val torrents: StateFlow<Map<String, TorrentState>> = _torrents.asStateFlow()

    private val _uiState = MutableStateFlow<TorrentUiState>(TorrentUiState.Idle)
    val uiState: StateFlow<TorrentUiState> = _uiState.asStateFlow()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as TorrentService.TorrentBinder
            torrentService = binder.getService()
            isBound = true

            // Observe torrent states
            observeTorrentStates()

            Timber.d("TorrentViewModel: Service connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            torrentService = null
            isBound = false
            Timber.d("TorrentViewModel: Service disconnected")
        }
    }

    init {
        bindToService()
    }

    private fun bindToService() {
        val intent = Intent(context, TorrentService::class.java)
        context.bindService(
            intent,
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    private fun observeTorrentStates() {
        viewModelScope.launch {
            torrentService?.getTorrentManager()?.torrentStates?.collect { states ->
                _torrents.value = states
            }
        }
    }

    /**
     * Add a torrent from a magnet link
     */
    fun addMagnet(magnetUri: String) {
        _uiState.value = TorrentUiState.Loading

        // Start service if not running
        TorrentService.start(context)

        torrentService?.getTorrentManager()?.addMagnet(magnetUri) { result ->
            result.fold(
                onSuccess = { infoHash ->
                    _uiState.value = TorrentUiState.Success("Torrent added successfully")
                    Timber.d("TorrentViewModel: Magnet added with hash: $infoHash")
                },
                onFailure = { error ->
                    _uiState.value = TorrentUiState.Error(error.message ?: "Failed to add magnet")
                    Timber.e(error, "TorrentViewModel: Failed to add magnet")
                }
            )
        }
    }

    /**
     * Add a torrent from a file path
     */
    fun addTorrentFile(filePath: String) {
        _uiState.value = TorrentUiState.Loading

        // Start service if not running
        TorrentService.start(context)

        torrentService?.getTorrentManager()?.addTorrentFile(filePath) { result ->
            result.fold(
                onSuccess = { infoHash ->
                    _uiState.value = TorrentUiState.Success("Torrent added successfully")
                    Timber.d("TorrentViewModel: Torrent file added with hash: $infoHash")
                },
                onFailure = { error ->
                    _uiState.value = TorrentUiState.Error(error.message ?: "Failed to add torrent file")
                    Timber.e(error, "TorrentViewModel: Failed to add torrent file")
                }
            )
        }
    }

    /**
     * Pause a torrent download
     */
    fun pauseTorrent(infoHash: String) {
        torrentService?.getTorrentManager()?.pauseTorrent(infoHash)
    }

    /**
     * Resume a torrent download
     */
    fun resumeTorrent(infoHash: String) {
        torrentService?.getTorrentManager()?.resumeTorrent(infoHash)
    }

    /**
     * Remove a torrent
     */
    fun removeTorrent(infoHash: String, deleteFiles: Boolean = false) {
        torrentService?.getTorrentManager()?.removeTorrent(infoHash, deleteFiles)
    }

    /**
     * Get the files of a torrent
     */
    fun getTorrentFiles(infoHash: String): List<TorrentFile> {
        return torrentService?.getTorrentManager()?.getTorrentFiles(infoHash) ?: emptyList()
    }

    /**
     * Reset UI state
     */
    fun resetUiState() {
        _uiState.value = TorrentUiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        if (isBound) {
            context.unbindService(serviceConnection)
            isBound = false
        }
    }
}

sealed class TorrentUiState {
    object Idle : TorrentUiState()
    object Loading : TorrentUiState()
    data class Success(val message: String) : TorrentUiState()
    data class Error(val message: String) : TorrentUiState()
}

