package com.lagradost.cloudstream3.ui.settings

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import cloud.app.csplayer.R
import cloud.app.csplayer.ui.settings.SettingsFragment.Companion.getPref
import cloud.app.csplayer.ui.settings.SettingsFragment.Companion.setPaddingBottom
import cloud.app.csplayer.ui.settings.SettingsFragment.Companion.setToolBarScrollFlags
import cloud.app.csplayer.ui.settings.SettingsFragment.Companion.setUpToolbar
import cloud.app.csplayer.utils.CommonActivitty.hideKeyboard
import cloud.app.csplayer.utils.SingleSelectionHelper.showBottomDialog
import cloud.app.csplayer.utils.Utils.logError
import cloud.app.csplayer.utils.SingleSelectionHelper.showDialog
class SettingsUI : PreferenceFragmentCompat() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpToolbar(R.string.category_ui)
        setPaddingBottom()
        setToolBarScrollFlags()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        activity?.hideKeyboard()
        setPreferencesFromResource(R.xml.settings_ui, rootKey)
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(requireContext())

        getPref(R.string.app_layout_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.app_layout)
            val prefValues = resources.getIntArray(R.array.app_layout_values)

            val currentLayout =
                settingsManager.getInt(getString(R.string.app_layout_key), -1)

            activity?.showBottomDialog(
                prefNames.toList(),
                prefValues.indexOf(currentLayout),
                getString(R.string.app_layout),
                true,
                {}) {
                try {
                    settingsManager.edit()
                        .putInt(getString(R.string.app_layout_key), prefValues[it])
                        .apply()
                    activity?.recreate()
                } catch (e: Exception) {
                    logError(e)
                }
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.app_theme_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.themes_names).toMutableList()
            val prefValues = resources.getStringArray(R.array.themes_names_values).toMutableList()

             if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) { // remove monet on android 11 and less
                val toRemove = prefValues
                    .mapIndexed { idx, s -> if (s.startsWith("Monet")) idx else null }
                    .filterNotNull()
                var offset = 0
                toRemove.forEach { idx ->
                    prefNames.removeAt(idx - offset)
                    prefValues.removeAt(idx - offset)
                    offset += 1
                }
            }

            val currentLayout =
                settingsManager.getString(getString(R.string.app_theme_key), prefValues.first())

            activity?.showBottomDialog(
                prefNames.toList(),
                prefValues.indexOf(currentLayout),
                getString(R.string.app_theme_settings),
                true,
                {}) {
                try {
                    settingsManager.edit()
                        .putString(getString(R.string.app_theme_key), prefValues[it])
                        .apply()
                    activity?.recreate()
                } catch (e: Exception) {
                    logError(e)
                }
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.primary_color_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.themes_overlay_names).toMutableList()
            val prefValues = resources.getStringArray(R.array.themes_overlay_names_values).toMutableList()

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) { // remove monet on android 11 and less
                val toRemove = prefValues
                    .mapIndexed { idx, s -> if (s.startsWith("Monet")) idx else null }
                    .filterNotNull()
                var offset = 0
                toRemove.forEach { idx ->
                    prefNames.removeAt(idx - offset)
                    prefValues.removeAt(idx - offset)
                    offset += 1
                }
            }

            val currentLayout =
                settingsManager.getString(getString(R.string.primary_color_key), prefValues.first())

            activity?.showDialog(
                prefNames.toList(),
                prefValues.indexOf(currentLayout),
                getString(R.string.primary_color_settings),
                true,
                {}) {
                try {
                    settingsManager.edit()
                        .putString(getString(R.string.primary_color_key), prefValues[it])
                        .apply()
                    activity?.recreate()
                } catch (e: Exception) {
                    logError(e)
                }
            }
            return@setOnPreferenceClickListener true
        }
    }
}
