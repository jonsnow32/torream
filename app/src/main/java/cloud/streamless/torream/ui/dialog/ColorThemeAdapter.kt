package cloud.streamless.torream.ui.dialog

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import cloud.streamless.torream.R
import cloud.streamless.torream.utils.AppColorTheme

class ColorThemeAdapter(
  context: Context,
  private val themes: List<AppColorTheme>,
  private val selectedPrefValue: String?
) : ArrayAdapter<AppColorTheme>(context, R.layout.item_color_theme_choice, themes) {

  override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
    val view = convertView
      ?: LayoutInflater.from(context).inflate(R.layout.item_color_theme_choice, parent, false)
    val theme = themes[position]

    val swatch1 = view.findViewById<View>(R.id.swatch1)
    val swatch2 = view.findViewById<View>(R.id.swatch2)
    val swatch3 = view.findViewById<View>(R.id.swatch3)
    val dynamicIcon = view.findViewById<ImageView>(R.id.dynamicSwatchIcon)
    val label = view.findViewById<TextView>(android.R.id.text1)
    val checkmark = view.findViewById<ImageView>(R.id.checkmark)

    val swatchColors = theme.swatchColors(context)
    val hasSwatches = swatchColors.size >= 3
    swatch1.isVisible = hasSwatches
    swatch2.isVisible = hasSwatches
    swatch3.isVisible = hasSwatches
    dynamicIcon.isVisible = !hasSwatches
    if (hasSwatches) {
      swatch1.backgroundTintList = ColorStateList.valueOf(swatchColors[0])
      swatch2.backgroundTintList = ColorStateList.valueOf(swatchColors[1])
      swatch3.backgroundTintList = ColorStateList.valueOf(swatchColors[2])
    }

    label.text = context.getString(theme.displayNameRes)
    checkmark.isVisible = theme.prefValue == selectedPrefValue

    return view
  }
}
