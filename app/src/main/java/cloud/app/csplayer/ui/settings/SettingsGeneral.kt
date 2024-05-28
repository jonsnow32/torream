package cloud.app.csplayer.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import cloud.app.csplayer.R
import cloud.app.csplayer.network.initClient
import cloud.app.csplayer.app
import cloud.app.csplayer.ui.settings.SettingsFragment.Companion.getPref
import cloud.app.csplayer.ui.settings.SettingsFragment.Companion.setPaddingBottom
import cloud.app.csplayer.ui.settings.SettingsFragment.Companion.setToolBarScrollFlags
import cloud.app.csplayer.ui.settings.SettingsFragment.Companion.setUpToolbar
import cloud.app.csplayer.utils.CommonActivitty.hideKeyboard
import cloud.app.csplayer.utils.CommonActivitty.setLocale
import cloud.app.csplayer.utils.LayoutMode
import cloud.app.csplayer.utils.SingleSelectionHelper.showBottomDialog
import cloud.app.csplayer.utils.SingleSelectionHelper.showDialog
import cloud.app.csplayer.utils.SubtitleHelper
import cloud.app.csplayer.utils.Utils.logError
import cloud.app.csplayer.utils.isLayout
import com.fasterxml.jackson.annotation.JsonProperty

// Change local language settings in the app.
fun getCurrentLocale(context: Context): String {
  val res = context.resources
  val conf = res.configuration

  return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
    conf?.locales?.get(0)?.toString() ?: "en"
  } else {
    @Suppress("DEPRECATION")
    conf?.locale?.toString() ?: "en"
  }
}

// idk, if you find a way of automating this it would be great
// https://www.iemoji.com/view/emoji/1794/flags/antarctica
// Emoji Character Encoding Data --> C/C++/Java Src
// https://en.wikipedia.org/wiki/List_of_ISO_639-1_codes leave blank for auto
val appLanguages = arrayListOf(
  /* begin language list */
  Triple("", "Afrikaans", "af"),
  Triple("", "عربي شامي", "ajp"),
  Triple("", "አማርኛ", "am"),
  Triple("", "العربية", "ar"),
  Triple("", "اللهجة النجدية", "ars"),
  Triple("", "български", "bg"),
  Triple("", "বাংলা", "bn"),
  Triple("\uD83C\uDDE7\uD83C\uDDF7", "português brasileiro", "bp"),
  Triple("", "čeština", "cs"),
  Triple("", "Deutsch", "de"),
  Triple("", "Ελληνικά", "el"),
  Triple("", "English", "en"),
  Triple("", "Esperanto", "eo"),
  Triple("", "español", "es"),
  Triple("", "فارسی", "fa"),
  Triple("", "fil", "fil"),
  Triple("", "français", "fr"),
  Triple("", "galego", "gl"),
  Triple("", "हिन्दी", "hi"),
  Triple("", "hrvatski", "hr"),
  Triple("", "magyar", "hu"),
  Triple("\uD83C\uDDEE\uD83C\uDDE9", "Bahasa Indonesia", "in"),
  Triple("", "italiano", "it"),
  Triple("\uD83C\uDDEE\uD83C\uDDF1", "עברית", "iw"),
  Triple("", "日本語 (にほんご)", "ja"),
  Triple("", "ಕನ್ನಡ", "kn"),
  Triple("", "한국어", "ko"),
  Triple("", "lietuvių kalba", "lt"),
  Triple("", "latviešu valoda", "lv"),
  Triple("", "македонски", "mk"),
  Triple("", "മലയാളം", "ml"),
  Triple("", "bahasa Melayu", "ms"),
  Triple("", "Malti", "mt"),
  Triple("", "ဗမာစာ", "my"),
  Triple("", "नेपाली", "ne"),
  Triple("", "Nederlands", "nl"),
  Triple("", "norsk nynorsk", "nn"),
  Triple("", "norsk bokmål", "no"),
  Triple("", "ଓଡ଼ିଆ", "or"),
  Triple("", "polski", "pl"),
  Triple("\uD83C\uDDF5\uD83C\uDDF9", "português", "pt"),
  Triple("\uD83E\uDD8D", "mmmm... monke", "qt"),
  Triple("", "română", "ro"),
  Triple("", "русский", "ru"),
  Triple("", "slovenčina", "sk"),
  Triple("", "Soomaaliga", "so"),
  Triple("", "svenska", "sv"),
  Triple("", "தமிழ்", "ta"),
  Triple("", "ትግርኛ", "ti"),
  Triple("", "Tagalog", "tl"),
  Triple("", "Türkçe", "tr"),
  Triple("", "українська", "uk"),
  Triple("", "اردو", "ur"),
  Triple("", "Tiếng Việt", "vi"),
  Triple("", "中文", "zh"),
  Triple("\uD83C\uDDF9\uD83C\uDDFC", "正體中文(臺灣)", "zh-rTW"),
  /* end language list */
).sortedBy { it.second.lowercase() } //ye, we go alphabetical, so ppl don't put their lang on top

class SettingsGeneral : PreferenceFragmentCompat() {
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    setUpToolbar(R.string.category_general)
    setPaddingBottom()
    setToolBarScrollFlags()
  }

  data class CustomSite(
    @JsonProperty("parentJavaClass") // javaClass.simpleName
    val parentJavaClass: String,
    @JsonProperty("name")
    val name: String,
    @JsonProperty("url")
    val url: String,
    @JsonProperty("lang")
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
      val filePath = uri.path
      println("Selected URI path: $uri - Full path: $filePath")

      // Stores the real URI using download_path_key
      // Important that the URI is stored instead of filepath due to permissions.
      PreferenceManager.getDefaultSharedPreferences(context)
        .edit().putString(getString(R.string.download_path_key), uri.toString()).apply()

      // From URI -> File path
      // File path here is purely for cosmetic purposes in settings
      (filePath ?: uri.toString()).let {
        PreferenceManager.getDefaultSharedPreferences(context)
          .edit().putString(getString(R.string.download_path_pref), it).apply()
      }
    }

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    activity?.hideKeyboard()
    setPreferencesFromResource(R.xml.settings_general, rootKey)
    val settingsManager = PreferenceManager.getDefaultSharedPreferences(requireContext())


    getPref(R.string.locale_key)?.setOnPreferenceClickListener { pref ->
      val tempLangs = appLanguages.toMutableList()
      val current = getCurrentLocale(pref.context)
      val languageCodes = tempLangs.map { (_, _, iso) -> iso }
      val languageNames = tempLangs.map { (emoji, name, iso) ->
        val flag = emoji.ifBlank { SubtitleHelper.getFlagFromIso(iso) ?: "ERROR" }
        "$flag $name"
      }
      val index = languageCodes.indexOf(current)

      activity?.showDialog(
        languageNames, index, getString(R.string.app_language), true, { }
      ) { languageIndex ->
        try {
          val code = languageCodes[languageIndex]
          setLocale(activity, code)
          settingsManager.edit().putString(getString(R.string.locale_key), code).apply()
          activity?.recreate()
        } catch (e: Exception) {
          logError(e)
        }
      }
      return@setOnPreferenceClickListener true
    }

    // disable preference on tvs and emulators
    getPref(R.string.battery_optimisation_key)?.isEnabled = context?.isLayout(LayoutMode.Phone.id) == true
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
        AlertDialog.Builder(it.context, R.style.AlertDialogCustom)
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

      activity?.showBottomDialog(
        prefNames.toList(),
        prefValues.indexOf(currentDns),
        getString(R.string.dns_pref),
        true,
        {}) {
        settingsManager.edit().putInt(getString(R.string.dns_pref), prefValues[it]).apply()
        context?.let { ctx -> app.initClient(ctx) }
      }
      return@setOnPreferenceClickListener true
    }

    fun getDownloadDirs(): List<String> {
//      return normalSafeApiCall {
//        context?.let { ctx ->
//          val defaultDir = VideoDownloadManager.getDefaultDir(ctx)?.filePath()
//
//          val first = listOf(defaultDir)
//          (try {
//            val currentDir = ctx.getBasePath().let { it.first?.filePath() ?: it.second }
//
//            (first +
//              ctx.getExternalFilesDirs("").mapNotNull { it.path } +
//              currentDir)
//          } catch (e: Exception) {
//            first
//          }).filterNotNull().distinct()
//        }
//      } ?:
      return emptyList()
    }



    getPref(R.string.download_path_key)?.setOnPreferenceClickListener {
      val dirs = getDownloadDirs()

//      val currentDir =
//        settingsManager.getString(getString(R.string.download_path_pref), null)
//          ?: context?.let { ctx -> VideoDownloadManager.getDefaultDir(ctx)?.filePath() }
//
//      activity?.showBottomDialog(
//        dirs + listOf("Custom"),
//        dirs.indexOf(currentDir),
//        getString(R.string.download_path_pref),
//        true,
//        {}) {
//        // Last = custom
//        if (it == dirs.size) {
//          try {
//            pathPicker.launch(Uri.EMPTY)
//          } catch (e: Exception) {
//            logError(e)
//          }
//        } else {
//          // Sets both visual and actual paths.
//          // key = used path
//          // pref = visual path
//          settingsManager.edit()
//            .putString(getString(R.string.download_path_key), dirs[it]).apply()
//          settingsManager.edit()
//            .putString(getString(R.string.download_path_pref), dirs[it]).apply()
//        }
//      }
      return@setOnPreferenceClickListener true
    }
  }
}
