package com.tv.apps.zippy.model

sealed class SyncState {
  data object Idle : SyncState()
  data class Syncing(val progress: Float) : SyncState()

  sealed class Error : SyncState() {
    abstract val message: String

    data class MissingPermission(
      override val message: String = "Storage permission is required to access media files"
    ) : Error()

    data class NetworkError(
      override val message: String = "Network connection error"
    ) : Error()

    data class StorageError(
      override val message: String = "Unable to access storage"
    ) : Error()

    data class Unknown(
      override val message: String = "Unknown error occurred"
    ) : Error()

    // Generic error with custom message
    data class Generic(
      override val message: String
    ) : Error()
  }

  data object Completed : SyncState()
}
