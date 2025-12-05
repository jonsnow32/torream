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
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.preference.PreferenceManager
import cloud.app.csplayer.FocusDirection
import cloud.app.csplayer.R
import cloud.app.csplayer.ui.player.PlayBackResult
import cloud.app.csplayer.ui.player.PlayerEventType
import cloud.app.csplayer.utils.AppUtils.isRtl
import cloud.app.csplayer.utils.UIHelper.hasPIPPermission
import cloud.app.csplayer.utils.Utils.activity
import cloud.app.csplayer.utils.Utils.logError
import cloud.app.csplayer.utils.Utils.setActivityInstance
import com.google.android.material.chip.ChipGroup
import timber.log.Timber
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

object CommonActivitty {
  var canEnterPipMode: Boolean = false
  var canShowPipMode: Boolean = false
  var isInPIPMode: Boolean = false
  var playerEventListener: ((PlayerEventType) -> Unit)? = null
  var keyEventListener: ((Pair<KeyEvent?, Boolean>) -> Boolean)? = null
  var activityResultEvent: ((PlayBackResult) -> Unit)? = null
  val displayMetrics: DisplayMetrics
    get() = activity?.resources?.displayMetrics ?: Resources.getSystem().displayMetrics

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

    componentActivity.updateLocale()

    canShowPipMode =
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && // OS SUPPORT
        componentActivity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) && // HAS FEATURE, MIGHT BE BLOCKED DUE TO POWER DRAIN
        componentActivity.hasPIPPermission() // CHECK IF FEATURE IS ENABLED IN SETTINGS


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
        Timber.d("Notification permission: $isGranted")
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
    "zh-rTW" to Locale.TRADITIONAL_CHINESE,
    "zh" to Locale.SIMPLIFIED_CHINESE
  )

  private var currentLocaleCode: String = ""

  fun setLocale(context: Context?, languageCode: String?) {
    if (context == null || languageCode == null) return

    if (currentLocaleCode == languageCode) return

    currentLocaleCode = languageCode
    val locale = appLanguageExceptions[languageCode] ?: Locale(languageCode)

    Locale.setDefault(locale)

    val resources: Resources = context.resources
    val config = resources.configuration

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      config.setLocale(locale)
    } else {
      @Suppress("DEPRECATION")
      config.locale = locale
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      context.createConfigurationContext(config)
    }
    resources.updateConfiguration(config, resources.displayMetrics)
  }

  fun Context.updateLocale(): Boolean {
    val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
    val localeCode =
      settingsManager.getString(getString(R.string.locale_key), Locale.getDefault().language)
      ?: Locale.getDefault().language

    val previousLocale = currentLocaleCode
    setLocale(this, localeCode)

    return previousLocale != currentLocaleCode
  }

  fun changeLanguage(context: Context?, languageCode: String?) {
    if (context == null || languageCode == null) return

    val settingsManager = PreferenceManager.getDefaultSharedPreferences(context)
    settingsManager.edit().putString(context.getString(R.string.locale_key), languageCode).apply()

    setLocale(context, languageCode)

    (context as? Activity)?.recreate()
  }

  private fun View.hasContent(): Boolean {
    return isShown && when (this) {
      //is RecyclerView -> this.childCount > 0
      is ViewGroup -> this.childCount > 0
      else -> true
    }
  }

  private fun localLook(from: View, id: Int): View? {
    if (id == View.NO_ID) return null
    var currentLook: View = from
    // limit to 15 look depth
    for (i in 0..15) {
      currentLook.findViewById<View?>(id)?.let { return it }
      currentLook = (currentLook.parent as? View) ?: break
    }
    return null
  }

  fun continueGetNextFocus(
    root: Any?,
    view: View,
    direction: FocusDirection,
    nextId: Int,
    depth: Int = 0
  ): View? {
    if (nextId == View.NO_ID) return null

    // do an initial search for the view, in case the localLook is too deep we can use this as
    // an early break and backup view
    var next =
      when (root) {
        is Activity -> root.findViewById(nextId)
        is View -> root.rootView.findViewById<View?>(nextId)
        else -> null
      } ?: return null

    next = localLook(view, nextId) ?: next
    val shown = next.hasContent()

    // if cant focus but visible then break and let android decide
    // the exception if is the view is a parent and has children that wants focus
    val hasChildrenThatWantsFocus = (next as? ViewGroup)?.let { parent ->
      parent.descendantFocusability == ViewGroup.FOCUS_AFTER_DESCENDANTS && parent.childCount > 0
    } ?: false
    if (!next.isFocusable && shown && !hasChildrenThatWantsFocus) return null

    // if not shown then continue because we will "skip" over views to get to a replacement
    if (!shown) {
      // we don't want a while true loop, so we let android decide if we find a recursive view
      if (next == view) return null
      return getNextFocus(root, next, direction, depth + 1)
    }

    (when (next) {
      is ChipGroup -> {
        next.children.firstOrNull { it.isFocusable && it.isShown }
      }

      else -> null
    })?.let {
      return it
    }

    // nothing wrong with the view found, return it
    return next
  }

  fun getNextFocus(
    root: Any?,
    view: View?,
    direction: FocusDirection,
    depth: Int = 0
  ): View? {
    // if input is invalid let android decide + depth test to not crash if loop is found
    if (view == null || depth >= 10 || root == null) {
      return null
    }

    var nextId = when (direction) {
      FocusDirection.Start -> {
        if (view.isRtl())
          view.nextFocusRightId
        else
          view.nextFocusLeftId
      }

      FocusDirection.Up -> {
        view.nextFocusUpId
      }

      FocusDirection.End -> {
        if (view.isRtl())
          view.nextFocusLeftId
        else
          view.nextFocusRightId
      }

      FocusDirection.Down -> {
        view.nextFocusDownId
      }
    }

    if (nextId == View.NO_ID) {
      // if not specified then use forward id
      nextId = view.nextFocusForwardId
      // if view is still not found to next focus then return and let android decide
      if (nextId == View.NO_ID)
        return null
    }
    return continueGetNextFocus(root, view, direction, nextId, depth)
  }

}
