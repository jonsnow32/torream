package cloud.streamless.torream.ui.settings

import android.os.Bundle
import android.text.format.Formatter.formatShortFileSize
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import cloud.streamless.torream.R
import cloud.streamless.torream.ui.dialog.SelectionDialog
import cloud.streamless.torream.ui.player.mpv.MPVUtils
import cloud.streamless.torream.ui.player.mpv.MPVView
import cloud.streamless.torream.ui.settings.SettingsFragment.Companion.getFolderSize
import cloud.streamless.torream.ui.settings.SettingsFragment.Companion.getPref
import cloud.streamless.torream.ui.settings.SettingsFragment.Companion.setToolBarScrollFlags
import cloud.streamless.torream.ui.settings.SettingsFragment.Companion.setUpToolbar
import cloud.streamless.torream.ui.subtitles.ChromecastSubtitlesFragment
import cloud.streamless.torream.ui.subtitles.MPVSubtitleFragment
import cloud.streamless.torream.utils.CommonActivitty.hideKeyboard
import cloud.streamless.torream.utils.Utils.logError

class SettingsPlayer : PreferenceFragmentCompat() {

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
    setUpToolbar(R.string.category_player)
    setToolBarScrollFlags()
  }

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    activity?.hideKeyboard()
    setPreferencesFromResource(R.xml.settings_player, rootKey)
    val settingsManager = PreferenceManager.getDefaultSharedPreferences(requireContext())

    getPref(R.string.video_buffer_length_key)?.setOnPreferenceClickListener {
      val prefNames = resources.getStringArray(R.array.video_buffer_length_names)
      val prefValues = resources.getIntArray(R.array.video_buffer_length_values)

      val currentPrefSize =
        settingsManager.getInt(getString(R.string.video_buffer_length_key), 0)

      SelectionDialog.single(
        prefNames.toList(),
        prefValues.indexOf(currentPrefSize),
        getString(R.string.video_buffer_length_settings),
        true
      ).show(parentFragmentManager) { bundle ->
        bundle?.apply {
          getIntegerArrayList(SelectionDialog.ITEMS_SELECTED)?.get(0)?.let { index ->
            settingsManager.edit {
              putInt(getString(R.string.video_buffer_length_key), prefValues[index])
            }
          }
        }

      }
      return@setOnPreferenceClickListener true
    }

    getPref(R.string.prefer_limit_title_key)?.setOnPreferenceClickListener {
      val prefNames = resources.getStringArray(R.array.limit_title_pref_names)
      val prefValues = resources.getIntArray(R.array.limit_title_pref_values)
      val current = settingsManager.getInt(getString(R.string.prefer_limit_title_key), 0)

      SelectionDialog.single(
        prefNames.toList(),
        prefValues.indexOf(current),
        getString(R.string.limit_title),
        true
      ).show(parentFragmentManager) { bundle ->
        bundle?.apply {
          getIntegerArrayList(SelectionDialog.ITEMS_SELECTED)?.get(0)?.let { index ->
            settingsManager.edit {
              putInt(getString(R.string.prefer_limit_title_key), prefValues[index])
            }
          }
        }
      }
      return@setOnPreferenceClickListener true
    }

    /*(getPref(R.string.double_tap_seek_time_key) as? SeekBarPreference?)?.let {

    }*/

    getPref(R.string.prefer_limit_title_rez_key)?.setOnPreferenceClickListener {
      val prefNames = resources.getStringArray(R.array.limit_title_rez_pref_names)
      val prefValues = resources.getIntArray(R.array.limit_title_rez_pref_values)
      val current = settingsManager.getInt(getString(R.string.prefer_limit_title_rez_key), 3)

      SelectionDialog.single(
        prefNames.toList(),
        prefValues.indexOf(current),
        getString(R.string.limit_title_rez),
        true
      ).show(parentFragmentManager) { bundle ->
          bundle?.apply {
            getIntegerArrayList(SelectionDialog.ITEMS_SELECTED)?.get(0)?.let { index ->
              settingsManager.edit {
                putInt(getString(R.string.prefer_limit_title_rez_key), prefValues[index])
              }
            }
          }
        }
      return@setOnPreferenceClickListener true
    }

    getPref(R.string.player_pref_key)?.setOnPreferenceClickListener {
      val prefNames = resources.getStringArray(R.array.player_pref_names)
      val prefValues = resources.getIntArray(R.array.player_pref_values)
      val current = settingsManager.getInt(getString(R.string.player_pref_key), 1)

      SelectionDialog.single(
        prefNames.toList(),
        prefValues.indexOf(current),
        getString(R.string.player_pref),
        true).show(parentFragmentManager) { bundle ->
        bundle?.apply {
          getIntegerArrayList(SelectionDialog.ITEMS_SELECTED)?.get(0)?.let { index ->
            settingsManager.edit().putInt(getString(R.string.player_pref_key), prefValues[index])
              .apply()
          }
        }
      }
      return@setOnPreferenceClickListener true
    }

    getPref(R.string.subtitle_settings_key)?.setOnPreferenceClickListener {
      MPVSubtitleFragment.push(activity, false)
      return@setOnPreferenceClickListener true
    }

    getPref(R.string.subtitle_settings_chromecast_key)?.setOnPreferenceClickListener {
      ChromecastSubtitlesFragment.push(activity, false)
      return@setOnPreferenceClickListener true
    }

    getPref(R.string.video_buffer_disk_key)?.setOnPreferenceClickListener {
      val prefNames = resources.getStringArray(R.array.video_buffer_size_names)
      val prefValues = resources.getIntArray(R.array.video_buffer_size_values)

      val currentPrefSize =
        settingsManager.getInt(getString(R.string.video_buffer_disk_key), 0)

      SelectionDialog.single(
        prefNames.toList(),
        prefValues.indexOf(currentPrefSize),
        getString(R.string.video_buffer_disk_settings),
        true).show(parentFragmentManager) { bundle ->
        bundle?.apply {
          getIntegerArrayList(SelectionDialog.ITEMS_SELECTED)?.get(0)?.let { index ->
            settingsManager.edit {
              putInt(getString(R.string.video_buffer_disk_key), prefValues[index])
            }
          }
        }
      }
      return@setOnPreferenceClickListener true
    }
    getPref(R.string.video_buffer_size_key)?.setOnPreferenceClickListener {
      val prefNames = resources.getStringArray(R.array.video_buffer_size_names)
      val prefValues = resources.getIntArray(R.array.video_buffer_size_values)

      val currentPrefSize =
        settingsManager.getInt(getString(R.string.video_buffer_size_key), 0)

      SelectionDialog.single(
        prefNames.toList(),
        prefValues.indexOf(currentPrefSize),
        getString(R.string.video_buffer_size_settings),
        true).show(parentFragmentManager) { bundle ->
        bundle?.apply {
          getIntegerArrayList(SelectionDialog.ITEMS_SELECTED)?.get(0)?.let { index ->
            settingsManager.edit {
              putInt(getString(R.string.video_buffer_size_key), prefValues[index])
            }
          }
        }
      }
      return@setOnPreferenceClickListener true
    }

    getPref(R.string.mpv_conf_editor_key)?.setOnPreferenceClickListener {
      showMpvConfEditor()
      true
    }

    getPref(R.string.video_buffer_clear_key)?.let { pref ->
      val cacheDir = context?.cacheDir ?: return@let

      fun updateSummery() {
        try {
          pref.summary = formatShortFileSize(view?.context, getFolderSize(cacheDir))
        } catch (e: Exception) {
          logError(e)
        }
      }

      updateSummery()

      pref.setOnPreferenceClickListener {
        try {
          cacheDir.deleteRecursively()
          updateSummery()
        } catch (e: Exception) {
          logError(e)
        }
        return@setOnPreferenceClickListener true
      }
    }

  }

  private fun showMpvConfEditor() {
    val ctx = requireContext()
    val confFile = MPVView.getMpvConfFile(ctx) ?: run {
      Toast.makeText(ctx, "External storage not available", Toast.LENGTH_SHORT).show()
      return
    }
    val currentContent = if (confFile.exists()) confFile.readText() else MPVView.DEFAULT_MPV_CONF

    val dp8 = MPVUtils.convertDp(ctx, 8f)

    val pathView = TextView(ctx).apply {
      text = confFile.absolutePath
      setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f)
      setTextColor(0xFF888888.toInt())
      setPadding(dp8, dp8, dp8, MPVUtils.convertDp(ctx, 4f))
    }

    val editText = EditText(ctx).apply {
      setText(currentContent)
      typeface = android.graphics.Typeface.MONOSPACE
      setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
      gravity = Gravity.TOP or Gravity.START
      inputType = android.text.InputType.TYPE_CLASS_TEXT or
        android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
        android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
      isSingleLine = false
      maxLines = 999
      isVerticalScrollBarEnabled = true
      setHorizontallyScrolling(false)
      setLines(20)
      setPadding(dp8, dp8, dp8, dp8)
    }

    val layout = LinearLayout(ctx).apply {
      orientation = LinearLayout.VERTICAL
      addView(pathView)
      addView(editText)
    }

    val dialog = AlertDialog.Builder(ctx, R.style.BaseMaterialDialogTheme)
      .setTitle(R.string.mpv_conf_editor)
      .setView(layout)
      .setPositiveButton(android.R.string.ok) { _, _ ->
        runCatching {
          confFile.parentFile?.mkdirs()
          confFile.writeText(editText.text.toString())
        }.onSuccess {
          Toast.makeText(ctx, R.string.mpv_conf_saved, Toast.LENGTH_SHORT).show()
        }.onFailure {
          Toast.makeText(ctx, "Failed to save: ${it.message}", Toast.LENGTH_LONG).show()
        }
      }
      .setNeutralButton(R.string.mpv_conf_reset, null)
      .setNegativeButton(android.R.string.cancel, null)
      .show()

    // Override neutral button so it doesn't auto-dismiss the dialog
    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
      editText.setText(MPVView.DEFAULT_MPV_CONF)
      editText.setSelection(0)
    }
  }
}
