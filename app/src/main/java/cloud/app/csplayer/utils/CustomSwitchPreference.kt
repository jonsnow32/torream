package cloud.app.csplayer.utils

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.Switch
import androidx.preference.PreferenceViewHolder
import androidx.preference.SwitchPreferenceCompat
import cloud.app.csplayer.R

class CustomSwitchPreference @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : SwitchPreferenceCompat(context, attrs, defStyleAttr) {

  init {
    layoutResource = R.layout.custom_switch_preference
  }

  override fun onBindViewHolder(holder: PreferenceViewHolder) {
    super.onBindViewHolder(holder)

    val switchView = holder.findViewById(R.id.switch_widget) as Switch
    switchView.isChecked = isChecked
    switchView.setOnCheckedChangeListener { _, isChecked ->
      if (callChangeListener(isChecked)) {
        this.isChecked = isChecked
      }
    }

    holder.itemView.setOnClickListener {
      val newValue = !switchView.isChecked
      if (callChangeListener(newValue)) {
        this.isChecked = newValue
        switchView.isChecked = newValue
      }
    }

    val view = holder.itemView
    view.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
      if (hasFocus) {
        v.setBackgroundResource(R.drawable.preference_focused)
      } else {
        v.setBackgroundResource(0)
      }
    }
  }
}
