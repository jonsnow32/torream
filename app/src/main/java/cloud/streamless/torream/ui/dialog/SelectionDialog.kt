package cloud.streamless.torream.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import androidx.core.view.doOnLayout
import androidx.core.view.isGone
import androidx.core.view.isVisible
import cloud.streamless.torream.R
import cloud.streamless.torream.databinding.DialogSelectionBinding
import cloud.streamless.torream.utils.AutoClearedValue.Companion.autoCleared
import cloud.streamless.torream.utils.UIHelper.dismissSafe
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SelectionDialog : DockingDialog() {
  private var binding by autoCleared<DialogSelectionBinding>()
  private val args by lazy { requireArguments() }
  private val items by lazy { args.getStringArrayList(ARG_ITEMS) ?: emptyList() }
  private val selectedIndex by lazy { args.getIntegerArrayList(ARG_SELECTED_INDEX) ?: emptyList() }
  private val name by lazy { args.getString(ARG_NAME, "") }
  private val showApply by lazy { args.getBoolean(ARG_SHOW_APPLY, false) }
  private val isMultiSelect by lazy { args.getBoolean(ARG_IS_MULTI_SELECT, false) }
  private val itemLayout by lazy { args.getInt(ARG_ITEM_LAYOUT, R.layout.sort_bottom_single_choice) }
  private val footerItems by lazy { args.getStringArrayList(ARG_FOOTER_ITEMS) ?: emptyList() }
  private val footerSelectedIndex by lazy { args.getInt(ARG_FOOTER_SELECTED_INDEX, -1) }

  companion object {
    private const val ARG_ITEMS = "ARG_ITEMS"
    private const val ARG_SELECTED_INDEX = "ARG_SELECTED_INDEX"
    private const val ARG_NAME = "ARG_NAME"
    private const val ARG_SHOW_APPLY = "ARG_SHOW_APPLY"
    private const val ARG_IS_MULTI_SELECT = "ARG_IS_MULTI_SELECT"
    private const val ARG_ITEM_LAYOUT = "ARG_ITEM_LAYOUT"
    private const val ARG_FOOTER_ITEMS = "ARG_FOOTER_ITEMS"
    private const val ARG_FOOTER_SELECTED_INDEX = "ARG_FOOTER_SELECTED_INDEX"

    const val ITEMS_SELECTED = "itemS_selected"
    const val FOOTER_SELECTED = "footer_selected"

    /**
     * @param footerItems optional row of horizontal buttons pinned to the bottom of the dialog,
     * for a secondary single-choice setting unrelated to [items] (e.g. audio channel layout).
     */
    fun single(
      items: List<String>,
      selectedIndex: Int,
      name: String,
      showApply: Boolean,
      footerItems: List<String> = emptyList(),
      footerSelectedIndex: Int = -1
    ): SelectionDialog {
      return newInstance(
        items,
        listOf(selectedIndex),
        name,
        showApply,
        false,
        R.layout.item_single_choice,
        footerItems,
        footerSelectedIndex
      )
    }

    fun multiple(
      items: List<String>,
      selectedIndex: List<Int>,
      name: String
    ): SelectionDialog {
      return newInstance(items, selectedIndex, name, true, true, R.layout.item_single_choice, emptyList(), -1)
    }

    private fun newInstance(
      items: List<String>,
      selectedIndex: List<Int>,
      name: String,
      showApply: Boolean,
      isMultiSelect: Boolean,
      itemLayout: Int,
      footerItems: List<String>,
      footerSelectedIndex: Int
    ) = SelectionDialog().apply {
      arguments = Bundle().apply {
        putStringArrayList(ARG_ITEMS, ArrayList(items))
        putIntegerArrayList(ARG_SELECTED_INDEX, ArrayList(selectedIndex))
        putString(ARG_NAME, name)
        putBoolean(ARG_SHOW_APPLY, showApply)
        putBoolean(ARG_IS_MULTI_SELECT, isMultiSelect)
        putInt(ARG_ITEM_LAYOUT, itemLayout)
        putStringArrayList(ARG_FOOTER_ITEMS, ArrayList(footerItems))
        putInt(ARG_FOOTER_SELECTED_INDEX, footerSelectedIndex)
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
    val context = this.context ?: return

    val realShowApply = showApply || isMultiSelect
    binding.apply {
      applyBttHolder.isVisible = realShowApply

      if (!realShowApply) {
        (listview1.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
          params.bottomMargin = 0
          listview1.layoutParams = params
        }
        (bottomArea.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
          params.topMargin = 0
          bottomArea.layoutParams = params
        }
      }

      if (footerItems.isNotEmpty()) {
        footerButtonsScroll.isVisible = true
        footerButtonsHolder.removeAllViews()
        footerItems.forEachIndexed { index, label ->
          val chip = Chip(context).apply {
            text = label
            isCheckable = true
            isChecked = index == footerSelectedIndex
          }
          chip.setOnClickListener { pendingFooterIndex = index }
          footerButtonsHolder.addView(chip)
        }
        pendingFooterIndex = footerSelectedIndex.coerceAtLeast(0)

        // Footer adds extra height below the list; re-reserve space for it once measured.
        bottomArea.doOnLayout { area ->
          (listview1.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            params.bottomMargin = area.height
            listview1.layoutParams = params
          }
          (area.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            params.topMargin = -area.height
            area.layoutParams = params
          }
        }
      }

      text1.text = name
      text1.isGone = name.isBlank()

      val arrayAdapter = ArrayAdapter(context, itemLayout, items)
      listview1.adapter = arrayAdapter
      listview1.choiceMode = if (isMultiSelect) AbsListView.CHOICE_MODE_MULTIPLE else AbsListView.CHOICE_MODE_SINGLE

      selectedIndex.forEach { index ->
        listview1.setItemChecked(index, true)
      }

      selectedIndex.minOrNull()?.let {
        listview1.setSelection(it)
      }


      listview1.setOnItemClickListener { _, _, which, _ ->
        if (realShowApply) {
          if (!isMultiSelect) listview1.setItemChecked(which, true)
        } else {
          updateSelectedItems(listOf(which))
          dialog?.dismissSafe(activity)
        }
      }

      if (realShowApply) {
        applyBtt.setOnClickListener {
          val selectedItems = (0 until listview1.count).filter {
            listview1.checkedItemPositions[it]
          }
          updateSelectedItems(selectedItems)
          committedFooterIndex = pendingFooterIndex
          dialog?.dismissSafe(activity)
        }

        cancelBtt.setOnClickListener {
          dialog?.dismissSafe(activity)
        }
      }
    }
  }

  private var selectedItems = emptyList<Int>()
  private var pendingFooterIndex = -1
  private var committedFooterIndex = -1
  private fun updateSelectedItems(items: List<Int>) {
    selectedItems = items
  }

  override fun getResultBundle(): Bundle? {
    if (selectedItems.isEmpty() && committedFooterIndex < 0) return null
    return Bundle().apply {
      if (selectedItems.isNotEmpty()) putIntegerArrayList(ITEMS_SELECTED, ArrayList(selectedItems))
      if (committedFooterIndex >= 0) putInt(FOOTER_SELECTED, committedFooterIndex)
    }
  }
}
