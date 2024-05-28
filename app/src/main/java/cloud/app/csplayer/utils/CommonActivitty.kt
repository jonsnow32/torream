package cloud.app.csplayer.utils

import android.Manifest
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import cloud.app.csplayer.R
import cloud.app.csplayer.ui.player.PlayerEventType
import cloud.app.csplayer.ui.player.TAG
import cloud.app.csplayer.utils.UIHelper.hasPIPPermission
import cloud.app.csplayer.utils.Utils.activity
import cloud.app.csplayer.utils.Utils.logError
import cloud.app.csplayer.utils.Utils.setActivityInstance
import java.util.Locale
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

  fun init(act: Activity) {
    setActivityInstance(act)

    val componentActivity = activity as? ComponentActivity ?: return

    //https://stackoverflow.com/questions/52594181/how-to-know-if-user-has-disabled-picture-in-picture-feature-permission
    //https://developer.android.com/guide/topics/ui/picture-in-picture
    canShowPipMode =
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && // OS SUPPORT
        componentActivity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) && // HAS FEATURE, MIGHT BE BLOCKED DUE TO POWER DRAIN
        componentActivity.hasPIPPermission() // CHECK IF FEATURE IS ENABLED IN SETTINGS

    componentActivity.updateLocale()

    // Ask for notification permissions on Android 13
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      ContextCompat.checkSelfPermission(
        componentActivity,
        Manifest.permission.POST_NOTIFICATIONS
      ) != PackageManager.PERMISSION_GRANTED
    ) {
      val requestPermissionLauncher = componentActivity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
      ) { isGranted: Boolean ->
        Log.d(TAG, "Notification permission: $isGranted")
      }
      requestPermissionLauncher.launch(
        Manifest.permission.POST_NOTIFICATIONS
      )
    }
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

  val appLanguageExceptions = hashMapOf(
    "zh-rTW" to Locale.TRADITIONAL_CHINESE
  )

  fun setLocale(context: Context?, languageCode: String?) {
    if (context == null || languageCode == null) return
    val locale = appLanguageExceptions[languageCode] ?: Locale(languageCode)
    val resources: Resources = context.resources
    val config = resources.configuration
    Locale.setDefault(locale)
    config.setLocale(locale)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
      context.createConfigurationContext(config)
    resources.updateConfiguration(config, resources.displayMetrics)
  }

  fun Context.updateLocale() {
    val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
    val localeCode = settingsManager.getString(getString(R.string.locale_key), null)
    setLocale(this, localeCode)
  }
}
