package cloud.app.csplayer.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.edit
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import cloud.app.csplayer.R
import cloud.app.csplayer.ui.dialog.SelectionDialog
import cloud.app.csplayer.ui.settings.SettingsFragment.Companion.getPref
import cloud.app.csplayer.ui.settings.SettingsFragment.Companion.setToolBarScrollFlags
import cloud.app.csplayer.ui.settings.SettingsFragment.Companion.setUpToolbar
import cloud.app.csplayer.utils.CommonActivitty.hideKeyboard
import cloud.app.csplayer.utils.LayoutMode
import cloud.app.csplayer.utils.isLayout

class SettingsDownload : PreferenceFragmentCompat() {

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
    setUpToolbar(R.string.download)
    setToolBarScrollFlags()
  }

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    activity?.hideKeyboard()
    setPreferencesFromResource(R.xml.settings_download, rootKey)

    val settingsManager = PreferenceManager.getDefaultSharedPreferences(requireContext())

    // Download path picker
    getPref(R.string.download_path_key)?.setOnPreferenceClickListener {
      // TODO: Implement file picker for download path
      // This would typically use ActivityResultContracts.OpenDocumentTree()
      true
    }

    // Battery optimization
    getPref(R.string.battery_optimisation_key)?.apply {
      isEnabled = context.isLayout(LayoutMode.Phone.id)
      setOnPreferenceClickListener {
        // TODO: Show battery optimization dialog
        true
      }
    }

    // Concurrent downloads limit
    getPref(R.string.download_concurrent_limit_key)?.setOnPreferenceClickListener {
      val options = arrayOf("1", "2", "3", "4", "5", "Unlimited")
      val values = arrayOf(1, 2, 3, 4, 5, -1)
      val currentValue = settingsManager.getInt(getString(R.string.download_concurrent_limit_key), 3)
      val currentIndex = values.indexOf(currentValue).takeIf { it >= 0 } ?: 2

      SelectionDialog.single(
        options.toList(),
        currentIndex,
        getString(R.string.download_concurrent_limit),
        true
      ).show(parentFragmentManager) { bundle ->
        bundle?.getIntegerArrayList(SelectionDialog.ITEMS_SELECTED)?.get(0)?.let { index ->
          settingsManager.edit { putInt(getString(R.string.download_concurrent_limit_key), values[index]) }
        }
      }
      true
    }

    // Download speed limit
    getPref(R.string.download_max_speed_key)?.setOnPreferenceClickListener {
      val options = arrayOf("Unlimited", "1 MB/s", "2 MB/s", "5 MB/s", "10 MB/s", "20 MB/s")
      val values = arrayOf(0, 1024, 2048, 5120, 10240, 20480) // in KB/s
      val currentValue = settingsManager.getInt(getString(R.string.download_max_speed_key), 0)
      val currentIndex = values.indexOf(currentValue).takeIf { it >= 0 } ?: 0

      SelectionDialog.single(
        options.toList(),
        currentIndex,
        getString(R.string.download_max_speed),
        true
      ).show(parentFragmentManager) { bundle ->
        bundle?.getIntegerArrayList(SelectionDialog.ITEMS_SELECTED)?.get(0)?.let { index ->
          settingsManager.edit { putInt(getString(R.string.download_max_speed_key), values[index]) }
        }
      }
      true
    }

    // Torrent port
    getPref(R.string.download_torrent_port_key)?.setOnPreferenceClickListener {
      // TODO: Show dialog to input custom port number
      // Default: 6881
      true
    }
  }
}
