package cloud.streamless.torream.utils

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import cloud.streamless.torream.R
import com.google.android.material.color.DynamicColors

enum class AppColorTheme(
  val prefValue: String,
  @StringRes val displayNameRes: Int,
  @StyleRes val styleRes: Int,
  val isDynamic: Boolean = false,
  val swatchColorRes: IntArray = intArrayOf()
) {
  DEFAULT(
    "default", R.string.color_theme_default, R.style.AppTheme,
    swatchColorRes = intArrayOf(R.color.default_swatch_background, R.color.default_swatch_primary, R.color.default_swatch_secondary)
  ),
  AMOLED_BLACK(
    "amoled_black", R.string.color_theme_amoled_black, R.style.Theme_AmoledBlack,
    swatchColorRes = intArrayOf(R.color.amoled_swatch_background, R.color.amoled_swatch_primary, R.color.amoled_swatch_secondary)
  ),
  CATPPUCCIN(
    "catppuccin", R.string.color_theme_catppuccin, R.style.Theme_Catppuccin,
    swatchColorRes = intArrayOf(R.color.catppuccin_swatch_background, R.color.catppuccin_swatch_primary, R.color.catppuccin_swatch_secondary)
  ),
  NORD(
    "nord", R.string.color_theme_nord, R.style.Theme_Nord,
    swatchColorRes = intArrayOf(R.color.nord_swatch_background, R.color.nord_swatch_primary, R.color.nord_swatch_secondary)
  ),
  DRACULA(
    "dracula", R.string.color_theme_dracula, R.style.Theme_Dracula,
    swatchColorRes = intArrayOf(R.color.dracula_swatch_background, R.color.dracula_swatch_primary, R.color.dracula_swatch_secondary)
  ),
  TOKYO_NIGHT(
    "tokyo_night", R.string.color_theme_tokyo_night, R.style.Theme_TokyoNight,
    swatchColorRes = intArrayOf(R.color.tokyo_night_swatch_background, R.color.tokyo_night_swatch_primary, R.color.tokyo_night_swatch_secondary)
  ),
  GRUVBOX(
    "gruvbox", R.string.color_theme_gruvbox, R.style.Theme_Gruvbox,
    swatchColorRes = intArrayOf(R.color.gruvbox_swatch_background, R.color.gruvbox_swatch_primary, R.color.gruvbox_swatch_secondary)
  ),
  SOLARIZED(
    "solarized", R.string.color_theme_solarized, R.style.Theme_Solarized,
    swatchColorRes = intArrayOf(R.color.solarized_swatch_background, R.color.solarized_swatch_primary, R.color.solarized_swatch_secondary)
  ),
  MATERIAL_YOU(
    "material_you", R.string.color_theme_material_you, R.style.AppTheme, isDynamic = true
  );

  val isAvailable: Boolean
    get() = !isDynamic || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

  fun swatchColors(context: Context): List<Int> =
    swatchColorRes.map { ContextCompat.getColor(context, it) }

  companion object {
    fun fromPref(value: String?): AppColorTheme =
      entries.firstOrNull { it.prefValue == value } ?: DEFAULT
  }
}

fun Context.getAppColorTheme(): AppColorTheme {
  val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
  return AppColorTheme.fromPref(settingsManager.getString(getString(R.string.app_color_theme_key), null))
}

fun Activity.applyAppColorTheme(theme: AppColorTheme = this.getAppColorTheme()) {
  setTheme(theme.styleRes)
  if (theme.isDynamic) {
    DynamicColors.applyToActivityIfAvailable(this)
  }
}
