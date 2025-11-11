package cloud.app.csplayer.ui.library

import adapters.FeedAdapter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import cloud.app.csplayer.MainActivityViewModel.Companion.applyContentRect
import cloud.app.csplayer.R
import cloud.app.csplayer.ads.AdManager
import cloud.app.csplayer.databinding.FragmentLibraryBinding
import cloud.app.csplayer.model.PlaybackData
import cloud.app.csplayer.ui.adapter.GridAdapter.Companion.configureGridLayout
import cloud.app.csplayer.ui.feed.FeedClickListener
import cloud.app.csplayer.ui.feed.FeedData
import cloud.app.csplayer.utils.AutoClearedValue.Companion.autoCleared
import cloud.app.csplayer.utils.FastScrollerHelper
import cloud.app.csplayer.utils.PlaybackDataHelper
import cloud.app.csplayer.utils.UIHelper.navigate
import cloud.app.csplayer.utils.Utils.showToast
import cloud.app.csplayer.utils.observe
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
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
class LibraryFragment : Fragment(), FeedClickListener {
  private var binding by autoCleared<FragmentLibraryBinding>()
  private val viewModel: LibraryViewModel by viewModels()

  @Inject
  lateinit var adManager: AdManager

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
    adapter = FeedAdapter(this as FeedClickListener, adManager, viewModel.filterConfig.value)

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

  // FeedClickListener implementation
  override fun onItemClick(item: FeedData) {
    when (item) {
      is FeedData.MediaItem -> {
        Timber.d("Media clicked: ${item.media.name}")

        // Create PlaybackData from Media
        val playbackData = PlaybackData(
          title = item.media.name,
          videoLinks = listOf(
            cloud.app.csplayer.model.VideoLink(
              url = item.media.uri,
              name = item.media.name,
              headers = emptyMap(),
              subtitles = emptyList(),
              width = item.media.width,
              height = item.media.height
            )
          ),
          subtitles = emptyList(),
          videoStartIndex = 0,
          subtitleStartIndex = 0,
          isSameEpisode = false,
          hasAd = false
        )

        // Navigate to player using Activity.navigate
        val bundle = PlaybackDataHelper.createBundle(playbackData)
        activity?.navigate(R.id.global_to_navigation_mpv_player, bundle)
      }

      is FeedData.FolderItem -> {
        Timber.d("Folder clicked: ${item.folder.name}")

        // Navigate to FeedFragment with folder path
        val bundle = Bundle().apply {
          putString("root_folder_path", item.folder.path)
        }
        findNavController().navigate(R.id.feedFragment, bundle)
      }

      is FeedData.TorrentDownloadItem -> {
        Timber.d("Torrent download clicked: ${item.torrentState.name}")

        // Show torrent details or open torrent manager
        showToast("Torrent: ${item.torrentState.name} - ${item.torrentState.progress}%")

        // TODO: Navigate to torrent details screen
      }

      is FeedData.HttpDownloadItem -> {
        Timber.d("HTTP download clicked: ${item.fileName}")
        showToast("HTTP download: ${item.fileName}")

        // TODO: Navigate to download manager
      }

      is FeedData.AdItem -> {
        Timber.d("Ad clicked: ${item.title}")
        // TODO: Handle ad click
      }

      is FeedData.HorizontalList -> {
        // Horizontal lists don't have individual click action
      }
    }
  }

  companion object {
    const val TAG = "LibraryFragment"
  }
}

