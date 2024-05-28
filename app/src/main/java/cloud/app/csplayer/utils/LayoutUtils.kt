package cloud.app.csplayer.utils

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.view.View
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import cloud.app.csplayer.R

enum class LayoutMode(val id: Int) {
  Phone(0b001),
  Tv(0b010),
  Emulator(0b100),
  Unknow(-1)

}

private fun Context.getLayoutInt(): Int {
  val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
  return settingsManager.getInt(this.getString(R.string.app_layout_key), -1)
}

private fun Context.isAutoTv(): Boolean {
  val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager?
  // AFT = Fire TV
  val model = Build.MODEL.lowercase()
  return uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION || Build.MODEL.contains(
    "AFT"
  ) || model.contains("firestick") || model.contains("fire tv") || model.contains("chromecast")
}

private fun Context.getLayoutMode(): LayoutMode {
  return when (getLayoutInt()) {
    -1 -> if (isAutoTv()) LayoutMode.Tv else LayoutMode.Phone
    0 -> LayoutMode.Phone
    1 -> LayoutMode.Tv
    2 -> LayoutMode.Emulator
    else -> LayoutMode.Phone
  }
}

fun Context.isLayout(flags: Int): Boolean {
  return (getLayoutMode().id and flags) != 0
}

fun Context.isTvOrEmulator(): Boolean {
  return isLayout(LayoutMode.Tv.id or LayoutMode.Emulator.id)
}
fun Fragment.isTvOrEmulator(): Boolean {
  return requireContext().isLayout(LayoutMode.Tv.id or LayoutMode.Emulator.id)
}
fun Activity.hideSystemUI() {
  @Suppress("DEPRECATION")
  window.decorView.systemUiVisibility = (
    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
      // Set the content to appear under the system bars so that the
      // content doesn't resize when the system bars hide and show.
      or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
      or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
      or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
      // Hide the nav bar and status bar
      or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
      or View.SYSTEM_UI_FLAG_FULLSCREEN
    )
  //}
}
