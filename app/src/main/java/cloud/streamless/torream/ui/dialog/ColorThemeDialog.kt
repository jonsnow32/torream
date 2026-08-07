package cloud.streamless.torream.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.LinearLayout
import androidx.core.view.isVisible
import cloud.streamless.torream.databinding.DialogSelectionBinding
import cloud.streamless.torream.utils.AppColorTheme
import cloud.streamless.torream.utils.AutoClearedValue.Companion.autoCleared
import cloud.streamless.torream.utils.UIHelper.dismissSafe
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ColorThemeDialog : DockingDialog() {
  private var binding by autoCleared<DialogSelectionBinding>()
  private val args by lazy { requireArguments() }
  private val themes by lazy {
    (args.getStringArrayList(ARG_THEMES) ?: emptyList()).map { AppColorTheme.fromPref(it) }
  }
  private val name by lazy { args.getString(ARG_NAME, "") }
  private val selectedPrefValue by lazy { args.getString(ARG_SELECTED) }

  companion object {
    private const val ARG_THEMES = "ARG_THEMES"
    private const val ARG_NAME = "ARG_NAME"
    private const val ARG_SELECTED = "ARG_SELECTED"

    const val SELECTED_THEME = "selected_theme"

    fun newInstance(themes: List<AppColorTheme>, selected: AppColorTheme, name: String): ColorThemeDialog {
      return ColorThemeDialog().apply {
        arguments = Bundle().apply {
          putStringArrayList(ARG_THEMES, ArrayList(themes.map { it.prefValue }))
          putString(ARG_NAME, name)
          putString(ARG_SELECTED, selected.prefValue)
        }
      }
    }
  }

  override fun onCreateView(
    inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
  ): View {
    binding = DialogSelectionBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    binding.apply {
      applyBttHolder.isVisible = false
      (listview1.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
        params.bottomMargin = 0
        listview1.layoutParams = params
      }

      text1.text = name

      val adapter = ColorThemeAdapter(root.context, themes, selectedPrefValue)
      listview1.adapter = adapter
      listview1.choiceMode = AbsListView.CHOICE_MODE_NONE

      listview1.setOnItemClickListener { _, _, which, _ ->
        selectedResult = themes.getOrNull(which)
        dialog?.dismissSafe(activity)
      }
    }
  }

  private var selectedResult: AppColorTheme? = null

  override fun getResultBundle(): Bundle? {
    return selectedResult?.let {
      Bundle().apply { putString(SELECTED_THEME, it.prefValue) }
    }
  }
}
