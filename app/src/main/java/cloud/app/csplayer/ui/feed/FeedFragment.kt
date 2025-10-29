package cloud.app.csplayer.ui.feed

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
import cloud.app.csplayer.databinding.FragmentFeedBinding
import cloud.app.csplayer.media.model.SyncState
import cloud.app.csplayer.ui.adapter.GridAdapter.Companion.configureGridLayout
import cloud.app.csplayer.ui.filesystem.FileTreeDialog
import cloud.app.csplayer.ui.player.EXTRA_TITLE
import cloud.app.csplayer.ui.player.EXTRA_VIDEO_URLS_NAME_HEADERS
import cloud.app.csplayer.utils.AutoClearedValue.Companion.autoCleared
import cloud.app.csplayer.utils.UIHelper.navigate
import cloud.app.csplayer.utils.Utils.showToast
import cloud.app.csplayer.utils.observe
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch


@AndroidEntryPoint
class FeedFragment : Fragment(), FeedClickListener {
  private var binding by autoCleared<FragmentFeedBinding>()
  private val viewModel: FeedViewModel by viewModels()

  // Keep reference to current Snackbar for sync state
  private var syncSnackbar: Snackbar? = null

  // Lazy initialization to allow permission check callback
  private val adapter by lazy {
    getFeedAdapter(viewModel)
  }

  // Permission request launcher
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

    // Observe title from ViewModel (will be root folder name or "Feed")
    observe(viewModel.title) { title ->
      binding.toolbar.title = title
      binding.root.contentDescription = title
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

    // Listen to load states to detect and handle different error types
    adapter.addLoadStateListener { loadStates ->
      val errorState = loadStates.refresh as? androidx.paging.LoadState.Error
      if (errorState != null) {
        val exception = errorState.error

        // Update error message based on exception type
        when {
          exception is SecurityException || !hasMediaPermissions() -> {
            // Permission error
            adapterWithStates.updateErrorMessage(
              message = getString(R.string.permission_required_message),
              buttonText = getString(R.string.grant_permission)
            )
          }

          else -> {
            // Generic error - use default messages
            adapterWithStates.updateErrorMessage(
              message = exception.message ?: getString(R.string.error_loading),
              buttonText = getString(R.string.retry)
            )
          }
        }
      }
    }

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
          adapter.refresh()
        }

        is SyncState.Error -> {
          // Show error with retry action
          syncSnackbar?.dismiss()
          syncSnackbar = Snackbar.make(
            binding.root,
            "Sync error: ${state.message}",
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


    observe(viewModel.displayType) {
      when (it) {
        FeedViewModel.DisplayType.GRID -> binding.toolbar.menu.findItem(R.id.displayType)
          .setIcon(R.drawable.outline_view_agenda_24)  // Tree/Grid view

        FeedViewModel.DisplayType.LIST -> binding.toolbar.menu.findItem(R.id.displayType)
          .setIcon(R.drawable.outline_grid_view_24)  // Folder only view
      }
    }
    binding.toolbar.setOnMenuItemClickListener {
      when (it.itemId) {
        R.id.displayType -> {
          viewModel.changeDisplayType()
        }

        R.id.sort -> {
        }

        R.id.add_folders -> {
          // Show FileTreeDialog to select folder
          FileTreeDialog.newInstance(
            onFolderSelected = { selectedFolder ->
              // Handle selected folder
              showToast("Folder added: ${selectedFolder.name}")

              // Refresh the feed to show newly added folder
              adapter.refresh()
            }
          ).show(parentFragmentManager, "FileTreeDialog")
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
        // Open media player based on type
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
