package cloud.app.csplayer.ui.settings

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import cloud.app.csplayer.R
import cloud.app.csplayer.databinding.LogcatBinding
import cloud.app.csplayer.ui.settings.SettingsFragment.Companion.getPref
import cloud.app.csplayer.ui.settings.SettingsFragment.Companion.setPaddingBottom
import cloud.app.csplayer.ui.settings.SettingsFragment.Companion.setToolBarScrollFlags
import cloud.app.csplayer.ui.settings.SettingsFragment.Companion.setUpToolbar
import cloud.app.csplayer.utils.CommonActivitty.hideKeyboard
import cloud.app.csplayer.utils.Coroutines.ioSafe
import cloud.app.csplayer.utils.SingleSelectionHelper.showBottomDialog
import cloud.app.csplayer.utils.UIHelper.clipboardHelper
import cloud.app.csplayer.utils.UIHelper.dismissSafe
import cloud.app.csplayer.utils.Utils.logError
import cloud.app.csplayer.utils.Utils.showToast
import cloud.app.csplayer.utils.txt
import cloud.app.csplayer.utils.InAppUpdater.Companion.runAutoUpdate
import java.io.BufferedReader
import java.io.InputStreamReader

class SettingsUpdates : PreferenceFragmentCompat() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpToolbar(R.string.category_updates)
        setPaddingBottom()
        setToolBarScrollFlags()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        activity?.hideKeyboard()
        setPreferencesFromResource(R.xml.settings_updates, rootKey)
        //val settingsManager = PreferenceManager.getDefaultSharedPreferences(requireContext())



        getPref(R.string.show_logcat_key)?.setOnPreferenceClickListener { pref ->
            val builder =
                AlertDialog.Builder(pref.context, R.style.AlertDialogCustom)

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

            activity?.showBottomDialog(
                prefNames.toList(),
                prefValues.indexOf(currentInstaller),
                getString(R.string.apk_installer_settings),
                true,
                {}) {
                try {
                    settingsManager.edit()
                        .putInt(getString(R.string.apk_installer_key), prefValues[it])
                        .apply()
                } catch (e: Exception) {
                    logError(e)
                }
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.manual_check_update_key)?.setOnPreferenceClickListener {
            ioSafe {
                if (activity?.runAutoUpdate(false) == false) {
                    activity?.runOnUiThread {
                        showToast(
                            R.string.no_update_found,
                            Toast.LENGTH_SHORT
                        )
                    }
                }
            }
            return@setOnPreferenceClickListener true
        }
    }
}
