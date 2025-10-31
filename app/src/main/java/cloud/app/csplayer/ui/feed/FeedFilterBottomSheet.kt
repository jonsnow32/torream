package cloud.app.csplayer.ui.feed

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayout
import cloud.app.csplayer.databinding.BottomSheetFeedFilterBinding

/**
 * Bottom sheet dialog for feed filtering and display options
 */
class FeedFilterBottomSheet : BottomSheetDialogFragment() {

  private var _binding: BottomSheetFeedFilterBinding? = null
  private val binding get() = _binding!!

  private var currentConfig: FeedFilterConfig = FeedFilterConfig()
  private var onApplyListener: ((FeedFilterConfig) -> Unit)? = null

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    _binding = BottomSheetFeedFilterBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    // Load current config
    currentConfig = FeedFilterConfig.load(requireContext())

    setupGroupModeTab()
    setupViewModeTab()
    setupSortByTab()
    setupSortOrderButtons()
    setupFieldsChips()
    setupActionButtons()
  }

  private fun setupGroupModeTab() {
    val tab = binding.groupMode
    tab.getTabAt(currentConfig.groupMode.ordinal)?.select()

    tab.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
      override fun onTabSelected(tab: TabLayout.Tab?) {
        currentConfig = currentConfig.copy(
          groupMode = FeedFilterConfig.GroupMode.entries[tab?.position ?: 0]
        )
      }

      override fun onTabUnselected(tab: TabLayout.Tab?) {}
      override fun onTabReselected(tab: TabLayout.Tab?) {}
    })
  }

  private fun setupViewModeTab() {
    // `viewMode` is a MaterialButtonToggleGroup (grid/list buttons), not a TabLayout.
    val toggleGroup = binding.viewMode

    // Set initial checked button based on currentConfig.viewMode
    val initialButtonId = if (currentConfig.viewMode == FeedFilterConfig.ViewMode.GRID) {
      binding.gridView.id
    } else {
      binding.listView.id
    }
    toggleGroup.check(initialButtonId)

    // Listen for toggle changes and update config accordingly
    toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
      if (!isChecked) return@addOnButtonCheckedListener

      val newViewMode = when (checkedId) {
        binding.gridView.id -> FeedFilterConfig.ViewMode.GRID
        binding.listView.id -> FeedFilterConfig.ViewMode.LIST
        else -> return@addOnButtonCheckedListener
      }
      currentConfig = currentConfig.copy(viewMode = newViewMode)
    }
  }

  private fun setupSortByTab() {
    val tab = binding.root.findViewById<TabLayout>(cloud.app.csplayer.R.id.sortByTabLayout)
    tab?.getTabAt(currentConfig.sortBy.ordinal)?.select()

    tab?.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
      override fun onTabSelected(tab: TabLayout.Tab?) {
        currentConfig = currentConfig.copy(
          sortBy = FeedFilterConfig.SortBy.entries[tab?.position ?: 0]
        )
        // Update sort order buttons to reflect the order for this sortBy
        updateSortOrderButtons()
      }

      override fun onTabUnselected(tab: TabLayout.Tab?) {}
      override fun onTabReselected(tab: TabLayout.Tab?) {}
    })
  }

  private fun setupSortOrderButtons() {
    // Set initial checked button based on config
    updateSortOrderButtons()

    // Listen for toggle changes
    binding.sortOrderToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
      if (isChecked) {
        val newOrder = when (checkedId) {
          binding.sortAscButton.id -> FeedFilterConfig.SortOrder.ASCENDING
          binding.sortDescButton.id -> FeedFilterConfig.SortOrder.DESCENDING
          else -> return@addOnButtonCheckedListener
        }
        currentConfig = currentConfig.withSortOrder(newOrder)
      }
    }
  }

  private fun updateSortOrderButtons() {
    val initialButton = if (currentConfig.sortOrder == FeedFilterConfig.SortOrder.ASCENDING) {
      binding.sortAscButton.id
    } else {
      binding.sortDescButton.id
    }
    binding.sortOrderToggleGroup.check(initialButton)
  }

  private fun setupFieldsChips() {
    binding.chipDuration.isChecked = currentConfig.showDuration
    binding.chipPath.isChecked = currentConfig.showPath
    binding.chipProgress.isChecked = currentConfig.showProgress
    binding.chipSize.isChecked = currentConfig.showSize
    binding.chipThumbnail.isChecked = currentConfig.showThumbnail

    binding.chipDuration.setOnCheckedChangeListener { _, isChecked ->
      currentConfig = currentConfig.copy(showDuration = isChecked)
    }

    binding.chipPath.setOnCheckedChangeListener { _, isChecked ->
      currentConfig = currentConfig.copy(showPath = isChecked)
    }
    binding.chipProgress.setOnCheckedChangeListener { _, isChecked ->
      currentConfig = currentConfig.copy(showProgress = isChecked)
    }
    binding.chipSize.setOnCheckedChangeListener { _, isChecked ->
      currentConfig = currentConfig.copy(showSize = isChecked)
    }
    binding.chipThumbnail.setOnCheckedChangeListener { _, isChecked ->
      currentConfig = currentConfig.copy(showThumbnail = isChecked)
    }
  }

  private fun setupActionButtons() {
    binding.cancelButton.setOnClickListener {
      dismiss()
    }

    binding.applyButton.setOnClickListener {
      // Save config
      FeedFilterConfig.save(requireContext(), currentConfig)
      // Notify listener
      onApplyListener?.invoke(currentConfig)
      dismiss()
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }

  companion object {
    const val TAG = "FeedFilterBottomSheet"

    fun newInstance(onApply: (FeedFilterConfig) -> Unit): FeedFilterBottomSheet {
      return FeedFilterBottomSheet().apply {
        onApplyListener = onApply
      }
    }
  }
}
