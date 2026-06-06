package cloud.streamless.torream.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import cloud.streamless.torream.R
import cloud.streamless.torream.ui.settings.SettingsFragment.Companion.getPref
import cloud.streamless.torream.ui.settings.SettingsFragment.Companion.setToolBarScrollFlags
import cloud.streamless.torream.ui.settings.SettingsFragment.Companion.setUpToolbar
import cloud.streamless.torream.utils.CommonActivitty.hideKeyboard
import cloud.streamless.torream.utils.Utils.showToast

class SettingsCast : PreferenceFragmentCompat() {

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    val view = inflater.inflate(R.layout.settings_title_top, container, false)
    val listContainer = view.findViewById<ViewGroup>(android.R.id.list_container)
    val preferenceView = super.onCreateView(inflater, listContainer, savedInstanceState)
    listContainer.removeAllViews()
    listContainer.addView(preferenceView)
    return view
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    setUpToolbar(R.string.cast_settings)
    setToolBarScrollFlags()
  }

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    activity?.hideKeyboard()
    setPreferencesFromResource(R.xml.settings_cast, rootKey)
    val settingsManager = PreferenceManager.getDefaultSharedPreferences(requireContext())

    // Chromecast enable/disable
    getPref(R.string.enable_chromecast_key)?.setOnPreferenceChangeListener { _, newValue ->
      val enabled = newValue as Boolean
      if (enabled) {
        showToast(R.string.chromecast_enabled_toast)
      } else {
        showToast(R.string.chromecast_disabled_toast)
      }
      true
    }

    // Fcast enable/disable
    getPref(R.string.enable_fcast_key)?.setOnPreferenceChangeListener { _, newValue ->
      val enabled = newValue as Boolean
      if (enabled) {
        // Check if WebVideoCaster is installed
        val webVideoCasterPackage = "com.instantbits.cast.webvideo"
        val isInstalled = try {
          requireContext().packageManager.getPackageInfo(webVideoCasterPackage, 0)
          true
        } catch (e: Exception) {
          false
        }

        if (!isInstalled) {
          showToast(R.string.fcast_app_required_toast)
          // Open Play Store to install WebVideoCaster
          try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
              data = Uri.parse("market://details?id=$webVideoCasterPackage")
              setPackage("com.android.vending")
            }
            startActivity(intent)
          } catch (e: Exception) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
              data = Uri.parse("https://play.google.com/store/apps/details?id=$webVideoCasterPackage")
            }
            startActivity(intent)
          }
          return@setOnPreferenceChangeListener false
        } else {
          showToast(R.string.fcast_enabled_toast)
        }
      } else {
        showToast(R.string.fcast_disabled_toast)
      }
      true
    }

    // Cast quality preference
    getPref(R.string.cast_quality_key)?.setOnPreferenceChangeListener { _, _ ->
      showToast(R.string.cast_quality_changed_toast)
      true
    }

    // About Chromecast info
    getPref(R.string.about_chromecast_key)?.setOnPreferenceClickListener {
      val intent = Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse("https://www.google.com/chromecast/")
      }
      startActivity(intent)
      true
    }

    // About Fcast info
    getPref(R.string.about_fcast_key)?.setOnPreferenceClickListener {
      val intent = Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse("https://play.google.com/store/apps/details?id=com.instantbits.cast.webvideo")
      }
      startActivity(intent)
      true
    }
  }
}
