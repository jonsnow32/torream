package cloud.app.csplayer.ui.settings

import android.os.Bundle
import android.text.format.Formatter.formatShortFileSize
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.edit
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import cloud.app.csplayer.R
import cloud.app.csplayer.ui.dialog.SelectionDialog
import cloud.app.csplayer.ui.settings.SettingsFragment.Companion.getFolderSize
import cloud.app.csplayer.ui.settings.SettingsFragment.Companion.getPref
import cloud.app.csplayer.ui.settings.SettingsFragment.Companion.setToolBarScrollFlags
import cloud.app.csplayer.ui.settings.SettingsFragment.Companion.setUpToolbar
import cloud.app.csplayer.ui.subtitles.ChromecastSubtitlesFragment
import cloud.app.csplayer.ui.subtitles.MPVSubtitleFragment
import cloud.app.csplayer.utils.CommonActivitty.hideKeyboard
import cloud.app.csplayer.utils.Utils.logError

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
}
