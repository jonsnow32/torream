package cloud.app.csplayer.media.model

sealed class SyncState {
  data object Idle : SyncState()
  data class Syncing(val progress: Float) : SyncState()
  data class Error(val message: String) : SyncState()
  data object Completed : SyncState()
}
