package cloud.streamless.torream.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import cloud.streamless.torream.BuildConfig
import cloud.streamless.torream.R
import cloud.streamless.torream.app
import cloud.streamless.torream.network.initClient
import cloud.streamless.torream.ui.dialog.SelectionDialog
import cloud.streamless.torream.ui.settings.SettingsFragment.Companion.getPref
import cloud.streamless.torream.ui.settings.SettingsFragment.Companion.setToolBarScrollFlags
import cloud.streamless.torream.ui.settings.SettingsFragment.Companion.setUpToolbar
import cloud.streamless.torream.utils.CommonActivitty.hideKeyboard
import cloud.streamless.torream.utils.CommonActivitty.setLocale
import cloud.streamless.torream.utils.LayoutMode
import cloud.streamless.torream.utils.SubtitleHelper
import cloud.streamless.torream.utils.Utils.logError
import cloud.streamless.torream.utils.isLayout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Change local language settings in the app.
fun getCurrentLocale(context: Context): String {
  val res = context.resources
  val conf = res.configuration

  val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
    conf?.locales?.get(0)
  } else {
    @Suppress("DEPRECATION")
    conf?.locale
  }

  // Return only language code (e.g., "vi" instead of "vi_VN")
  return locale?.language ?: "en"
}

// Supported languages: English, Spanish, Chinese (Simplified)
val appLanguages = arrayListOf(
  /* begin language list */
  Triple("", "English", "en"),
  Triple("", "español", "es"),
  Triple("", "中文", "zh"),
  /* end language list */
).sortedBy { it.second.lowercase() }

class SettingsGeneral : PreferenceFragmentCompat() {

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    // Inflate custom layout with toolbar for PreferenceFragment
    val view = inflater.inflate(R.layout.settings_title_top, container, false)
    val listContainer = view.findViewById<ViewGroup>(android.R.id.list_container)

    // Let PreferenceFragmentCompat inflate its content into the list_container
    val preferenceView = super.onCreateView(inflater, listContainer, savedInstanceState)
    listContainer.removeAllViews()
    listContainer.addView(preferenceView)

    return view
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    setUpToolbar(R.string.category_general)
    setToolBarScrollFlags()
  }

  @Serializable
  data class CustomSite(
    @SerialName("parentJavaClass") // javaClass.simpleName
    val parentJavaClass: String,
    @SerialName("name")
    val name: String,
    @SerialName("url")
    val url: String,
    @SerialName("lang")
    val lang: String,
  )

  // Open file picker
  private val pathPicker =
    registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
      // It lies, it can be null if file manager quits.
      if (uri == null) return@registerForActivityResult
      val context = context ?: return@registerForActivityResult
      // RW perms for the path
      val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
        Intent.FLAG_GRANT_WRITE_URI_PERMISSION

      context.contentResolver.takePersistableUriPermission(uri, flags)

//      val file = File(requireNotNull(uri.path))
      val filePath = uri.toString()
      println("Selected URI path: $uri - Full path: $filePath")

      // Stores the real URI using download_path_key
      // Important that the URI is stored instead of filepath due to permissions.
      PreferenceManager.getDefaultSharedPreferences(context)
        .edit { putString(getString(R.string.download_path_key), uri.toString()) }

      // From URI -> File path
      // File path here is purely for cosmetic purposes in settings
      if(!filePath.isBlank()){
        PreferenceManager.getDefaultSharedPreferences(context)
          .edit { putString(getString(R.string.download_path_pref), filePath) }
      }
    }

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    activity?.hideKeyboard()
    setPreferencesFromResource(R.xml.settings_general, rootKey)
    val settingsManager = PreferenceManager.getDefaultSharedPreferences(requireContext())

    getPref(R.string.ad_key)?.isVisible = BuildConfig.DEBUG

    getPref(R.string.app_layout_key)?.setOnPreferenceClickListener {
      val prefNames = resources.getStringArray(R.array.app_layout)
      val prefValues = resources.getIntArray(R.array.app_layout_values)

      val currentLayout =
        settingsManager.getInt(getString(R.string.app_layout_key), -1)

      SelectionDialog.single(
        prefNames.toList(),
        prefValues.indexOf(currentLayout),
        getString(R.string.codec),
        false
      ).show(parentFragmentManager) { bundle ->
        bundle?.apply {
          getIntegerArrayList(SelectionDialog.ITEMS_SELECTED)?.get(0)?.let { index ->
            settingsManager.edit {
              putInt(getString(R.string.app_layout_key), prefValues[index])
            }
            activity?.recreate()
          }
        }
      }
      return@setOnPreferenceClickListener true
    }

    getPref(R.string.app_theme_key)?.setOnPreferenceClickListener {
      val prefNames = resources.getStringArray(R.array.themes_names).toMutableList()
      val prefValues = resources.getStringArray(R.array.themes_names_values).toMutableList()

      val currentLayout =
        settingsManager.getString(getString(R.string.app_theme_key), "System")

      // Handle unsupported themes (e.g., AmoledLight) by defaulting to System
      val currentIndex = prefValues.indexOf(currentLayout)
      val index = if (currentIndex == -1) {
        // Migrate unsupported theme to System
        settingsManager.edit {
          putString(getString(R.string.app_theme_key), "System")
        }
        prefValues.indexOf("System")
      } else {
        currentIndex
      }

      SelectionDialog.single(
        prefNames.toList(),
        index,
        getString(R.string.app_theme_settings),
        true
      ).show(parentFragmentManager) { bundle ->

        bundle?.apply {
          getIntegerArrayList(SelectionDialog.ITEMS_SELECTED)?.get(0)?.let { index ->
            try {
              settingsManager.edit {
                putString(getString(R.string.app_theme_key), prefValues[index])
              }
              // Apply the theme based on the selected value, not the index
              when (prefValues[index]) {
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
                  AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                }
              }
              activity?.recreate()
            } catch (e: Exception) {
              logError(e)
            }
          }
        }
      }
      return@setOnPreferenceClickListener true
    }


    getPref(R.string.locale_key)?.setOnPreferenceClickListener { pref ->
      val tempLangs = appLanguages.toMutableList()
      val current = getCurrentLocale(pref.context)
      val languageCodes = tempLangs.map { (_, _, iso) -> iso }
      val languageNames = tempLangs.map { (emoji, name, iso) ->
        val flag = emoji.ifBlank { SubtitleHelper.getFlagFromIso(iso) ?: "ERROR" }
        "$flag $name"
      }
      // Default to English if current locale is not in the list
      val currentIndex = languageCodes.indexOf(current)
      val index = if (currentIndex == -1) languageCodes.indexOf("en") else currentIndex

      SelectionDialog.single(
        languageNames, index, getString(R.string.app_language), true
      ).show(parentFragmentManager) { bundle ->

        bundle?.apply {
          getIntegerArrayList(SelectionDialog.ITEMS_SELECTED)?.get(0)?.let { index ->
            try {
              val code = languageCodes[index]
              setLocale(activity, code)
              settingsManager.edit { putString(getString(R.string.locale_key), code) }
              activity?.recreate()
            } catch (e: Exception) {
              logError(e)
            }
          }
        }

      }
      return@setOnPreferenceClickListener true
    }

    // disable preference on tvs and emulators
    getPref(R.string.battery_optimisation_key)?.isEnabled =
      context?.isLayout(LayoutMode.Phone.id) == true
    getPref(R.string.battery_optimisation_key)?.setOnPreferenceClickListener {
      val ctx = context ?: return@setOnPreferenceClickListener false

//      if (isAppRestricted(ctx)) {
//        showBatteryOptimizationDialog(ctx)
//      } else {
//        showToast(R.string.app_unrestricted_toast)
//      }

      true
    }



    getPref(R.string.legal_notice_key)?.setOnPreferenceClickListener {
      val builder: AlertDialog.Builder =
        AlertDialog.Builder(it.context, R.style.BaseMaterialDialogTheme)
      builder.setTitle(R.string.legal_notice)
      builder.setMessage(R.string.legal_notice_text)
      builder.show()
      return@setOnPreferenceClickListener true
    }

    getPref(R.string.dns_key)?.setOnPreferenceClickListener {
      val prefNames = resources.getStringArray(R.array.dns_pref)
      val prefValues = resources.getIntArray(R.array.dns_pref_values)

      val currentDns =
        settingsManager.getInt(getString(R.string.dns_pref), 0)

      SelectionDialog.single(
        prefNames.toList(),
        prefValues.indexOf(currentDns),
        getString(R.string.dns_pref),
        true
      ).show(parentFragmentManager) { bundle ->
        bundle?.apply {
          getIntegerArrayList(SelectionDialog.ITEMS_SELECTED)?.get(0)?.let { index ->
            try {
              settingsManager.edit { putInt(getString(R.string.dns_pref), prefValues[index]) }
              context?.let { ctx -> app.initClient(ctx) }
            } catch (e: Exception) {
              logError(e)
            }
          }
        }


      }
      return@setOnPreferenceClickListener true
    }

    getPref(R.string.ad_key)?.setOnPreferenceClickListener {
      findNavController().navigate(R.id.adTestFragment)
      return@setOnPreferenceClickListener true
    }
  }
}
