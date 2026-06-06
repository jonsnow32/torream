package cloud.streamless.torream.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat

fun Context.hasMediaPermissions(): Boolean {
  return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    // Android 13+ (API 33+) - Need READ_MEDIA_VIDEO and READ_MEDIA_AUDIO
    val hasVideoPermission = ContextCompat.checkSelfPermission(
      this,
      Manifest.permission.READ_MEDIA_VIDEO
    ) == PackageManager.PERMISSION_GRANTED

    val hasAudioPermission = ContextCompat.checkSelfPermission(
      this,
      Manifest.permission.READ_MEDIA_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    hasVideoPermission && hasAudioPermission
  } else {
    // Android 12 and below - Need READ_EXTERNAL_STORAGE
    ContextCompat.checkSelfPermission(
      this,
      Manifest.permission.READ_EXTERNAL_STORAGE
    ) == PackageManager.PERMISSION_GRANTED
  }
}

fun Context.requestMediaPermissions(launcher: ActivityResultLauncher<Array<String>>) {
  val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    // Android 13+ (API 33+)
    arrayOf(
      Manifest.permission.READ_MEDIA_VIDEO,
      Manifest.permission.READ_MEDIA_AUDIO
    )
  } else {
    // Android 12 and below
    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
  }
  launcher.launch(permissions)
}
