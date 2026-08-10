package cloud.streamless.torream.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cloud.streamless.torream.media.repository.NetworkShareRepository
import cloud.streamless.torream.model.NetworkShare
import cloud.streamless.torream.model.NetworkShareProtocol
import cloud.streamless.torream.model.VideoLink
import cloud.streamless.torream.ui.browse.network.BrowseEntry
import cloud.streamless.torream.ui.browse.network.FtpBrowseClient
import cloud.streamless.torream.ui.browse.network.SmbBrowseClient
import cloud.streamless.torream.ui.browse.network.WebDavBrowseClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class BrowseViewModel @Inject constructor(
  private val networkShareRepository: NetworkShareRepository
) : ViewModel() {

  private val webDavBrowseClient = WebDavBrowseClient()
  private val ftpBrowseClient = FtpBrowseClient()
  private val smbBrowseClient = SmbBrowseClient()

  val shares: StateFlow<List<NetworkShare>> = networkShareRepository.observeShares()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  private val _currentShare = MutableStateFlow<NetworkShare?>(null)
  val currentShare: StateFlow<NetworkShare?> = _currentShare

  private val _currentPath = MutableStateFlow("")
  val currentPath: StateFlow<String> = _currentPath

  private val _entries = MutableStateFlow<List<BrowseEntry>>(emptyList())
  val entries: StateFlow<List<BrowseEntry>> = _entries

  private val _isLoading = MutableStateFlow(false)
  val isLoading: StateFlow<Boolean> = _isLoading

  private val _error = MutableStateFlow<String?>(null)
  val error: StateFlow<String?> = _error

  fun openShare(share: NetworkShare) {
    _currentShare.value = share
    _currentPath.value = ""
    loadCurrentDirectory()
  }

  fun openDirectory(entry: BrowseEntry) {
    _currentPath.value = entry.relativePath
    loadCurrentDirectory()
  }

  /** Returns true if it navigated up within a share; false if it left the share (caller should show the share list). */
  fun navigateUp(): Boolean {
    val path = _currentPath.value
    if (path.isEmpty()) {
      _currentShare.value = null
      _entries.value = emptyList()
      return false
    }
    _currentPath.value = path.substringBeforeLast("/", "")
    loadCurrentDirectory()
    return true
  }

  fun deleteShare(share: NetworkShare) {
    viewModelScope.launch { networkShareRepository.deleteShare(share.id) }
  }

  fun buildVideoLink(entry: BrowseEntry): VideoLink? {
    val share = _currentShare.value ?: return null
    return networkShareRepository.buildVideoLink(share, entry.relativePath, entry.name)
  }

  private fun loadCurrentDirectory() {
    val share = _currentShare.value ?: return
    viewModelScope.launch {
      _isLoading.value = true
      _error.value = null
      try {
        val result = withContext(Dispatchers.IO) {
          when (share.protocol) {
            NetworkShareProtocol.SMB -> smbBrowseClient.list(share, _currentPath.value)
            NetworkShareProtocol.FTP -> ftpBrowseClient.list(share, _currentPath.value)
            NetworkShareProtocol.WEBDAV -> webDavBrowseClient.list(share, _currentPath.value)
          }
        }
        _entries.value = result.sortedWith(
          compareByDescending<BrowseEntry> { it.isDirectory }.thenBy { it.name.lowercase() }
        )
      } catch (e: Exception) {
        _error.value = e.message ?: e.toString()
        _entries.value = emptyList()
      } finally {
        _isLoading.value = false
      }
    }
  }
}
