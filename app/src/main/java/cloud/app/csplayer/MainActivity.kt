package cloud.app.csplayer

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.IdRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentResultListener
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import cloud.app.csplayer.databinding.ActivityMainBinding
import cloud.app.csplayer.network.initClient
import cloud.app.csplayer.ui.colorpicker.ColorPickerDialogListener
import cloud.app.csplayer.ui.player.PlayerEventType
import cloud.app.csplayer.ui.player.mpv.MPVUtils
import cloud.app.csplayer.utils.AppUtils.isCastApiAvailable
import cloud.app.csplayer.utils.CommonActivitty
import cloud.app.csplayer.utils.CommonActivitty.activityResultEvent
import cloud.app.csplayer.utils.CommonActivitty.getNextFocus
import cloud.app.csplayer.utils.CommonActivitty.keyEventListener
import cloud.app.csplayer.utils.CommonActivitty.onUserLeaveHint
import cloud.app.csplayer.utils.CommonActivitty.playerEventListener
import cloud.app.csplayer.utils.CommonActivitty.updateLocale
import cloud.app.csplayer.utils.Coroutines.ioSafe
import cloud.app.csplayer.utils.Event
import cloud.app.csplayer.utils.GlobalEvent.onColorSelectedEvent
import cloud.app.csplayer.utils.GlobalEvent.onDialogDismissedEvent
import cloud.app.csplayer.utils.InAppUpdater.Companion.runAutoUpdate
import cloud.app.csplayer.utils.UIHelper
import cloud.app.csplayer.utils.UIHelper.colorFromAttribute
import cloud.app.csplayer.utils.UIHelper.navigate
import cloud.app.csplayer.utils.UIHelper.setDefaultFocus
import cloud.app.csplayer.utils.Utils
import cloud.app.csplayer.utils.Utils.USER_AGENT
import cloud.app.csplayer.utils.Utils.logError
import cloud.app.csplayer.utils.Utils.setActivityInstance
import cloud.app.csplayer.utils.isTvOrEmulator
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.Session
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.lagradost.nicehttp.Requests
import com.lagradost.nicehttp.ResponseParser
import java.net.URI
import kotlin.reflect.KClass
import kotlin.system.exitProcess


enum class FocusDirection {
  Start,
  End,
  Up,
  Down,
}


var app = Requests(responseParser = object : ResponseParser {
  val mapper: ObjectMapper = jacksonObjectMapper().configure(
    DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
    false
  )

  override fun <T : Any> parse(text: String, kClass: KClass<T>): T {
    return mapper.readValue(text, kClass.java)
  }

  override fun <T : Any> parseSafe(text: String, kClass: KClass<T>): T? {
    return try {
      mapper.readValue(text, kClass.java)
    } catch (e: Exception) {
      null
    }
  }

  override fun writeValueAsString(obj: Any): String {
    return mapper.writeValueAsString(obj)
  }
}).apply {
  defaultHeaders = mapOf("user-agent" to USER_AGENT)
}

class MainActivity : AppCompatActivity(), ColorPickerDialogListener {
  private lateinit var binding: ActivityMainBinding;
  private var result: Pair<Int, Long>? = null;
  lateinit var mSessionManager: SessionManager
  private val mSessionManagerListener: SessionManagerListener<Session> by lazy { SessionManagerListenerImpl() }

  private inner class SessionManagerListenerImpl : SessionManagerListener<Session> {
    override fun onSessionStarting(session: Session) {
    }

    override fun onSessionStarted(session: Session, sessionId: String) {
      invalidateOptionsMenu()
    }

    override fun onSessionStartFailed(session: Session, i: Int) {
    }

    override fun onSessionEnding(session: Session) {
    }

    override fun onSessionResumed(session: Session, wasSuspended: Boolean) {
      invalidateOptionsMenu()
    }

    override fun onSessionResumeFailed(session: Session, i: Int) {
    }

    override fun onSessionSuspended(session: Session, i: Int) {
    }

    override fun onSessionEnded(session: Session, error: Int) {
    }

    override fun onSessionResuming(session: Session, s: String) {
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {

    app.initClient(this)
    loadThemes()
    updateLocale()
    CommonActivitty.init(this)
    MPVUtils.copyAssets(this)
    super.onCreate(savedInstanceState)

    if (!hasAllFilesAccessPermission(this)) {
      //create a dialog to request permission
      val dialog = AlertDialog.Builder(this).setTitle(R.string.request_permission_title)
        .setMessage(R.string.request_permission_message)
        .setPositiveButton(R.string.ok) { dialog, which ->
          requestAllFilesAccessPermission(this, 100)
          dialog.dismiss()
        }.create()
      dialog.show()
    }

    try {
      if (isCastApiAvailable()) {
        mSessionManager = CastContext.getSharedInstance(this).sessionManager
      }
    } catch (t: Throwable) {
      logError(t)
    }
    binding = ActivityMainBinding.inflate(layoutInflater)
    val view = binding.root
    setContentView(view);

    val navHostFragment =
      supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
    val navController = navHostFragment.navController

    navController.addOnDestinationChangedListener { _: NavController, navDestination: NavDestination, bundle: Bundle? ->
      if (isTvOrEmulator()) {
        if (navDestination.matchDestination(R.id.navigation_settings)) {
          attachBackPressedCallback()
        } else detachBackPressedCallback()
      }
    }

    activityResultEvent = { code, position ->
      // Do something with the result value
      result = Pair(code, position)
    }

    handleAppIntent(intent)
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray
  ) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode == 100) {
      if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        // Permission was granted, yay! Do the
        // contacts-related task you need to do.
        Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show()
      } else {
        // Permission was denied, handle the case where you inform the user and potentially disable features
        Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
      }
    }
  }

  override fun onStop() {
    super.onStop()
  }

  override fun finish() {
    result?.let {
      val resultIntent = Intent().apply {
        putExtra("position", it.second)
      }
      setResult(it.first, resultIntent)
    }
    super.finish()
  }

  //  override fun onSupportNavigateUp(): Boolean {
//    val navController = (supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment).navController
//    return navController.navigateUp() || super.onSupportNavigateUp()
//  }
  private fun NavDestination.matchDestination(@IdRes destId: Int): Boolean =
    hierarchy.any { it.id == destId }

  override fun onNewIntent(intent: Intent?) {
    handleAppIntent(intent)
    super.onNewIntent(intent)
  }

  fun handleAppIntent(intent: Intent?) {
    val extras = intent?.extras ?: return
    if (intent.action == Intent.ACTION_VIEW) {
      val navBuilder = NavOptions.Builder()
      val navOptions: NavOptions = navBuilder.setPopUpTo(R.id.mobile_navigation, true, true).build()
      navigate(
        R.id.global_to_navigation_mvp_player,
        extras,
        navOptions
      )
    }
  }

  private var backPressedCallback: OnBackPressedCallback? = null

  private fun attachBackPressedCallback() {
    if (backPressedCallback == null) {
      backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          if (supportFragmentManager.findFragmentById(R.id.nav_host_fragment)?.findNavController()
              ?.navigateUp() == false
          ) {
            showConfirmExitDialog()
            window?.navigationBarColor =
              colorFromAttribute(R.attr.primaryGrayBackground)
          }
        }
      }
    }

    backPressedCallback?.isEnabled = true
    onBackPressedDispatcher.addCallback(this, backPressedCallback ?: return)
  }

  private fun detachBackPressedCallback() {
    backPressedCallback?.isEnabled = false
  }

  private fun showConfirmExitDialog() {
    val builder: AlertDialog.Builder = AlertDialog.Builder(this)
    builder.setTitle(R.string.confirm_exit_dialog)
    builder.apply {
      // Forceful exit since back button can actually go back to setup
      setPositiveButton(R.string.yes) { _, _ -> finish() }
      setNegativeButton(R.string.no) { _, _ -> }
    }
    builder.show().setDefaultFocus()
  }

  override fun onResume() {
    super.onResume()
    setActivityInstance(this)
    ioSafe {
      runAutoUpdate()
    }

    try {
      if (isCastApiAvailable()) {
        //mCastSession = mSessionManager.currentCastSession
        mSessionManager.addSessionManagerListener(mSessionManagerListener)
      }
    } catch (e: Exception) {
      logError(e)
    }
  }

  override fun onPause() {
    super.onPause()

    try {
      if (isCastApiAvailable()) {
        mSessionManager.removeSessionManagerListener(mSessionManagerListener)
        //mCastSession = null
      }
    } catch (e: Exception) {
      logError(e)
    }
  }

  fun loadThemes() {
    val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)

    val currentTheme =
      when (settingsManager.getString(getString(R.string.app_theme_key), "AmoledLight")) {
        "Light" -> R.style.LightMode
        "AmoledLight" -> R.style.AmoledModeLight
        "Ocean" -> R.style.OceanMode
        "Monet" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
          R.style.MonetMode else R.style.AppTheme

        else -> R.style.AppTheme
      }

    val currentOverlayTheme =
      when (settingsManager.getString(getString(R.string.primary_color_key), "Banana")) {
        "Normal" -> R.style.OverlayPrimaryColorNormal
        "DandelionYellow" -> R.style.OverlayPrimaryColorDandelionYellow
        "CarnationPink" -> R.style.OverlayPrimaryColorCarnationPink
        "Orange" -> R.style.OverlayPrimaryColorOrange
        "DarkGreen" -> R.style.OverlayPrimaryColorDarkGreen
        "Maroon" -> R.style.OverlayPrimaryColorMaroon
        "NavyBlue" -> R.style.OverlayPrimaryColorNavyBlue
        "Grey" -> R.style.OverlayPrimaryColorGrey
        "White" -> R.style.OverlayPrimaryColorWhite
        "CoolBlue" -> R.style.OverlayPrimaryColorCoolBlue
        "Brown" -> R.style.OverlayPrimaryColorBrown
        "Purple" -> R.style.OverlayPrimaryColorPurple
        "Green" -> R.style.OverlayPrimaryColorGreen
        "GreenApple" -> R.style.OverlayPrimaryColorGreenApple
        "Red" -> R.style.OverlayPrimaryColorRed
        "Banana" -> R.style.OverlayPrimaryColorBanana
        "Party" -> R.style.OverlayPrimaryColorParty
        "Pink" -> R.style.OverlayPrimaryColorPink
        "Lavender" -> R.style.OverlayPrimaryColorLavender
        "Monet" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
          R.style.OverlayPrimaryColorMonet else R.style.OverlayPrimaryColorNormal

        "Monet2" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
          R.style.OverlayPrimaryColorMonetTwo else R.style.OverlayPrimaryColorNormal

        else -> R.style.OverlayPrimaryColorNormal
      }
    theme.applyStyle(currentTheme, true)
    theme.applyStyle(currentOverlayTheme, true)

    theme.applyStyle(
      R.style.LoadedStyle,
      true
    ) // THEME IS SET BEFORE VIEW IS CREATED TO APPLY THE THEME TO THE MAIN VIEW
  }

  override fun onColorSelected(dialogId: Int, color: Int) {
    onColorSelectedEvent.invoke(Pair(dialogId, color))
  }

  override fun onDialogDismissed(dialogId: Int) {
    onDialogDismissedEvent.invoke(dialogId)
  }

  override fun onUserLeaveHint() {
    super.onUserLeaveHint()
    onUserLeaveHint(this)
  }

  override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
    // 149 keycode_numpad 5
    when (keyCode) {
      KeyEvent.KEYCODE_FORWARD, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
        PlayerEventType.SeekForward
      }

      KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD, KeyEvent.KEYCODE_MEDIA_REWIND -> {
        PlayerEventType.SeekBack
      }

      KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_N -> {
        PlayerEventType.NextEpisode
      }

      KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_B -> {
        PlayerEventType.PrevEpisode
      }

      KeyEvent.KEYCODE_MEDIA_PAUSE -> {
        PlayerEventType.Pause
      }

      KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_BUTTON_START -> {
        PlayerEventType.Play
      }

      KeyEvent.KEYCODE_L, KeyEvent.KEYCODE_NUMPAD_7, KeyEvent.KEYCODE_7 -> {
        PlayerEventType.Lock
      }

      KeyEvent.KEYCODE_H, KeyEvent.KEYCODE_MENU -> {
        PlayerEventType.ToggleHide
      }

      KeyEvent.KEYCODE_M, KeyEvent.KEYCODE_VOLUME_MUTE -> {
        PlayerEventType.ToggleMute
      }

      KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_NUMPAD_9, KeyEvent.KEYCODE_9 -> {
        PlayerEventType.ShowMirrors
      }
      // OpenSubtitles shortcut
      KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_NUMPAD_8, KeyEvent.KEYCODE_8 -> {
        PlayerEventType.SearchSubtitlesOnline
      }

      KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_NUMPAD_3, KeyEvent.KEYCODE_3 -> {
        PlayerEventType.ShowSpeed
      }

      KeyEvent.KEYCODE_R, KeyEvent.KEYCODE_NUMPAD_0, KeyEvent.KEYCODE_0 -> {
        PlayerEventType.Resize
      }

      KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_NUMPAD_4, KeyEvent.KEYCODE_4 -> {
        PlayerEventType.SkipOp
      }

      KeyEvent.KEYCODE_V, KeyEvent.KEYCODE_NUMPAD_5, KeyEvent.KEYCODE_5 -> {
        PlayerEventType.SkipCurrentChapter
      }

      KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_ENTER -> { // space is not captured due to navigation
        PlayerEventType.PlayPauseToggle
      }

      else -> null
    }?.let { playerEvent ->
      playerEventListener?.invoke(playerEvent)
      return true;
    }

    //when (keyCode) {
    //    KeyEvent.KEYCODE_DPAD_CENTER -> {
    //        println("DPAD PRESSED")
    //    }
    //}
    return super.onKeyDown(keyCode, event)
  }


  @SuppressLint("RestrictedApi")
  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    val currentFocus = this.currentFocus

    event.keyCode.let { keyCode ->
      if (currentFocus == null || event.action != KeyEvent.ACTION_DOWN) return@let
      val nextView = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> getNextFocus(
          this,
          currentFocus,
          FocusDirection.Start
        )

        KeyEvent.KEYCODE_DPAD_RIGHT -> getNextFocus(
          this,
          currentFocus,
          FocusDirection.End
        )

        KeyEvent.KEYCODE_DPAD_UP -> getNextFocus(
          this,
          currentFocus,
          FocusDirection.Up
        )

        KeyEvent.KEYCODE_DPAD_DOWN -> getNextFocus(
          this,
          currentFocus,
          FocusDirection.Down
        )

        else -> null
      }
      // println("NEXT FOCUS : $nextView")
      if (nextView != null) {
        nextView.requestFocus()
        keyEventListener?.invoke(Pair(event, true))
        return true
      }

      if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER &&
        (currentFocus is SearchView || currentFocus is SearchView.SearchAutoComplete)
      ) {
        UIHelper.showInputMethod(this.currentFocus?.findFocus())
      }

      //println("Keycode: $keyCode")
      //showToast(
      //    this,
      //    "Got Keycode $keyCode | ${KeyEvent.keyCodeToString(keyCode)} \n ${event?.action}",
      //    Toast.LENGTH_LONG
      //)

    }

    // if someone else want to override the focus then don't handle the event as it is already
    // consumed. used in video player
    if (keyEventListener?.invoke(Pair(event, false)) == true) {
      return true
    }
    return super.dispatchKeyEvent(event)
  }

  fun hasAllFilesAccessPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      Environment.isExternalStorageManager()
    } else {
      // For Android 10 and below, check if WRITE_EXTERNAL_STORAGE permission is granted
      (ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
      ) == PackageManager.PERMISSION_GRANTED)
    }
  }

  fun requestAllFilesAccessPermission(activity: Activity, requestCode: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      try {
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
        intent.addCategory(Intent.CATEGORY_DEFAULT)
        val uri: Uri = Uri.fromParts("package", activity.packageName, null)
        intent.data = uri
        activity.startActivityForResult(intent, requestCode)
      } catch (e: Exception) {
        if (ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
          )
          != PackageManager.PERMISSION_GRANTED
        ) {
          ActivityCompat.requestPermissions(
            activity, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            requestCode
          )
        }
      }
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      // For Android 6 (Marshmallow) to Android 10, use WRITE_EXTERNAL_STORAGE
      if (ContextCompat.checkSelfPermission(
          activity,
          Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) != PackageManager.PERMISSION_GRANTED
      )
        ActivityCompat.requestPermissions(
          activity,
          arrayOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE
          ),
          requestCode
        )
    }
  }
}
