package com.tv.apps.zippy.ui.settings

import android.os.Bundle
import android.text.format.Formatter.formatShortFileSize
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.edit
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.tv.apps.zippy.R
import com.tv.apps.zippy.ui.dialog.SelectionDialog
import com.tv.apps.zippy.ui.settings.SettingsFragment.Companion.getFolderSize
import com.tv.apps.zippy.ui.settings.SettingsFragment.Companion.getPref
import com.tv.apps.zippy.ui.settings.SettingsFragment.Companion.setToolBarScrollFlags
import com.tv.apps.zippy.ui.settings.SettingsFragment.Companion.setUpToolbar
import com.tv.apps.zippy.ui.subtitles.ChromecastSubtitlesFragment
import com.tv.apps.zippy.ui.subtitles.MPVSubtitleFragment
import com.tv.apps.zippy.utils.CommonActivitty.hideKeyboard
import com.tv.apps.zippy.utils.Utils.logError

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
