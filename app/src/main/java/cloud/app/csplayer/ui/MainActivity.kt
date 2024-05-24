package cloud.app.csplayer.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.annotation.IdRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.fragment.NavHostFragment
import cloud.app.csplayer.R
import cloud.app.csplayer.databinding.ActivityMainBinding
import cloud.app.csplayer.utils.UIHelper.colorFromAttribute
import cloud.app.csplayer.utils.UIHelper.navigate
import cloud.app.csplayer.utils.UIHelper.setDefaultFocus
import cloud.app.csplayer.utils.Utils
import cloud.app.csplayer.utils.Utils.setActivityInstance
import cloud.app.csplayer.utils.isTvOrEmulator
import java.net.URI
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
  private lateinit var binding: ActivityMainBinding;

  override fun onCreate(savedInstanceState: Bundle?) {
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
}
