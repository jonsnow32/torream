package cloud.app.csplayer.utils

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.util.DisplayMetrics
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.preference.PreferenceManager
import cloud.app.csplayer.R
import cloud.app.csplayer.ui.player.PlayerEventType
import cloud.app.csplayer.utils.Utils.logError
import kotlin.math.max
import kotlin.math.min

object CommonActivitty {
  var canEnterPipMode: Boolean = false
  var canShowPipMode: Boolean = false
  var isInPIPMode: Boolean = false
  var playerEventListener: ((PlayerEventType) -> Unit)? = null
  var keyEventListener: ((Pair<KeyEvent?, Boolean>) -> Boolean)? = null



  val displayMetrics: DisplayMetrics = Resources.getSystem().displayMetrics

  // screenWidth and screenHeight does always
  // refer to the screen while in landscape mode
  val screenWidth: Int
    get() {
      return max(displayMetrics.widthPixels, displayMetrics.heightPixels)
    }
  val screenHeight: Int
    get() {
      return min(displayMetrics.widthPixels, displayMetrics.heightPixels)
    }

  fun Context.shouldShowPIPMode(isInPlayer: Boolean): Boolean {
    return try {
      val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
      settingsManager?.getBoolean(
        getString(R.string.pip_enabled_key),
        true
      ) ?: true && isInPlayer
    } catch (e: Exception) {
      logError(e)
      false
    }
  }

  private fun Activity.enterPIPMode() {
    if (!shouldShowPIPMode(canEnterPipMode) || !canShowPipMode) return
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        try {
          enterPictureInPictureMode(PictureInPictureParams.Builder().build())
        } catch (e: Exception) {
          enterPictureInPictureMode()
        }
      } else {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
          enterPictureInPictureMode()
        }
      }
    } catch (e: Exception) {
      logError(e)
    }
  }

  fun onUserLeaveHint(act: Activity?) {
    if (canEnterPipMode && canShowPipMode) {
      act?.enterPIPMode()
    }
  }
  fun hideKeyboard(view: View?) {
    if (view == null) return

    val inputMethodManager =
      view.context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager?
    inputMethodManager?.hideSoftInputFromWindow(view.windowToken, 0)
  }
  fun Activity.hideKeyboard() {
    window?.decorView?.clearFocus()
    this.findViewById<View>(android.R.id.content)?.rootView?.let {
      hideKeyboard(it)
    }
  }
}
