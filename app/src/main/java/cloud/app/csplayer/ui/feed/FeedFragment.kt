package cloud.app.csplayer.ui.feed

import adapters.FeedAdapter
import adapters.FeedAdapter.Companion.getFeedAdapter
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import cloud.app.csplayer.R
import cloud.app.csplayer.ads.AdManager
import cloud.app.csplayer.databinding.FragmentFeedBinding
import cloud.app.csplayer.media.model.SyncState
import cloud.app.csplayer.ui.adapter.GridAdapter.Companion.configureGridLayout
import cloud.app.csplayer.ui.adapter.GridAdapter.Companion.recalculateGridLayout
import cloud.app.csplayer.ui.player.EXTRA_TITLE
import cloud.app.csplayer.ui.player.EXTRA_VIDEO_URLS_NAME_HEADERS
import cloud.app.csplayer.utils.AutoClearedValue.Companion.autoCleared
import cloud.app.csplayer.utils.FastScrollerHelper
import cloud.app.csplayer.utils.UIHelper.navigate
import cloud.app.csplayer.utils.Utils.showToast
import cloud.app.csplayer.utils.observe
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject


@AndroidEntryPoint
class FeedFragment : Fragment(), FeedClickListener {
  private var binding by autoCleared<FragmentFeedBinding>()
  private val viewModel: FeedViewModel by viewModels()

  @Inject
  lateinit var adManager: AdManager

  // Keep reference to current Snackbar for sync state
  private var syncSnackbar: Snackbar? = null


  private lateinit var adapter: FeedAdapter

  // Permission request launcher (multiple permissions)
  private val permissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { permissions ->
    val allGranted = permissions.values.all { it }
    if (allGranted) {
      // Permissions granted, trigger MediaRepository refresh and reload dat
      showToast(getString(R.string.permissions_granted))

      // Refresh MediaRepository to sync from MediaStore
      viewModel.refreshMediaRepository()

      // Refresh adapter to reload with new data
      adapter.refresh()
    } else {
      // Permissions denied
      showToast(getString(R.string.permissions_denied))
    }
  }

  // Multiple permission request launcher (for sync errors - need both video and audio)
  private val requestPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { permissions ->
    val allGranted = permissions.values.all { it }
    if (allGranted) {
      // Permission granted, retry sync
      showToast(getString(R.string.permissions_granted))
      viewModel.refreshMediaRepository()
      adapter.refresh()
    } else {
      // Permission denied
      showToast(getString(R.string.permissions_denied))
    }
  }

  private fun getRequiredPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      arrayOf(
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_AUDIO
      )
    } else {
      arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
  }

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

    FastScrollerHelper.applyTo(binding.rvFeed)
    adapter = getFeedAdapter(viewModel, adManager)
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
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
          }
        } catch (e: Exception) {
          // Ignore if we can't find the subtitle view
        }
      }
    } else {
      // Clear navigation icon and subtitle when at root level
      binding.toolbar.navigationIcon = null
      binding.toolbar.subtitle = null
    }

    ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
      val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
      val displayCutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
      view.setPadding(
        maxOf(systemBars.left, displayCutout.left),
        maxOf(systemBars.top, displayCutout.top),
        maxOf(systemBars.right, displayCutout.right),
        systemBars.bottom
      )
      insets
    }

    // Configure adapter with error handling
    val adapterWithStates = adapter.withLoadingStates(
      errorMessage = null,
      buttonText = null
    ) {
      // Handle retry/permission request dynamically
      if (!hasMediaPermissions()) {
        requestMediaPermissions()
      } else {
        adapter.refresh()
      }
    }


//    // Listen to load states to detect and handle different error types
//    adapter.addLoadStateListener { loadStates ->
//      val errorState = loadStates.refresh as? androidx.paging.LoadState.Error
//      if (errorState != null) {
//        val exception = errorState.error
//
//        // Update error message based on exception type
//        when {
//          exception is SecurityException || !hasMediaPermissions() -> {
//            // Permission error
//            adapterWithStates.updateErrorMessage(
//              message = getString(R.string.permission_required_message),
//              buttonText = getString(R.string.grant_permission)
//            )
//          }
//
//          else -> {
//            // Generic error - use default messages
//            adapterWithStates.updateErrorMessage(
//              message = exception.message ?: getString(R.string.error_loading),
//              buttonText = getString(R.string.retry)
//            )
//          }
//        }
//      }
//    }

    configureGridLayout(binding.rvFeed, adapterWithStates)

    // Setup SwipeRefreshLayout
    binding.swipeRefresh.setOnRefreshListener {
      // Refresh data when user pulls down
      adapter.refresh()
    }

    // Observe loading states to hide refresh indicator
    observe(viewModel.feedData) {
      binding.swipeRefresh.isRefreshing = false
    }

    observe(viewModel.syncState) { state ->
      when (state) {
        is SyncState.Idle -> {
          // Dismiss any existing snackbar
          syncSnackbar?.dismiss()
          syncSnackbar = null
        }

        is SyncState.Syncing -> {

        }

        is SyncState.Completed -> {
          //adapter.refresh()
        }

        is SyncState.Error -> {
          // Show error with appropriate action based on error type
          syncSnackbar?.dismiss()

          when (state) {
            is SyncState.Error.MissingPermission -> {
              syncSnackbar = Snackbar.make(
                binding.root,
                state.message,
                Snackbar.LENGTH_LONG
              ).apply {
                setAction("Grant Permission") {
                  requestPermissionLauncher.launch(getRequiredPermissions())
                }
                show()
              }
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


    observe(viewModel.viewMode) {

    }

    // Setup SearchView
    val searchItem = binding.toolbar.menu.findItem(R.id.search)
    val searchView = searchItem?.actionView as? androidx.appcompat.widget.SearchView

    if (searchView != null) {
      Timber.d("SearchView found and configured")
      searchView.apply {
        queryHint = getString(R.string.search_hint)
        maxWidth = Integer.MAX_VALUE // Make SearchView expand fully

        setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
          override fun onQueryTextSubmit(query: String?): Boolean {
            Timber.d("onQueryTextSubmit called with query: $query")
            // Update search query in ViewModel
            viewModel.searchQuery.value = query?.trim()?.takeIf { it.isNotEmpty() }
            return true
          }

          override fun onQueryTextChange(newText: String?): Boolean {
            Timber.d("onQueryTextChange called with text: $newText")
            // Update search query in ViewModel as user types
            viewModel.searchQuery.value = newText?.trim()?.takeIf { it.isNotEmpty() }
            return true
          }
        })

        // Clear search query when SearchView is closed
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
          FeedFilterBottomSheet.newInstance { config ->
            // Apply filter config
            applyFilterConfig(config)
          }.show(parentFragmentManager, FeedFilterBottomSheet.TAG)
        }
      }
      true
    }
  }

  override fun onItemClick(item: FeedData) {
    //open item based on its type
    when (item) {
      is FeedData.FolderItem -> {
        // Navigate into folder using Navigation Component
        val bundle = Bundle().apply {
          putString("root_folder_path", item.folder.path)
        }

        // Use Navigation Component to navigate with automatic backstack management
        // R.id will be generated after build, fallback to dynamic navigation
        try {
          findNavController().navigate(R.id.action_feedFragment_self, bundle)
        } catch (e: Exception) {
          // Fallback if action ID not generated yet
          findNavController().navigate(R.id.feedFragment, bundle)
        }
      }

      is FeedData.MediaItem -> {
        // Build playlist with all media files in current folder/context
        buildAndPlayPlaylist(item)
      }

      is FeedData.AdItem -> {
        // TODO: Handle ad click
        showToast("Ad clicked: ${item.title}")
      }

      is FeedData.HorizontalList -> {
        // Horizontal lists don't have individual click action
        // Items inside the list have their own click handlers
      }
    }
  }

  /**
   * Build playlist from current feed data and play starting from clicked item
   */
  private fun buildAndPlayPlaylist(clickedItem: FeedData.MediaItem) {
    // Launch coroutine to collect current feed data
    viewLifecycleOwner.lifecycleScope.launch {
      try {
        // Get current snapshot of feed data from adapter
        val snapshot = adapter.snapshot()

        // Extract all media items (filter out folders, ads, and horizontal lists)
        val allMediaItems = snapshot.items.filterIsInstance<FeedData.MediaItem>()

        if (allMediaItems.isEmpty()) {
          // Fallback to single file if no media items found
          playMediaItem(clickedItem)
          return@launch
        }

        // Find the index of clicked item
        val clickedIndex = allMediaItems.indexOfFirst {
          it.media.uri == clickedItem.media.uri
        }.coerceAtLeast(0)

        // Build URLs and titles lists for playlist
        val urls = allMediaItems.map { it.media.uri }
        val titles = allMediaItems.map { it.title }

        // Navigate to MPV player with playlist
        activity?.navigate(
          R.id.global_to_navigation_mpv_player,
          bundleOf(
            "playlist_urls" to ArrayList(urls),
            "playlist_titles" to ArrayList(titles),
            "playlist_start_index" to clickedIndex,
            "started_from_intent" to false,
            EXTRA_TITLE to clickedItem.title
          )
        )

        showToast("Playing ${clickedIndex + 1} of ${allMediaItems.size}")

      } catch (e: Exception) {
        // Fallback to single file on error
        Timber.e(e, "Error building playlist, falling back to single file")
        playMediaItem(clickedItem)
      }
    }
  }

  /**
   * Play single media item (fallback)
   */
  private fun playMediaItem(item: FeedData.MediaItem) {
    when (item.type) {
      FeedData.Type.Video, FeedData.Type.VideoSmall -> {
        // Navigate to MPV player for video
        activity?.navigate(
          R.id.global_to_navigation_mpv_player,
          bundleOf(
            EXTRA_VIDEO_URLS_NAME_HEADERS to arrayListOf<String>(
              item.media.uri,
              item.media.path,
              ""
            ).toTypedArray(),
            EXTRA_TITLE to item.title
          )
        )
      }

      FeedData.Type.Audio, FeedData.Type.AudioSmall -> {
        // Navigate to MPV player for audio
        activity?.navigate(
          R.id.global_to_navigation_mpv_player,
          bundleOf(
            EXTRA_VIDEO_URLS_NAME_HEADERS to arrayListOf<String>(
              item.media.uri,
              "user_url_1",
              ""
            ).toTypedArray()
          )
        )
      }

      else -> {
        // Shouldn't happen
        showToast("Playing: ${item.title}")
      }
    }
  }


  /**
   * Check if app has required media permissions
   */
  private fun hasMediaPermissions(): Boolean {
    val context = requireContext()

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      // Android 13+ (API 33+) - Need READ_MEDIA_VIDEO and READ_MEDIA_AUDIO
      val hasVideoPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_MEDIA_VIDEO
      ) == PackageManager.PERMISSION_GRANTED

      val hasAudioPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_MEDIA_AUDIO
      ) == PackageManager.PERMISSION_GRANTED

      hasVideoPermission && hasAudioPermission
    } else {
      // Android 12 and below - Need READ_EXTERNAL_STORAGE
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_EXTERNAL_STORAGE
      ) == PackageManager.PERMISSION_GRANTED
    }
  }

  /**
   * Request required media permissions
   */
  private fun requestMediaPermissions() {
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      // Android 13+ (API 33+)
      arrayOf(
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_AUDIO
      )
    } else {
      // Android 12 and below
      arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    permissionLauncher.launch(permissions)
  }


  /**
   * Find the subtitle TextView in Toolbar to apply custom styling
   */
  private fun findSubtitleTextView(toolbar: androidx.appcompat.widget.Toolbar): android.widget.TextView? {
    for (i in 0 until toolbar.childCount) {
      val child = toolbar.getChildAt(i)
      if (child is android.widget.TextView) {
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
    FeedFilterConfig.save(requireContext(), config)

    // Refresh the adapter to apply changes
    adapter.refresh()

    showToast("Settings applied: ${config.groupMode.name} / ${config.viewMode.name}")
  }

  /**
   * Handle configuration changes (like orientation) to recalculate grid layout
   * This is called from MainActivity.onConfigurationChanged()
   */
  override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
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
}
