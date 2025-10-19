package cloud.app.csplayer.ui.settings

import android.os.Bundle
import android.text.format.Formatter.formatShortFileSize
import android.view.View
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import cloud.app.csplayer.R
import cloud.app.csplayer.ui.subtitles.MPVSubtitleFragment
import cloud.app.csplayer.ui.settings.SettingsFragment.Companion.getFolderSize
import cloud.app.csplayer.ui.settings.SettingsFragment.Companion.getPref
import cloud.app.csplayer.ui.settings.SettingsFragment.Companion.setPaddingBottom
import cloud.app.csplayer.ui.settings.SettingsFragment.Companion.setToolBarScrollFlags
import cloud.app.csplayer.ui.settings.SettingsFragment.Companion.setUpToolbar
import cloud.app.csplayer.ui.subtitles.ChromecastSubtitlesFragment
import cloud.app.csplayer.utils.CommonActivitty.hideKeyboard
import cloud.app.csplayer.utils.SingleSelectionHelper.showBottomDialog
import cloud.app.csplayer.utils.SingleSelectionHelper.showDialog
import cloud.app.csplayer.utils.Utils.logError

class SettingsPlayer : PreferenceFragmentCompat() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpToolbar(R.string.category_player)
        setPaddingBottom()
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

            activity?.showDialog(
                prefNames.toList(),
                prefValues.indexOf(currentPrefSize),
                getString(R.string.video_buffer_length_settings),
                true,
                {}) {
                settingsManager.edit()
                    .putInt(getString(R.string.video_buffer_length_key), prefValues[it])
                    .apply()
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.prefer_limit_title_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.limit_title_pref_names)
            val prefValues = resources.getIntArray(R.array.limit_title_pref_values)
            val current = settingsManager.getInt(getString(R.string.prefer_limit_title_key), 0)

            activity?.showBottomDialog(
                prefNames.toList(),
                prefValues.indexOf(current),
                getString(R.string.limit_title),
                true,
                {}) {
                settingsManager.edit()
                    .putInt(getString(R.string.prefer_limit_title_key), prefValues[it])
                    .apply()
            }
            return@setOnPreferenceClickListener true
        }

        /*(getPref(R.string.double_tap_seek_time_key) as? SeekBarPreference?)?.let {

        }*/

        getPref(R.string.prefer_limit_title_rez_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.limit_title_rez_pref_names)
            val prefValues = resources.getIntArray(R.array.limit_title_rez_pref_values)
            val current = settingsManager.getInt(getString(R.string.prefer_limit_title_rez_key), 3)

            activity?.showBottomDialog(
                prefNames.toList(),
                prefValues.indexOf(current),
                getString(R.string.limit_title_rez),
                true,
                {}) {
                settingsManager.edit()
                    .putInt(getString(R.string.prefer_limit_title_rez_key), prefValues[it])
                    .apply()
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.player_pref_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.player_pref_names)
            val prefValues = resources.getIntArray(R.array.player_pref_values)
            val current = settingsManager.getInt(getString(R.string.player_pref_key), 1)

            activity?.showBottomDialog(
                prefNames.toList(),
                prefValues.indexOf(current),
                getString(R.string.player_pref),
                true,
                {}) {
                settingsManager.edit().putInt(getString(R.string.player_pref_key), prefValues[it]).apply()
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

            activity?.showDialog(
                prefNames.toList(),
                prefValues.indexOf(currentPrefSize),
                getString(R.string.video_buffer_disk_settings),
                true,
                {}) {
                settingsManager.edit()
                    .putInt(getString(R.string.video_buffer_disk_key), prefValues[it])
                    .apply()
            }
            return@setOnPreferenceClickListener true
        }
        getPref(R.string.video_buffer_size_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.video_buffer_size_names)
            val prefValues = resources.getIntArray(R.array.video_buffer_size_values)

            val currentPrefSize =
                settingsManager.getInt(getString(R.string.video_buffer_size_key), 0)

            activity?.showDialog(
                prefNames.toList(),
                prefValues.indexOf(currentPrefSize),
                getString(R.string.video_buffer_size_settings),
                true,
                {}) {
                settingsManager.edit()
                    .putInt(getString(R.string.video_buffer_size_key), prefValues[it])
                    .apply()
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
