package cloud.streamless.torream.ui.feed

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import cloud.streamless.torream.databinding.BottomSheetFeedFilterBinding
import cloud.streamless.torream.ui.dialog.DockingDialog
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Bottom sheet dialog for feed filtering and display options
 */
@AndroidEntryPoint
class FeedFilterDialog : DockingDialog() {

  @Inject
  lateinit var sharedPreferences: SharedPreferences

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
    currentConfig = FeedFilterConfig.load(sharedPreferences)

    setupGroupModeTab()
    setupViewModeTab()
    setupSortByTab()
    setupSortOrderButtons()
    setupFieldsChips()
    setupActionButtons()
  }

  private fun setupGroupModeTab() {
    val toggleGroup = binding.groupMode

    val initialButtonId = if (currentConfig.groupMode == FeedFilterConfig.GroupMode.FOLDERS) {
      binding.folderMode.id
    } else {
      binding.mediaMode.id
    }
    toggleGroup.check(initialButtonId)

    toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
      if (!isChecked) return@addOnButtonCheckedListener

      val newGroupMode = when (checkedId) {
        binding.folderMode.id -> FeedFilterConfig.GroupMode.FOLDERS
        binding.mediaMode.id -> FeedFilterConfig.GroupMode.CAROUSEL
        else -> return@addOnButtonCheckedListener
      }
      currentConfig = currentConfig.copy(groupMode = newGroupMode)
    }
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
    val chipBySortBy = mapOf(
      FeedFilterConfig.SortBy.TITLE to binding.sortByTitle,
      FeedFilterConfig.SortBy.DURATION to binding.sortByDuration,
      FeedFilterConfig.SortBy.DATE to binding.sortByDate,
      FeedFilterConfig.SortBy.SIZE to binding.sortBySize,
      FeedFilterConfig.SortBy.LOCATION to binding.sortByLocation,
    )

    chipBySortBy[currentConfig.sortBy]?.isChecked = true

    binding.sortByChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
      val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
      val newSortBy = chipBySortBy.entries.firstOrNull { it.value.id == checkedId }?.key
        ?: return@setOnCheckedStateChangeListener
      currentConfig = currentConfig.copy(sortBy = newSortBy)
      // Update sort order buttons to reflect the order for this sortBy
      updateSortOrderButtons()
    }
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
      FeedFilterConfig.save(sharedPreferences, currentConfig)
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

    fun newInstance(onApply: (FeedFilterConfig) -> Unit): FeedFilterDialog {
      return FeedFilterDialog().apply {
        onApplyListener = onApply
      }
    }
  }
}
