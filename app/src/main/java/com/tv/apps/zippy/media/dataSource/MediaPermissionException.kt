package com.tv.apps.zippy.media.dataSource

/**
 * Exception thrown when media permissions are not granted
 */
class MediaPermissionException(
  message: String = "Media access permission is required to load videos and audio files"
) : SecurityException(message)

