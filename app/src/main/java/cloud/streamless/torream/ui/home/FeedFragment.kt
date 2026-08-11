package cloud.streamless.torream.ui.home

import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import cloud.streamless.torream.MainActivityViewModel
import cloud.streamless.torream.MainActivityViewModel.Companion.applyContentRect
import cloud.streamless.torream.R
import cloud.streamless.torream.ads.AdManager
import cloud.streamless.torream.databinding.FragmentFeedBinding
import cloud.streamless.torream.download.DownloadCoordinator
import cloud.streamless.torream.model.SyncState
import cloud.streamless.torream.ui.adapter.GridAdapter.Companion.configureGridLayout
import cloud.streamless.torream.ui.adapter.GridAdapter.Companion.recalculateGridLayout
import cloud.streamless.torream.ui.feed.FeedAction
import cloud.streamless.torream.ui.feed.FeedFilterConfig
import cloud.streamless.torream.ui.feed.FeedFilterDialog
import cloud.streamless.torream.ui.feed.adapters.FeedAdapter
import cloud.streamless.torream.utils.AutoClearedValue.Companion.autoCleared
import cloud.streamless.torream.utils.FastScrollerHelper
import cloud.streamless.torream.utils.Utils.showToast
import cloud.streamless.torream.utils.hasMediaPermissions
import cloud.streamless.torream.utils.observe
import cloud.streamless.torream.utils.requestMediaPermissions
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject


@AndroidEntryPoint
class FeedFragment : Fragment() {
  private var binding by autoCleared<FragmentFeedBinding>()
  private val viewModel: FeedViewModel by viewModels()
  private val mainViewModel: MainActivityViewModel by activityViewModels()

  @Inject
  lateinit var adManager: AdManager

  @Inject
  lateinit var downloadRepository: cloud.streamless.torream.download.DownloadRepository

  @Inject
  lateinit var sharedPreferences: SharedPreferences

  @Inject
  lateinit var downloadCoordinator: DownloadCoordinator

  @Inject
  lateinit var mediaRepository: cloud.streamless.torream.media.repository.MediaRepository

  @Inject
  lateinit var playlistRepository: cloud.streamless.torream.media.repository.PlaylistRepository

  @Inject
  lateinit var favoriteRepository: cloud.streamless.torream.favorites.FavoriteRepository

  private var syncSnackbar: Snackbar? = null

  private lateinit var adapter: FeedAdapter

  private val permissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { permissions ->
    val allGranted = permissions.values.all { it }
    if (allGranted) {
      // Permissions granted, trigger MediaRepository refresh and reload data
      showToast(getString(R.string.permissions_granted))

      Timber.d("Permission granted - starting data refresh")

      // Set flag to refresh adapter when sync completes
      waitingForSyncToRefresh = true

      viewModel.refreshMediaRepository()
    } else {
      // Permissions denied - adapter will continue showing error state
      showToast(getString(R.string.permissions_denied))
    }
  }

  // Flag to track if we should refresh adapter after sync completes
  private var waitingForSyncToRefresh = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // Get root folder path from Navigation arguments or manual bundle
    val rootFolderPath = arguments?.getString("root_folder_path")
      ?: arguments?.getString(ARG_ROOT_FOLDER_PATH)
    viewModel.setRootFolder(rootFolderPath)
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    binding = FragmentFeedBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    applyContentRect(binding.appBar, binding.swipeRefresh, binding.appBarOutline)
    FastScrollerHelper.applyTo(binding.rvFeed)

    // Setup basic UI components (toolbar, search, etc.)
    setupBasicUI()

    // Always setup adapter - it will show error state if no permission
    setupAdapter()
  }

  /**
   * Setup basic UI components (toolbar, search) that don't require permissions
   */
  private fun setupBasicUI() {
    // Observe title from ViewModel (will be root folder name or "Feed")
    observe(viewModel.title) { title ->
      binding.toolbar.title = title
      binding.root.contentDescription = title
    }

    // Show back button if we're in a subfolder (rootFolderPath != null)
    val rootFolderPath = arguments?.getString("root_folder_path")
      ?: arguments?.getString(ARG_ROOT_FOLDER_PATH)

    if (rootFolderPath != null) {
      // Show back button
      binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back_24)
      binding.toolbar.setNavigationOnClickListener {
        // Navigate back
        findNavController().navigateUp()
      }

      // Set subtitle to show the full path
      binding.toolbar.subtitle = rootFolderPath

      // Make subtitle ellipsize in the middle to show "start...end" format
      // This ensures both the beginning and the folder name (end of path) are visible
      binding.toolbar.post {
        // Find the subtitle TextView and set ellipsize
        try {
          val subtitleView = findSubtitleTextView(binding.toolbar)
          subtitleView?.apply {
            setSingleLine()
            ellipsize = TextUtils.TruncateAt.MIDDLE
          }
        } catch (_: Exception) {
          // Ignore if we can't find the subtitle view
        }
      }
    } else {
      // Clear navigation icon and subtitle when at root level
      binding.toolbar.navigationIcon = null
      binding.toolbar.subtitle = null
    }

    // Setup SearchView
    val searchItem = binding.toolbar.menu.findItem(R.id.search)
    val searchView = searchItem?.actionView as? SearchView

    if (searchView != null) {
      Timber.d("SearchView found and configured")
      searchView.apply {
        queryHint = getString(R.string.search_hint)
        maxWidth = Integer.MAX_VALUE // Make SearchView expand fully

        setOnQueryTextListener(object : SearchView.OnQueryTextListener {
          override fun onQueryTextSubmit(query: String?): Boolean {
            Timber.d("onQueryTextSubmit called with query: $query")
            viewModel.searchQuery.value = query?.trim()?.takeIf { it.isNotEmpty() }
            return true
          }

          override fun onQueryTextChange(newText: String?): Boolean {
            Timber.d("onQueryTextChange called with text: $newText")
            viewModel.searchQuery.value = newText?.trim()?.takeIf { it.isNotEmpty() }
            return true
          }
        })

        setOnCloseListener {
          Timber.d("SearchView closed")
          viewModel.searchQuery.value = null
          false
        }
      }
    } else {
      Timber.e("SearchView not found in menu!")
    }

    binding.toolbar.setOnMenuItemClickListener {
      when (it.itemId) {
        R.id.quickSettings -> {
          FeedFilterDialog.newInstance { config ->
            applyFilterConfig(config)
          }.show(parentFragmentManager, FeedFilterDialog.TAG)
        }
      }
      true
    }
  }

  /**
   * Setup adapter and data loading
   */
  private fun setupAdapter() {

    adapter = FeedAdapter(
      FeedAction(this, downloadRepository, downloadCoordinator, favoriteRepository, mediaRepository, playlistRepository),
      adManager,
      viewModel.filterConfig.value,
      downloadRepository
    )

    // Observe filterConfig changes and update adapter
    observe(viewModel.filterConfig) { config ->
      adapter.updateFilterConfig(config)
      // Refresh to rebind all items with new visibility settings
      adapter.notifyDataSetChanged()
    }

    observe(viewModel.feedData) {
      adapter.submitData(it)
      binding.swipeRefresh.isRefreshing = false
    }

    // Configure adapter with dynamic error handling
    val adapterWithStates = adapter.withLoadingStates(
      errorMessage = null,
      buttonText = null
    ) {
      // Handle retry/permission request dynamically
      if (!requireContext().hasMediaPermissions()) {
        requireContext().requestMediaPermissions(permissionLauncher)
      } else {
        adapter.refresh()
      }
    }

    // Listen to load states to dynamically update error message based on permission state
    adapter.addLoadStateListener { loadStates ->
      val errorState = loadStates.refresh as? androidx.paging.LoadState.Error
      if (errorState != null) {
        // Check if it's a permission error
        if (!requireContext().hasMediaPermissions()) {
          adapterWithStates.updateErrorMessage(
            message = getString(R.string.permission_required_message),
            buttonText = getString(R.string.grant_permission)
          )
        } else {
          // Generic error
          adapterWithStates.updateErrorMessage(
            message = errorState.error.message ?: getString(R.string.error_loading),
            buttonText = getString(R.string.retry)
          )
        }
      }
    }

    configureGridLayout(binding.rvFeed, adapterWithStates)

    // If no permission at startup, force show error state by clearing data first
    if (!requireContext().hasMediaPermissions()) {
      Timber.d("No permission detected - forcing error state display")
      // Trigger refresh which will call PagingSource that should throw exception
      viewLifecycleOwner.lifecycleScope.launch {
        // Submit empty data first to clear any cached data
        adapter.submitData(androidx.paging.PagingData.empty())
        // Then refresh to trigger error
        adapter.refresh()
      }
    }

    // Setup SwipeRefreshLayout
    binding.swipeRefresh.setOnRefreshListener {
      // Refresh data when user pulls down
      adapter.refresh()
    }

    observe(viewModel.syncState) { state ->
      when (state) {
        is SyncState.Idle -> {
          // Dismiss any existing snackbar
          syncSnackbar?.dismiss()
          syncSnackbar = null
        }

        is SyncState.Syncing -> {
          Timber.d("Sync in progress...")
        }

        is SyncState.Completed -> {
          Timber.d("Sync completed")

          // If we were waiting for sync to complete after permission grant, refresh adapter now
          if (waitingForSyncToRefresh) {
            Timber.d("Permission was granted, refreshing adapter to load new data")
            waitingForSyncToRefresh = false
            adapter.refresh()
          }
        }

        is SyncState.Error -> {
          // Show error with appropriate action based on error type
          syncSnackbar?.dismiss()

          when (state) {
            is SyncState.Error.MissingPermission -> {
              // Don't show snackbar for permission error
              // It's already shown in RecyclerView error adapter
              // Just log it
              Timber.w("Permission missing - error UI shown in RecyclerView")
            }

            is SyncState.Error.StorageError -> {
              syncSnackbar = Snackbar.make(
                binding.root,
                state.message,
                Snackbar.LENGTH_LONG
              ).apply {
                setAction("Retry") {
                  viewModel.refreshMediaRepository()
                }
                show()
              }
            }

            is SyncState.Error.NetworkError -> {
              syncSnackbar = Snackbar.make(
                binding.root,
                state.message,
                Snackbar.LENGTH_LONG
              ).apply {
                setAction("Retry") {
                  viewModel.refreshMediaRepository()
                }
                show()
              }
            }

            else -> {
              syncSnackbar = Snackbar.make(
                binding.root,
                state.message,
                Snackbar.LENGTH_LONG
              ).apply {
                setAction("Retry") {
                  viewModel.refreshMediaRepository()
                }
                show()
              }
            }
          }
        }
      }
    }


  }

  /**
   * Find the subtitle TextView in Toolbar to apply custom styling
   */
  private fun findSubtitleTextView(toolbar: Toolbar): TextView? {
    for (i in 0 until toolbar.childCount) {
      val child = toolbar.getChildAt(i)
      if (child is TextView) {
        val text = child.text
        if (text != null && text.toString() == toolbar.subtitle) {
          return child
        }
      }
    }
    return null
  }

  /**
   * Apply filter configuration from bottom sheet
   */
  private fun applyFilterConfig(config: FeedFilterConfig) {
    // Update the filterConfig in ViewModel - this will automatically update displayType and folderViewMode
    viewModel.filterConfig.value = config

    // Save the config to persist across app restarts
    FeedFilterConfig.save(sharedPreferences, config)

    // Note: no need to call adapter.refresh() here - updating filterConfig.value
    // already triggers a new Pager via flatMapLatest. Calling refresh() too would
    // race a reload on the old (soon-to-be-cancelled) PagingSource against the new one.

    showToast("Settings applied: ${config.groupMode.name} / ${config.viewMode.name}")
  }

  /**
   * Handle configuration changes (like orientation) to recalculate grid layout
   * This is called from MainActivity.onConfigurationChanged()
   */
  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    Timber.d("FeedFragment configuration changed: orientation=${newConfig.orientation}")

    recalculateGridLayout(
      binding.rvFeed,
      adapter
    )
    Timber.d("Grid layout recalculated for new orientation")

  }

  companion object {
    private const val ARG_ROOT_FOLDER_PATH = "root_folder_path"

    /**
     * Create new instance of FeedFragment
     * @param rootFolderPath Optional root folder path to filter. If null, loads all folders from preferences.
     */
    fun newInstance(rootFolderPath: String? = null): FeedFragment {
      return FeedFragment().apply {
        arguments = Bundle().apply {
          putString(ARG_ROOT_FOLDER_PATH, rootFolderPath)
        }
      }
    }
  }

  // Allow external callers (e.g. FeedAction) to request adapter refresh
  fun refreshAdapter() {
    try {
      Timber.d("FeedFragment.refreshAdapter called - submitting empty PagingData and refreshing")
      viewLifecycleOwner.lifecycleScope.launch {
        try {
          adapter.submitData(androidx.paging.PagingData.empty())
          adapter.refresh()
        } catch (t: Throwable) {
          Timber.w(t, "Failed to submit empty PagingData in refreshAdapter")
        }
      }
    } catch (_: Exception) {
      // best-effort, ignore if adapter not initialized
    }
  }
}
