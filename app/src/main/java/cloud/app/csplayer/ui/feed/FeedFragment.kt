package cloud.app.csplayer.ui.feed

import adapters.FeedAdapter.Companion.getFeedAdapter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import cloud.app.csplayer.R
import cloud.app.csplayer.databinding.FragmentFeedBinding
import cloud.app.csplayer.ui.adapter.GridAdapter.Companion.configureGridLayout
import cloud.app.csplayer.ui.filesystem.FileTreeDialog
import cloud.app.csplayer.utils.AutoClearedValue.Companion.autoCleared
import cloud.app.csplayer.utils.Utils.showToast
import cloud.app.csplayer.utils.observe
import dagger.hilt.android.AndroidEntryPoint


@AndroidEntryPoint
class FeedFragment : Fragment(), FeedClickListener {
  private var binding by autoCleared<FragmentFeedBinding>()
  private val viewModel: FeedViewModel by viewModels()
  private val adapter by lazy { getFeedAdapter(viewModel) }

  // Stack to track folder navigation for back button support
  private val folderStack = mutableListOf<String?>()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // Get root folder path from arguments if provided
    val rootFolderPath = arguments?.getString(ARG_ROOT_FOLDER_PATH)
    viewModel.setRootFolder(rootFolderPath)

    // Initialize stack with initial folder
    if (folderStack.isEmpty()) {
      folderStack.add(rootFolderPath)
    }
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

    // Setup back button handler for folder navigation
    setupBackPressHandler()

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
    configureGridLayout(
      binding.rvFeed,
      adapter.withLoadingStates() {
        // Retry on error - refresh the PagingData
        adapter.refresh()
      }
    )

    // Setup SwipeRefreshLayout
    binding.swipeRefresh.setOnRefreshListener {
      // Refresh data when user pulls down
      adapter.refresh()
    }

    // Observe loading states to hide refresh indicator
    observe(viewModel.feedData) {
      binding.swipeRefresh.isRefreshing = false
    }


    observe(viewModel.displayType) {
      when (it) {
        FeedViewModel.DisplayType.LIST -> binding.toolbar.menu.findItem(R.id.displayType)
          .setIcon(R.drawable.outline_grid_view_24)

        FeedViewModel.DisplayType.GRID -> binding.toolbar.menu.findItem(R.id.displayType)
          .setIcon(R.drawable.outline_view_agenda_24)
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
        // Navigate into folder by updating ViewModel and refreshing
        // Push current folder to stack
        folderStack.add(item.folder.path)

        viewModel.setRootFolder(item.folder.path)
        adapter.refresh()

        // Scroll to top to show new content
        binding.rvFeed.scrollToPosition(0)
      }

      is FeedData.VideoItem -> {
        // TODO: Open video player
        showToast("Playing: ${item.title}")
      }

      is FeedData.AudioItem -> {
        // TODO: Open audio player
        showToast("Playing audio: ${item.title}")
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
   * Setup back button handler to navigate through folder stack
   */
  private fun setupBackPressHandler() {
    val callback = object : OnBackPressedCallback(true) {
      override fun handleOnBackPressed() {
        if (folderStack.size > 1) {
          // Pop current folder from stack
          folderStack.removeLastOrNull()

          // Navigate to previous folder
          val previousFolder = folderStack.lastOrNull()
          viewModel.setRootFolder(previousFolder)
          adapter.refresh()

          // Scroll to top
          binding.rvFeed.scrollToPosition(0)
        } else {
          // No more folders in stack, let system handle back press
          isEnabled = false
          requireActivity().onBackPressedDispatcher.onBackPressed()
        }
      }
    }

    requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)
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
