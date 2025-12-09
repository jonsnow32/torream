package cloud.app.csplayer.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import cloud.app.csplayer.R
import cloud.app.csplayer.ui.dialog.SelectionDialog
import cloud.app.csplayer.ui.settings.SettingsFragment.Companion.getPref
import cloud.app.csplayer.ui.settings.SettingsFragment.Companion.setToolBarScrollFlags
import cloud.app.csplayer.ui.settings.SettingsFragment.Companion.setUpToolbar
import cloud.app.csplayer.utils.AppUtils.getDownloadPath
import cloud.app.csplayer.utils.CommonActivitty.hideKeyboard
import cloud.app.csplayer.utils.LayoutMode
import cloud.app.csplayer.utils.UnifiedFileFactory
import cloud.app.csplayer.utils.isLayout

class SettingsDownload : PreferenceFragmentCompat() {

  // Open file picker for download path
  private val pathPicker =
    registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
      // It lies, it can be null if file manager quits.
      if (uri == null) return@registerForActivityResult
      val context = context ?: return@registerForActivityResult
      // RW perms for the path
      val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
        Intent.FLAG_GRANT_WRITE_URI_PERMISSION

      context.contentResolver.takePersistableUriPermission(uri, flags)

      val file = UnifiedFileFactory.fromUri(context, uri) ?: run {
        Toast.makeText(context, "Please select a valid directory", Toast.LENGTH_LONG).show()
        return@registerForActivityResult
      }

      if (!file.isDirectory) {
        Toast.makeText(context, "Please select a valid directory", Toast.LENGTH_LONG).show()
        return@registerForActivityResult
      }
      // Stores the real URI using download_path_key
      // Important that the URI is stored instead of filepath due to permissions.
      PreferenceManager.getDefaultSharedPreferences(context)
        .edit(true) { putString(getString(R.string.download_path_key), file.uri.toString()) }

      getPref(R.string.download_path_key)?.apply {
        val downloadPathUri = context.getDownloadPath()
        summary = downloadPathUri?.filePath ?: downloadPathUri?.uri?.toString() ?: "Unknown"
      }
      Toast.makeText(context, "Download path updated to ${UnifiedFileFactory.fromUri(context, uri)?.name}", Toast.LENGTH_SHORT).show()
    }

  // Battery optimization request launcher
  private val batteryOptimizationLauncher =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
      // Check if battery optimization was disabled after returning
      val context = context ?: return@registerForActivityResult
      if (!isAppRestricted(context)) {
        Toast.makeText(context, "Battery optimization disabled successfully", Toast.LENGTH_SHORT).show()
      }
    }

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
    getPref(R.string.download_path_key)?.apply {
      // Set initial summary to show current download path
      val downloadPathUri = context.getDownloadPath()
      summary = downloadPathUri?.filePath

      setOnPreferenceClickListener {
        try {
          val downloadPath = context.getDownloadPath()?.uri?.path
          val path = settingsManager.getString(getString(R.string.download_path_key), downloadPath)
          pathPicker.launch(path?.toUri())
        } catch (e: Exception) {
          Toast.makeText(context, "Failed to open file picker: ${e.message}", Toast.LENGTH_LONG).show()
        }
        true
      }
    }

    // Open download folder in file manager
    getPref(R.string.open_download_folder_key)?.setOnPreferenceClickListener {
      openDownloadFolder()
      true
    }

    // Battery optimization
    getPref(R.string.battery_optimisation_key)?.apply {
      isEnabled = context.isLayout(LayoutMode.Phone.id)
      setOnPreferenceClickListener {
        showBatteryOptimizationDialog(context)
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

    // WiFi-only download
    getPref(R.string.download_over_wifi_key)?.setOnPreferenceChangeListener { _, newValue ->
      val isWifiOnly = newValue as Boolean
      if (isWifiOnly) {
        Toast.makeText(context, "Downloads limited to WiFi networks only", Toast.LENGTH_SHORT).show()
      } else {
        Toast.makeText(context, "Downloads allowed on any network", Toast.LENGTH_SHORT).show()
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
      showTorrentPortDialog(settingsManager)
      true
    }
  }

  /**
   * Check if app is restricted by battery optimization
   */
  private fun isAppRestricted(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
      powerManager?.isIgnoringBatteryOptimizations(context.packageName) == false
    } else {
      false
    }
  }

  /**
   * Show battery optimization dialog and guide user to settings
   */
  private fun showBatteryOptimizationDialog(context: Context) {
    if (!isAppRestricted(context)) {
      Toast.makeText(context, R.string.app_unrestricted_toast, Toast.LENGTH_SHORT).show()
      return
    }

    AlertDialog.Builder(context, R.style.BaseMaterialDialogTheme)
      .setTitle(R.string.battery_dialog_title)
      .setMessage(R.string.battery_dialog_message)
      .setPositiveButton("Open Settings") { _, _ ->
        try {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // First try the direct request action
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:${context.packageName}")

            // Verify the intent can be handled
            if (intent.resolveActivity(context.packageManager) != null) {
              batteryOptimizationLauncher.launch(intent)
            } else {
              // Fallback to general battery optimization list
              openBatteryOptimizationList(context)
            }
          } else {
            Toast.makeText(context, "Battery optimization not available on this Android version", Toast.LENGTH_SHORT).show()
          }
        } catch (_: Exception) {
          // Fallback to general battery optimization settings
          openBatteryOptimizationList(context)
        }
      }
      .setNegativeButton("Cancel", null)
      .show()
  }

  /**
   * Open the battery optimization settings list as fallback
   */
  private fun openBatteryOptimizationList(context: Context) {
    try {
      val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
      batteryOptimizationLauncher.launch(intent)
      Toast.makeText(context, "Find and select ${context.getString(R.string.app_name)} from the list", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
      // Last resort: open app settings
      try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.parse("package:${context.packageName}")
        batteryOptimizationLauncher.launch(intent)
        Toast.makeText(context, "Please disable battery optimization in app settings", Toast.LENGTH_LONG).show()
      } catch (e2: Exception) {
        Toast.makeText(context, "Failed to open settings: ${e2.message}", Toast.LENGTH_LONG).show()
      }
    }
  }

  /**
   * Show dialog to input custom torrent port
   */
  private fun showTorrentPortDialog(settingsManager: android.content.SharedPreferences) {
    val context = context ?: return
    val currentPort = settingsManager.getInt(getString(R.string.download_torrent_port_key), 6881)

    val editText = EditText(context).apply {
      inputType = android.text.InputType.TYPE_CLASS_NUMBER
      setText(currentPort.toString())
      hint = "Enter port (1024-65535)"
      setPadding(64, 32, 64, 32)
    }

    AlertDialog.Builder(context, R.style.BaseMaterialDialogTheme)
      .setTitle(R.string.download_torrent_port)
      .setMessage("Default port: 6881\nValid range: 1024-65535")
      .setView(editText)
      .setPositiveButton("Save") { _, _ ->
        val portText = editText.text.toString()
        try {
          val port = portText.toIntOrNull()
          if (port != null && port in 1024..65535) {
            settingsManager.edit { putInt(getString(R.string.download_torrent_port_key), port) }
            Toast.makeText(context, "Port updated to $port", Toast.LENGTH_SHORT).show()
          } else {
            Toast.makeText(context, "Invalid port. Please enter a number between 1024-65535", Toast.LENGTH_LONG).show()
          }
        } catch (e: Exception) {
          Toast.makeText(context, "Invalid input: ${e.message}", Toast.LENGTH_LONG).show()
        }
      }
      .setNegativeButton("Cancel", null)
      .setNeutralButton("Reset to Default") { _, _ ->
        settingsManager.edit { putInt(getString(R.string.download_torrent_port_key), 6881) }
        Toast.makeText(context, "Port reset to 6881", Toast.LENGTH_SHORT).show()
      }
      .show()
  }

  /**
   * Open the download folder in file manager
   */
  private fun openDownloadFolder() {
    val context = context ?: return

    try {
      val downloadPath = context.getDownloadPath()

      if (downloadPath == null) {
        Toast.makeText(context, "Download path not configured", Toast.LENGTH_SHORT).show()
        return
      }

      // Try to open the folder with ACTION_VIEW
      val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(downloadPath.uri, "resource/folder")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }

      // Check if any app can handle this intent
      if (intent.resolveActivity(context.packageManager) != null) {
        try {
          startActivity(intent)
          return
        } catch (e: Exception) {
          // Fall through to alternative methods
        }
      }

      // Alternative: Try using DocumentsContract for SAF URIs
      if (downloadPath.uri.scheme == "content") {
        try {
          val docIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(downloadPath.uri, "vnd.android.document/directory")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
          startActivity(docIntent)
          return
        } catch (e: Exception) {
          // Fall through
        }
      }

      // Alternative: Try opening file manager with generic intent
      try {
        val fileManagerIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
          type = "*/*"
          addCategory(Intent.CATEGORY_OPENABLE)
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (fileManagerIntent.resolveActivity(context.packageManager) != null) {
          startActivity(Intent.createChooser(fileManagerIntent, "Open File Manager"))
          Toast.makeText(
            context,
            "Navigate to: ${downloadPath.filePath ?: downloadPath.uri.path}",
            Toast.LENGTH_LONG
          ).show()
          return
        }
      } catch (e: Exception) {
        // Fall through
      }

      // Last resort: Show the path and suggest manual navigation
      AlertDialog.Builder(context, R.style.BaseMaterialDialogTheme)
        .setTitle("Download Folder Location")
        .setMessage(
          "Your downloads are saved at:\n\n" +
          "${downloadPath.filePath ?: downloadPath.uri.path}\n\n" +
          "Please open your file manager and navigate to this location."
        )
        .setPositiveButton("Copy Path") { _, _ ->
          val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
          val clip = android.content.ClipData.newPlainText(
            "Download Path",
            downloadPath.filePath ?: downloadPath.uri.path
          )
          clipboard?.setPrimaryClip(clip)
          Toast.makeText(context, "Path copied to clipboard", Toast.LENGTH_SHORT).show()
        }
        .setNegativeButton("OK", null)
        .show()

    } catch (e: Exception) {
      Toast.makeText(context, "Failed to open download folder: ${e.message}", Toast.LENGTH_LONG).show()
    }
  }

  companion object {
    const val DEFAULT_TORRENT_PORT = 6881
  }
}
