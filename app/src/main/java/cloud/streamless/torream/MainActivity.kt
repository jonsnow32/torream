package cloud.streamless.torream

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color.TRANSPARENT
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import android.util.AttributeSet
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.IdRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.view.updateLayoutParams
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager
import cloud.streamless.torream.databinding.ActivityMainBinding
import cloud.streamless.torream.datastore.Serializer
import cloud.streamless.torream.model.PlaybackData
import cloud.streamless.torream.model.PlaybackData.Companion.KEY_PLAYBACK_JSON_URI
import cloud.streamless.torream.model.VideoLink
import cloud.streamless.torream.utils.PlaybackDataHelper
import cloud.streamless.torream.network.initClient
import cloud.streamless.torream.ui.colorpicker.ColorPickerDialogListener
import cloud.streamless.torream.ui.player.PlayBackResult
import cloud.streamless.torream.ui.player.PlayerEventType
import cloud.streamless.torream.ui.player.mpv.MPVUtils
import cloud.streamless.torream.utils.AppUtils.isCastApiAvailable
import cloud.streamless.torream.utils.applyAppColorTheme
import cloud.streamless.torream.utils.CommonActivitty
import cloud.streamless.torream.utils.CommonActivitty.activityResultEvent
import cloud.streamless.torream.utils.ContinueWatchingReminderWorker
import cloud.streamless.torream.utils.CommonActivitty.getNextFocus
import cloud.streamless.torream.utils.CommonActivitty.keyEventListener
import cloud.streamless.torream.utils.CommonActivitty.onUserLeaveHint
import cloud.streamless.torream.utils.CommonActivitty.playerEventListener
import cloud.streamless.torream.utils.CommonActivitty.screenHeight
import cloud.streamless.torream.utils.CommonActivitty.setLocale
import cloud.streamless.torream.utils.CommonActivitty.updateLocale
import cloud.streamless.torream.utils.Coroutines.ioSafe
import cloud.streamless.torream.utils.GlobalEvent.onColorSelectedEvent
import cloud.streamless.torream.utils.GlobalEvent.onDialogDismissedEvent
import cloud.streamless.torream.utils.InAppUpdater.Companion.runAutoUpdate
import cloud.streamless.torream.utils.UIHelper
import cloud.streamless.torream.utils.UIHelper.getResourceColor
import cloud.streamless.torream.utils.UIHelper.isLandscape
import cloud.streamless.torream.utils.UIHelper.isNightMode
import cloud.streamless.torream.utils.UIHelper.navigate
import cloud.streamless.torream.utils.UIHelper.setDefaultFocus
import cloud.streamless.torream.utils.Utils.USER_AGENT
import cloud.streamless.torream.utils.Utils.logError
import cloud.streamless.torream.utils.Utils.setActivityInstance
import cloud.streamless.torream.utils.isTvOrEmulator
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.Session
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.lagradost.nicehttp.Requests
import com.lagradost.nicehttp.ResponseParser
import cloud.streamless.torream.ui.dialog.UrlInputDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.serialization.serializer
import timber.log.Timber
import kotlin.reflect.KClass


enum class FocusDirection {
  Start,
  End,
  Up,
  Down,
}


var app = Requests(responseParser = object : ResponseParser {
  private val json = Serializer.json

  override fun <T : Any> parse(text: String, kClass: KClass<T>): T {
    @Suppress("UNCHECKED_CAST")
    return json.decodeFromString(
      serializer(kClass.java) as kotlinx.serialization.KSerializer<T>,
      text
    )
  }

  override fun <T : Any> parseSafe(text: String, kClass: KClass<T>): T? {
    return try {
      @Suppress("UNCHECKED_CAST")
      json.decodeFromString(serializer(kClass.java) as kotlinx.serialization.KSerializer<T>, text)
    } catch (_: Exception) {
      null
    }
  }

  override fun writeValueAsString(obj: Any): String {
    @Suppress("UNCHECKED_CAST")
    return json.encodeToString(serializer(obj::class.java), obj)
  }
}).apply {
  defaultHeaders = mapOf("user-agent" to USER_AGENT)
}

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), ColorPickerDialogListener {

  val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
  private val viewmodel by viewModels<MainActivityViewModel>()
  private var result = mutableListOf<PlayBackResult>()
  lateinit var mSessionManager: SessionManager
  private val mSessionManagerListener: SessionManagerListener<Session> by lazy { SessionManagerListenerImpl() }

  // Notification permission launcher - deferred until first download
  private val notificationPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    if (isGranted) {
      Timber.d("Notification permission granted")
    } else {
      Timber.d("Notification permission denied")
    }
  }

  // Cập nhật locale TRƯỚC khi inflate layout
  override fun attachBaseContext(newBase: Context) {
    val settingsManager = PreferenceManager.getDefaultSharedPreferences(newBase)
    val localeCode = settingsManager.getString(
      newBase.getString(R.string.locale_key),
      java.util.Locale.getDefault().language
    ) ?: java.util.Locale.getDefault().language

    setLocale(newBase, localeCode)
    super.attachBaseContext(newBase)
  }

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

  override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View? {
    return super.onCreateView(name, context, attrs)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    loadThemes()
    applyAppColorTheme()
    super.onCreate(savedInstanceState)

    ioSafe {
      app.initClient(this@MainActivity)
      MPVUtils.copyAssets(this@MainActivity)
    }
    updateLocale()
    CommonActivitty.init(this)

    @Suppress("DEPRECATION")
    try {
      if (isCastApiAvailable()) {
        mSessionManager = CastContext.getSharedInstance(this).sessionManager
      }
    } catch (t: Throwable) {
      logError(t)
    }
    setContentView(binding.root)

    enableEdgeToEdge(
      SystemBarStyle.auto(TRANSPARENT, TRANSPARENT),
      if (isNightMode()) SystemBarStyle.dark(TRANSPARENT)
      else SystemBarStyle.light(TRANSPARENT, TRANSPARENT)
    )

    val navHostFragment =
      supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
    val navController = navHostFragment.navController


    val rippleColor = ColorStateList.valueOf(getResourceColor(R.attr.colorPrimary, 0.1f))

    binding.navView.apply {
      itemRippleColor = rippleColor
      itemActiveIndicatorColor = rippleColor
      setupWithNavController(navController)
      setOnItemSelectedListener { item ->
        onNavDestinationSelected(
          item,
          navController
        )
      }
    }

    binding.navRailView.apply {
      itemRippleColor = rippleColor
      itemActiveIndicatorColor = rippleColor
      setupWithNavController(navController)
      if (isTvOrEmulator()) {
        background?.alpha = 200
      } else {
        background?.alpha = 255
      }
      setOnItemSelectedListener { item ->
        onNavDestinationSelected(
          item,
          navController
        )
      }
    }
    //calculate safeRect
    val isRail = isLandscape()
    binding.root.post {
      val safeRect = resources.run {
        val height = getDimensionPixelSize(R.dimen.nav_height)
        if (!isRail) return@run MainActivityViewModel.SafeRect(bottom = height)
        else
          return@run MainActivityViewModel.SafeRect(start = height)
      }
      viewmodel.setNavSafeRect(safeRect)
    }

    ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
      viewmodel.setSystemSafeRect(this, insets)
      binding.statusBarScrim.updateLayoutParams {
        height = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
      }
      val cutout = insets.displayCutout
      if (cutout != null) {
        viewmodel.setNotchSafeRect(
          MainActivityViewModel.SafeRect(
            0, cutout.safeInsetBottom, cutout.safeInsetLeft, cutout.safeInsetRight
          )
        )
      }
      insets
    }

    navController.addOnDestinationChangedListener { _: NavController, navDestination: NavDestination, bundle: Bundle? ->
      // Hide bottom navigation when in MPV player
      if (navDestination.matchDestination(R.id.mvpFragmentPlayer)) {
        binding.navView.isGone = true
        binding.navRailView.isGone = true
      } else {
        updateNavSafeRect(isLandscape())
      }

      if (isTvOrEmulator()) {
        if (navDestination.matchDestination(R.id.navigation_settings)) {
          attachBackPressedCallback()
        } else detachBackPressedCallback()
      }
    }

    activityResultEvent = { result ->
      // Do something with the result value
      if (isSameEpisode) {
        this.result.clear()
        this.result.add(result)
      } else {
        val older = this.result.firstOrNull { it.episode == result.episode }
        if (older != null) {
          this.result.remove(older)
          this.result.add(result)
        } else {
          this.result.add(result)
        }
      }
    }

    handleAppIntent(intent)

    StrictMode.setThreadPolicy(
      StrictMode.ThreadPolicy.Builder()
        .detectAll()
        .penaltyLog()
        .build()
    )

//
//    binding.root.viewTreeObserver.addOnGlobalFocusChangeListener { _, newFocus ->
//      centerView(newFocus)
//    }
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

  private var isSameEpisode = true
  override fun finish() {
    if (result.isNotEmpty()) {
      if (isSameEpisode) {
        result.first().apply {
          val resultIntent = Intent().apply {
            putExtra("position", position)
            putExtra("end_by", reason)
          }
          setResult(code, resultIntent)
        }
      } else {
        val positions = result.map { "${it.episode}:${it.position}" }
        val resultIntent = Intent().apply {
          putExtra("playlist", true)
          putExtra("positions", positions.joinToString(separator = ","))
          putExtra("end_by", result.last().reason)
        }
        setResult(result.last().code, resultIntent)
      }
    }
    super.finish()
  }


  private fun NavDestination.matchDestination(@IdRes destId: Int): Boolean =
    hierarchy.any { it.id == destId }

  override fun onNewIntent(intent: Intent) {
    handleAppIntent(intent)
    super.onNewIntent(intent)
  }

  fun handleAppIntent(intent: Intent?) {
    if (intent == null) return

    val navBuilder = NavOptions.Builder()
    val navOptions: NavOptions = navBuilder.setPopUpTo(R.id.mobile_navigation, true, true).build()

    // Handle download notification click - navigate to Downloads section in Library
    if (intent.action == "cloud.streamless.torream.OPEN_DOWNLOADS") {
      try {
        val bundle = Bundle().apply {
          putInt("section", 0) // 0 = DOWNLOADS section in LibrarySection enum
        }
        navigate(R.id.navigation_libraryFragment, bundle, navOptions)
        return
      } catch (e: Exception) {
        Timber.e(e, "Failed to navigate to Downloads section")
      }
    }

    // Handle continue watching notification click - resume the unfinished video
    if (intent.action == ContinueWatchingReminderWorker.ACTION_CONTINUE_WATCHING) {
      val mediaUri = intent.getStringExtra(ContinueWatchingReminderWorker.EXTRA_MEDIA_URI)
      if (mediaUri != null) {
        try {
          val name = intent.getStringExtra(ContinueWatchingReminderWorker.EXTRA_MEDIA_NAME) ?: ""
          val position = intent.getLongExtra(ContinueWatchingReminderWorker.EXTRA_POSITION, 0L)
          val playbackData = PlaybackData(
            title = name,
            videoLinks = listOf(
              VideoLink(
                url = mediaUri,
                name = name,
                subtitles = emptyList(),
                position = position
              )
            ),
            subtitles = emptyList(),
            videoStartIndex = 0,
            subtitleStartIndex = 0,
            isSameEpisode = true,
            hasAd = false
          )
          navigate(
            R.id.global_to_navigation_mpv_player,
            PlaybackDataHelper.createBundle(playbackData),
            navOptions
          )
          return
        } catch (e: Exception) {
          Timber.e(e, "Failed to resume media from continue watching notification")
        }
      }
    }

    //handle DOWNLOAD_URL action with header
    if (intent.action?.endsWith(".action.DOWNLOAD_URL") == true) {
      val url = intent.getStringExtra("url") ?: return
      val name = intent.getStringExtra("name")
      val hasAds = intent.getBooleanExtra("hasAds", true)
      val headersArray = intent.getStringArrayExtra("headers")
      val headerMap = mutableMapOf<String, String>()

      // Convert array to map - array contains alternating keys and values
      // [key1, value1, key2, value2, ...]
      if (headersArray != null && headersArray.size >= 2) {
        var i = 0
        while (i < headersArray.size - 1) {
          headerMap[headersArray[i]] = headersArray[i + 1]
          i += 2
        }
        Timber.d("Converted ${headerMap.size} headers from array to map")
      }

      UrlInputDialog.newInstance(url, name, if (headerMap.isNotEmpty()) headerMap else null, hasAds).show(supportFragmentManager)
      return
    }

    // Handle ACTION_VIEW for PlaybackData
    if (intent.action != Intent.ACTION_VIEW) return

    // Try to get PlaybackData from URI (inter-app communication via FileProvider)
    val jsonUri = intent.data
    if (jsonUri != null && jsonUri.scheme == "content") {
      try {
        // Pass URI string to PlayerViewModel (let it read lazily)
        // This avoids reading the file twice and Bundle size limit
        val bundle = Bundle().apply {
          putString(KEY_PLAYBACK_JSON_URI, jsonUri.toString())
        }
        navigate(
          R.id.global_to_navigation_mpv_player,
          bundle,
          navOptions
        )
        return
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }

  private var backPressedCallback: OnBackPressedCallback? = null

  @Suppress("DEPRECATION")
  private fun attachBackPressedCallback() {
    if (backPressedCallback == null) {
      backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          if (supportFragmentManager.findFragmentById(R.id.nav_host_fragment)?.findNavController()
              ?.navigateUp() == false
          ) {
            showConfirmExitDialog()
            // Avoid calling deprecated Window.setNavigationBarColor on newer Android.
            // Rely on theme or WindowCompat/decor fitting for system bar styling.
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

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    val isLandScape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE

    val navHostFragment =
      supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
    navHostFragment.navController.currentDestination?.let {
      if (!it.matchDestination(R.id.mvpFragmentPlayer))
        updateNavSafeRect(isLandScape)
      else {
        binding.navView.isGone = true
        binding.navRailView.isGone = true
      }
    }


  }

  private fun updateNavSafeRect(isLandscape: Boolean) {
    binding.navView.isGone = isLandscape
    binding.navRailView.isGone = !isLandscape

    binding.root.post {
      val safeRect = resources.run {
        val height = getDimensionPixelSize(R.dimen.nav_height)
        if (!isLandscape)
          return@run MainActivityViewModel.SafeRect(bottom = height)
        else
          return@run MainActivityViewModel.SafeRect(start = height)
      }
      viewmodel.setNavSafeRect(safeRect)
    }
  }

  private fun showConfirmExitDialog() {
    val builder: AlertDialog.Builder = AlertDialog.Builder(this, R.style.BaseMaterialDialogTheme)
    builder.setTitle(R.string.confirm_exit_dialog)
    builder.apply {
      // Forceful exit since back button can actually go back to setup
      setPositiveButton(R.string.yes) { _, _ -> finish() }
      setNegativeButton(R.string.no) { _, _ -> }
    }
    builder.show().setDefaultFocus()
  }

  /**
   * Request notification permission on-demand when download is initiated
   * Only requests on Android 13+ if permission hasn't been granted yet
   */
  @Suppress("unused")
  fun requestNotificationPermissionForDownload() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      // Only request if permission is not already granted
      if (ContextCompat.checkSelfPermission(
          this,
          android.Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
      ) {
        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
      }
    }
  }

  override fun onResume() {
    super.onResume()
    setActivityInstance(this)
    PreferenceManager.getDefaultSharedPreferences(this).edit {
      putLong(ContinueWatchingReminderWorker.PREF_APP_LAST_OPENED_AT, System.currentTimeMillis())
    }
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
    try {
      val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
      val themeKey = getString(R.string.app_theme_key)
      val savedTheme = settingsManager.getString(themeKey, "System") // Default to System theme

      when (savedTheme) {
        "Light" -> {
          AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        "Dark" -> {
          AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }

        "System" -> {
          AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }

        else -> {
          // Default to System theme
          AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
      }
    } catch (e: Exception) {
      logError(e)
      // Fallback to system theme on error
      AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }
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
      return true
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

  private var lastNavTime = 0L
  private fun onNavDestinationSelected(item: MenuItem, navController: NavController): Boolean {
    val currentTime = System.currentTimeMillis()
    if (currentTime - lastNavTime < 200) return false
    lastNavTime = currentTime

    val destinationId = item.itemId

    // Check if we are already at the selected destination
    if (navController.currentDestination?.id == destinationId) return false

    val builder = NavOptions.Builder().setLaunchSingleTop(true).setRestoreState(true)
      .setEnterAnim(R.anim.enter_anim)
      .setExitAnim(R.anim.exit_anim)
      .setPopEnterAnim(R.anim.pop_enter)
      .setPopExitAnim(R.anim.pop_exit)
    if (item.order and Menu.CATEGORY_SECONDARY == 0) {
      builder.setPopUpTo(
        navController.graph.findStartDestination().id,
        inclusive = false,
        saveState = false
      )
    }
    return try {
      navController.navigate(destinationId, null, builder.build())
      navController.currentDestination?.matchDestination(destinationId) == true
    } catch (e: IllegalArgumentException) {
      Log.e("NavigationError", "Failed to navigate: ${e.message}")
      false
    }
  }

  private fun centerView(view: View?) {
    if (view == null) return
    try {
      Timber.v("centerView: $view")
      val r = Rect(0, 0, 0, 0)
      view.getDrawingRect(r)
      val x = r.centerX()
      val y = r.centerY()
      val dx = r.width() / 2 //screenWidth / 2
      val dy = screenHeight / 2
      val r2 = Rect(x - dx, y - dy, x + dx, y + dy)
      view.requestRectangleOnScreen(r2, false)
      // TvFocus.current =TvFocus.current.copy(y=y.toFloat())
    } catch (_: Throwable) {
    }
  }

}
