package com.tv.apps.zippy.ui.library


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.tv.apps.zippy.MainActivityViewModel.Companion.applyContentRect
import com.tv.apps.zippy.R
import com.tv.apps.zippy.ads.AdManager
import com.tv.apps.zippy.databinding.FragmentLibraryBinding
import com.tv.apps.zippy.download.DownloadRepository
import com.tv.apps.zippy.download.DownloadCoordinator
import com.tv.apps.zippy.ui.adapter.GridAdapter.Companion.configureGridLayout
import com.tv.apps.zippy.ui.feed.FeedAction
import com.tv.apps.zippy.ui.feed.adapters.FeedAdapter
import com.tv.apps.zippy.utils.AutoClearedValue.Companion.autoCleared
import com.tv.apps.zippy.utils.FastScrollerHelper
import com.tv.apps.zippy.utils.observe
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
  lateinit var mediaRepository: com.tv.apps.zippy.media.repository.MediaRepository

  @Inject
  lateinit var playlistRepository: com.tv.apps.zippy.media.repository.PlaylistRepository

  @Inject
  lateinit var favoriteRepository: com.tv.apps.zippy.favorites.FavoriteRepository

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
    applyContentRect(binding.appBar, binding.rvLibrary)
    FastScrollerHelper.applyTo(binding.rvLibrary)

    // Handle section argument if provided
    arguments?.getInt("section", -1)?.let { sectionOrdinal ->
      if (sectionOrdinal >= 0) {
        viewModel.section.value = LibrarySection.entries[sectionOrdinal]
      }
    }

    setupUI()
    setupAdapter()
    setupStorageStats()

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
    // Select the appropriate radio button based on current section
    when (viewModel.section.value) {
      LibrarySection.DOWNLOADS -> binding.tabDownloads.isChecked = true
      LibrarySection.FAVORITES -> binding.tabFavorites.isChecked = true
      LibrarySection.HISTORY -> binding.tabHistory.isChecked = true
      LibrarySection.PLAYLISTS -> binding.tabPlaylists.isChecked = true
    }

    // Handle radio button selection changes
    binding.tabLayout.setOnCheckedChangeListener { _, checkedId ->
      when (checkedId) {
        R.id.tabDownloads -> viewModel.section.value = LibrarySection.DOWNLOADS
        R.id.tabFavorites -> viewModel.section.value = LibrarySection.FAVORITES
        R.id.tabHistory -> viewModel.section.value = LibrarySection.HISTORY
        R.id.tabPlaylists -> viewModel.section.value = LibrarySection.PLAYLISTS
      }
      Timber.d("Tab selected: ${viewModel.section.value}")
      // Update storage stats visibility
      updateStorageStatsVisibility()
    }
  }

  private fun setupStorageStats() {
    // Initial update
    updateStorageStatsVisibility()

    // Update storage stats when on Downloads tab
    if (viewModel.section.value == LibrarySection.DOWNLOADS) {
      updateStorageStatsUI()
    }

    // Observe section changes
    observe(viewModel.section) { section ->
      updateStorageStatsVisibility()
      if (section == LibrarySection.DOWNLOADS) {
        updateStorageStatsUI()
      }
    }
  }

  private fun updateStorageStatsVisibility() {
    // Show storage stats only on Downloads tab
    binding.storageStatsView.visibility = if (viewModel.section.value == LibrarySection.DOWNLOADS) {
      View.VISIBLE
    } else {
      View.GONE
    }
  }

  private fun updateStorageStatsUI() {
    try {
      binding.storageStatsView.loadAndDisplay()
    } catch (e: Exception) {
      Timber.e(e, "Failed to update storage stats UI")
    }
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
