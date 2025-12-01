package cloud.app.csplayer.ui.library


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import cloud.app.csplayer.MainActivityViewModel.Companion.applyContentRect
import cloud.app.csplayer.R
import cloud.app.csplayer.ads.AdManager
import cloud.app.csplayer.databinding.FragmentLibraryBinding
import cloud.app.csplayer.download.DownloadRepository
import cloud.app.csplayer.download.DownloadCoordinator
import cloud.app.csplayer.ui.adapter.GridAdapter.Companion.configureGridLayout
import cloud.app.csplayer.ui.feed.FeedAction
import cloud.app.csplayer.ui.feed.adapters.FeedAdapter
import cloud.app.csplayer.utils.AutoClearedValue.Companion.autoCleared
import cloud.app.csplayer.utils.FastScrollerHelper
import cloud.app.csplayer.utils.observe
import com.google.android.material.tabs.TabLayout
import android.widget.Toast
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * LibraryFragment displays user's library organized into sections:
 * - Downloads: Active torrent downloads
 * - Favorites: Favorite media items
 * - History: Recently played media
 * - Playlists: User-created playlists
 */
@AndroidEntryPoint
class LibraryFragment : Fragment() {
  private var binding by autoCleared<FragmentLibraryBinding>()
  private val viewModel: LibraryViewModel by viewModels()

  @Inject
  lateinit var adManager: AdManager

  @Inject
  lateinit var downloadRepository: DownloadRepository

  @Inject
  lateinit var downloadCoordinator: DownloadCoordinator

  @Inject
  lateinit var mediaRepository: cloud.app.csplayer.media.repository.MediaRepository

  @Inject
  lateinit var playlistRepository: cloud.app.csplayer.media.repository.PlaylistRepository

  @Inject
  lateinit var favoriteRepository: cloud.app.csplayer.favorites.FavoriteRepository

  private lateinit var adapter: FeedAdapter

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    binding = FragmentLibraryBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    applyContentRect(binding.appBar, binding.swipeRefresh)
    FastScrollerHelper.applyTo(binding.rvLibrary)

    // Handle section argument if provided
    arguments?.getInt("section", -1)?.let { sectionOrdinal ->
      if (sectionOrdinal >= 0) {
        viewModel.section.value = LibrarySection.entries[sectionOrdinal]
      }
    }

    setupUI()
    setupAdapter()
  }

  private fun setupUI() {
    binding.toolbar.title = getString(R.string.library)
    binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back_24)
    binding.toolbar.setNavigationOnClickListener {
      findNavController().navigateUp()
    }

    // Handle menu item clicks
    binding.toolbar.setOnMenuItemClickListener { menuItem ->
      when (menuItem.itemId) {
        R.id.createPlaylist -> {
          showCreatePlaylistDialog()
          true
        }
        R.id.clearHistory -> {
          clearHistory()
          true
        }
        else -> false
      }
    }

    binding.swipeRefresh.setOnRefreshListener {
      // Refresh data when user pulls down
      adapter.refresh()
    }
    // Setup tabs for different library sections
    setupTabs()

  }

  private fun setupTabs() {
    // Add tabs
    binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.downloads))
    binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.favorites))
    binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.history))
    binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.playlists))

    // Set default tab based on viewModel
    binding.tabLayout.getTabAt(viewModel.section.value.ordinal)?.select()

    // Handle tab selection
    binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
      override fun onTabSelected(tab: TabLayout.Tab?) {
        when (tab?.position) {
          0 -> viewModel.section.value = LibrarySection.DOWNLOADS
          1 -> viewModel.section.value = LibrarySection.FAVORITES
          2 -> viewModel.section.value = LibrarySection.HISTORY
          3 -> viewModel.section.value = LibrarySection.PLAYLISTS
        }
        Timber.d("Tab selected: ${viewModel.section.value}")
      }

      override fun onTabUnselected(tab: TabLayout.Tab?) {}
      override fun onTabReselected(tab: TabLayout.Tab?) {}
    })
  }

  private fun setupAdapter() {
    adapter = FeedAdapter(
      FeedAction(this, downloadRepository, downloadCoordinator, favoriteRepository, mediaRepository, playlistRepository),
      adManager,
      viewModel.filterConfig.value,
      downloadRepository
    )

    // Observe feed data
    observe(viewModel.feedData) {
      adapter.submitData(it)
      binding.swipeRefresh.isRefreshing = false
    }

    // Configure adapter with loading states
    val adapterWithStates = adapter.withLoadingStates(
      errorMessage = getString(R.string.error_loading),
      buttonText = getString(R.string.retry)
    ) {
      adapter.refresh()
    }

    // Configure grid layout
    configureGridLayout(binding.rvLibrary, adapterWithStates)
  }

  private fun showCreatePlaylistDialog() {
    val dialog = CreatePlaylistDialog { name, description ->
      // Create playlist
      viewLifecycleOwner.lifecycleScope.launch {
        try {
          val playlistId = playlistRepository.createPlaylist(name, description)
          Timber.d("Playlist created with id: $playlistId")
          Toast.makeText(requireContext(), getString(R.string.playlist_created, name), Toast.LENGTH_SHORT).show()
          // Refresh if we're on the playlists tab
          if (viewModel.section.value == LibrarySection.PLAYLISTS) {
            adapter.refresh()
          }
        } catch (e: Exception) {
          Timber.e(e, "Failed to create playlist")
          Toast.makeText(requireContext(), getString(R.string.failed_to_create_playlist), Toast.LENGTH_SHORT).show()
        }
      }
    }
    dialog.show(childFragmentManager, CreatePlaylistDialog.TAG)
  }

  private fun clearHistory() {
    androidx.appcompat.app.AlertDialog.Builder(requireContext())
      .setTitle(getString(R.string.clear_history))
      .setMessage(getString(R.string.clear_history_message))
      .setPositiveButton(getString(R.string.clear_history_confirm)) { _, _ ->
        viewLifecycleOwner.lifecycleScope.launch {
          try {
            val success = viewModel.clearHistory()
            if (success) {
              Toast.makeText(requireContext(), getString(R.string.history_cleared), Toast.LENGTH_SHORT).show()
            } else {
              Toast.makeText(requireContext(), getString(R.string.failed_to_clear_history), Toast.LENGTH_SHORT).show()
            }
          } catch (e: Exception) {
            Timber.e(e, "Failed to clear history")
            Toast.makeText(requireContext(), getString(R.string.failed_to_clear_history), Toast.LENGTH_SHORT).show()
          }
        }
      }
      .setNegativeButton(getString(R.string.cancel), null)
      .show()
  }

  companion object {
    const val TAG = "LibraryFragment"
  }

  // Allow external callers (e.g. FeedAction) to request adapter refresh
  fun refreshAdapter() {
    try {
      Timber.d("LibraryFragment.refreshAdapter called - submitting empty PagingData and refreshing")
      viewLifecycleOwner.lifecycleScope.launch {
        try {
          adapter.submitData(androidx.paging.PagingData.empty())
          adapter.refresh()
        } catch (t: Throwable) {
          Timber.w(t, "Failed to submit empty PagingData in LibraryFragment.refreshAdapter")
        }
      }
    } catch (_: Exception) {
      // best-effort, ignore if adapter not initialized
    }
  }

  // Allow external callers to invalidate PagingSource via ViewModel
  fun invalidatePaging() {
    try {
      viewModel.invalidatePaging()
    } catch (_: Exception) {
      // ignore
    }
  }
}
