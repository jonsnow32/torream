package cloud.app.csplayer

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.annotation.IdRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceManager
import cloud.app.csplayer.databinding.ActivityMainBinding
import cloud.app.csplayer.network.initClient
import cloud.app.csplayer.utils.CommonActivitty.updateLocale
import cloud.app.csplayer.utils.GlobalEvent.onColorSelectedEvent
import cloud.app.csplayer.utils.GlobalEvent.onDialogDismissedEvent
import cloud.app.csplayer.utils.UIHelper.colorFromAttribute
import cloud.app.csplayer.utils.UIHelper.navigate
import cloud.app.csplayer.utils.UIHelper.setDefaultFocus
import cloud.app.csplayer.utils.Utils
import cloud.app.csplayer.utils.Utils.USER_AGENT
import cloud.app.csplayer.utils.Utils.setActivityInstance
import cloud.app.csplayer.utils.isTvOrEmulator
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import cloud.app.csplayer.ui.colorpicker.ColorPickerDialogListener
import cloud.app.csplayer.utils.CommonActivitty
import com.lagradost.nicehttp.Requests
import com.lagradost.nicehttp.ResponseParser
import java.net.URI
import kotlin.reflect.KClass
import kotlin.system.exitProcess
import cloud.app.csplayer.utils.CommonActivitty.onUserLeaveHint

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

  override fun onCreate(savedInstanceState: Bundle?) {

    app.initClient(this)
    loadThemes()
    updateLocale()
    CommonActivitty.init(this)
    super.onCreate(savedInstanceState)

    binding = ActivityMainBinding.inflate(layoutInflater)
    val view = binding.root
    setContentView(view);

    val navHostFragment =
      supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
    val navController = navHostFragment.navController

    navController.addOnDestinationChangedListener { _: NavController, navDestination: NavDestination, bundle: Bundle? ->
      if (isTvOrEmulator()) {
        if (navDestination.matchDestination(R.id.browseFragment)) {
          attachBackPressedCallback()
        } else detachBackPressedCallback()
      }
    }
    handleAppIntent(intent)
  }
  private fun NavDestination.matchDestination(@IdRes destId: Int): Boolean =
    hierarchy.any { it.id == destId }

  override fun onNewIntent(intent: Intent?) {
    handleAppIntent(intent)
    super.onNewIntent(intent)
  }

  fun handleAppIntent(intent: Intent?) {
    if (intent == null) return;
    fun safeURI(uri: String) = Utils.normalSafeApiCall { URI(uri) }
    val dataString = intent.dataString;

    if(!dataString.isNullOrEmpty()) {
      val uri = safeURI(dataString);
      if(uri?.scheme == "csplayer") {

      } else if(uri?.scheme?.contains("http") == true || uri?.scheme?.contains("file") == true) {
        navigate(
          R.id.global_to_navigation_player,
          intent.extras
        )
      }
    }
  }

  private var backPressedCallback: OnBackPressedCallback? = null

  private fun attachBackPressedCallback() {
    if (backPressedCallback == null) {
      backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          showConfirmExitDialog()
          window?.navigationBarColor =
            colorFromAttribute(R.attr.primaryGrayBackground)
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
      setPositiveButton(R.string.yes) { _, _ -> exitProcess(0) }
      setNegativeButton(R.string.no) { _, _ -> }
    }
    builder.show().setDefaultFocus()
  }

  override fun onResume() {
    super.onResume()
    setActivityInstance(this)
  }

  fun loadThemes() {
    val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)

    val currentTheme =
      when (settingsManager.getString(getString(R.string.app_theme_key), "AmoledLight")) {
        "Black" -> R.style.AppTheme
        "Light" -> R.style.LightMode
        "Amoled" -> R.style.AmoledMode
        "AmoledLight" -> R.style.AmoledModeLight
        "Monet" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
          R.style.MonetMode else R.style.AppTheme

        else -> R.style.AppTheme
      }

    val currentOverlayTheme =
      when (settingsManager.getString(getString(R.string.primary_color_key), "Normal")) {
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

}
