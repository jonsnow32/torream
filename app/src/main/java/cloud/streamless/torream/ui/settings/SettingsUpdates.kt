package cloud.streamless.torream.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import cloud.streamless.torream.BuildConfig
import cloud.streamless.torream.R
import cloud.streamless.torream.databinding.LogcatBinding
import cloud.streamless.torream.ui.dialog.SelectionDialog
import cloud.streamless.torream.ui.settings.SettingsFragment.Companion.getPref
import cloud.streamless.torream.ui.settings.SettingsFragment.Companion.setToolBarScrollFlags
import cloud.streamless.torream.ui.settings.SettingsFragment.Companion.setUpToolbar
import cloud.streamless.torream.utils.CommonActivitty.hideKeyboard
import cloud.streamless.torream.utils.UIHelper.clipboardHelper
import cloud.streamless.torream.utils.UIHelper.dismissSafe
import cloud.streamless.torream.utils.Utils.logError
import cloud.streamless.torream.utils.Utils.showToast
import cloud.streamless.torream.utils.txt
import java.io.BufferedReader
import java.io.InputStreamReader
import androidx.core.content.edit

class SettingsUpdates : PreferenceFragmentCompat() {

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
    setUpToolbar(R.string.category_updates)
    setToolBarScrollFlags()
  }

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    activity?.hideKeyboard()
    setPreferencesFromResource(R.xml.settings_updates, rootKey)
    //val settingsManager = PreferenceManager.getDefaultSharedPreferences(requireContext())


    getPref(R.string.show_logcat_key)?.setOnPreferenceClickListener { pref ->
      val builder =
        AlertDialog.Builder(pref.context, R.style.BaseMaterialDialogTheme)

      val binding = LogcatBinding.inflate(layoutInflater, null, false)
      builder.setView(binding.root)

      val dialog = builder.create()
      dialog.show()
      val log = StringBuilder()
      try {
        //https://developer.android.com/studio/command-line/logcat
        val process = Runtime.getRuntime().exec("logcat -d")
        val bufferedReader = BufferedReader(
          InputStreamReader(process.inputStream)
        )

        var line: String?
        while (bufferedReader.readLine().also { line = it } != null) {
          log.append("${line}\n")
        }
      } catch (e: Exception) {
        logError(e) // kinda ironic
      }

      val text = log.toString()
      binding.text1.text = text

      binding.copyBtt.setOnClickListener {
        clipboardHelper(txt("Logcat"), text)
        dialog.dismissSafe(activity)
      }

      binding.clearBtt.setOnClickListener {
        Runtime.getRuntime().exec("logcat -c")
        dialog.dismissSafe(activity)
      }

      binding.saveBtt.setOnClickListener {
//                val date = SimpleDateFormat("yyyy_MM_dd_HH_mm").format(Date(currentTimeMillis()))
//                var fileStream: OutputStream? = null
//                try {
//                    fileStream = OutputStream. VideoDownloadManager.setupStream(
//                            it.context,
//                            "logcat_${date}",
//                            null,
//                            "txt",
//                            false
//                        ).openNew()
//                    fileStream.writer().write(text)
//                    dialog.dismissSafe(activity)
//                } catch (t: Throwable) {
//                    logError(t)
//                    showToast(t.message)
//                } finally {
//                    fileStream?.closeQuietly()
//                }
      }

      binding.closeBtt.setOnClickListener {
        dialog.dismissSafe(activity)
      }

      return@setOnPreferenceClickListener true
    }

    getPref(R.string.apk_installer_key)?.setOnPreferenceClickListener {
      val settingsManager = PreferenceManager.getDefaultSharedPreferences(it.context)

      val prefNames = resources.getStringArray(R.array.apk_installer_pref)
      val prefValues = resources.getIntArray(R.array.apk_installer_values)

      val currentInstaller =
        settingsManager.getInt(getString(R.string.apk_installer_key), 0)

      SelectionDialog.single(
        prefNames.toList(),
        prefValues.indexOf(currentInstaller),
        getString(R.string.apk_installer_settings),
        true).show(parentFragmentManager) { bundle ->
        bundle?.apply {
          getIntegerArrayList(SelectionDialog.ITEMS_SELECTED)?.get(0)?.let { index ->
            try {
              settingsManager.edit {
                putInt(getString(R.string.apk_installer_key), prefValues[index])
              }
            } catch (e: Exception) {
              logError(e)
            }
          }
        }
      }
      return@setOnPreferenceClickListener true
    }
    val checkUpdatePref = getPref(R.string.manual_check_update_key)
    checkUpdatePref?.summary = BuildConfig.VERSION_NAME
    checkUpdatePref?.setOnPreferenceClickListener {
      val ctx = it.context
      // If running a debug build the package may have a suffix (e.g. .debug) and won't exist on Play Store.
      // Try to derive the release package id by stripping common debug/test suffixes.
      val pkg = ctx.packageName
      val playPkg = when {
        pkg.endsWith(".debug") -> pkg.removeSuffix(".debug")
        pkg.endsWith("-debug") -> pkg.removeSuffix("-debug")
        pkg.endsWith(".dev") -> pkg.removeSuffix(".dev")
        pkg.endsWith(".staging") -> pkg.removeSuffix(".staging")
        pkg.endsWith(".beta") -> pkg.removeSuffix(".beta")
        pkg.contains(".debug") -> pkg.substringBefore(".debug")
        else -> pkg
      }
      // Open app page in Play Store app if available, otherwise open in browser
      val playIntent = Intent(Intent.ACTION_VIEW, "market://details?id=$playPkg".toUri()).apply {
        setPackage("com.android.vending")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      // Prefer Play Store app when it can handle the intent to avoid web redirects for debug package names
      val pm = ctx.packageManager
      if (playIntent.resolveActivity(pm) != null) {
        ctx.startActivity(playIntent)
      } else {
        // Play Store app not available/doesn't handle the intent — open web fallback
        val webIntent = Intent(Intent.ACTION_VIEW,
          "https://play.google.com/store/apps/details?id=$playPkg".toUri()).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (webIntent.resolveActivity(pm) != null) {
          ctx.startActivity(webIntent)
        } else {
          logError(IllegalStateException("No activity found to open Play Store or web for $playPkg"))
          showToast(R.string.no_update_found, Toast.LENGTH_SHORT)
        }
      }
      return@setOnPreferenceClickListener true
    }
  }
}
