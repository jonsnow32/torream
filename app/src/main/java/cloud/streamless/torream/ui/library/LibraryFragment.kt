package cloud.streamless.torream.ui.library


import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import cloud.streamless.torream.MainActivityViewModel.Companion.applyContentRect
import cloud.streamless.torream.R
import cloud.streamless.torream.ads.AdManager
import cloud.streamless.torream.databinding.FragmentLibraryBinding
import cloud.streamless.torream.download.DownloadCoordinator
import cloud.streamless.torream.download.DownloadRepository
import cloud.streamless.torream.ui.adapter.GridAdapter.Companion.configureGridLayout
import cloud.streamless.torream.ui.feed.FeedAction
import cloud.streamless.torream.ui.feed.adapters.FeedAdapter
import cloud.streamless.torream.utils.AutoClearedValue.Companion.autoCleared
import cloud.streamless.torream.utils.FastScrollerHelper
import cloud.streamless.torream.utils.observe
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
  lateinit var mediaRepository: cloud.streamless.torream.media.repository.MediaRepository

  @Inject
  lateinit var playlistRepository: cloud.streamless.torream.media.repository.PlaylistRepository

  @Inject
  lateinit var favoriteRepository: cloud.streamless.torream.favorites.FavoriteRepository

  private lateinit var adapter: FeedAdapter

  private var isPrivateUnlocked = false
  private var lastCheckedTabId = R.id.tabHistory
  private var isHandlingPrivateAuth = false

  private val pinPrefKey = "private_folder_pin"

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
    when (viewModel.section.value) {
      LibrarySection.DOWNLOADS -> { binding.tabDownloads.isChecked = true; lastCheckedTabId = R.id.tabDownloads }
      LibrarySection.FAVORITES -> { binding.tabFavorites.isChecked = true; lastCheckedTabId = R.id.tabFavorites }
      LibrarySection.HISTORY   -> { binding.tabHistory.isChecked = true; lastCheckedTabId = R.id.tabHistory }
      LibrarySection.PLAYLISTS -> { binding.tabPlaylists.isChecked = true; lastCheckedTabId = R.id.tabPlaylists }
      LibrarySection.PRIVATE   -> { binding.tabPrivate.isChecked = true; lastCheckedTabId = R.id.tabPrivate }
    }

    binding.tabLayout.setOnCheckedChangeListener { group, checkedId ->
      if (isHandlingPrivateAuth) return@setOnCheckedChangeListener

      if (checkedId == R.id.tabPrivate && !isPrivateUnlocked) {
        isHandlingPrivateAuth = true
        val storedPin = viewModel.sharedPreferences.getString(pinPrefKey, null)
        if (storedPin == null) {
          showSetPinDialog(
            onSet = {
              isPrivateUnlocked = true
              lastCheckedTabId = R.id.tabPrivate
              commitSection(LibrarySection.PRIVATE)
              isHandlingPrivateAuth = false
            },
            onCancel = {
              group.check(lastCheckedTabId)
              isHandlingPrivateAuth = false
            }
          )
        } else {
          showEnterPinDialog(
            onCorrect = {
              isPrivateUnlocked = true
              lastCheckedTabId = R.id.tabPrivate
              commitSection(LibrarySection.PRIVATE)
              isHandlingPrivateAuth = false
            },
            onWrong = {
              group.check(lastCheckedTabId)
              isHandlingPrivateAuth = false
              Toast.makeText(requireContext(), getString(R.string.private_folder_wrong_pin), Toast.LENGTH_SHORT).show()
            },
            onCancel = {
              group.check(lastCheckedTabId)
              isHandlingPrivateAuth = false
            }
          )
        }
        return@setOnCheckedChangeListener
      }

      lastCheckedTabId = checkedId
      when (checkedId) {
        R.id.tabDownloads -> commitSection(LibrarySection.DOWNLOADS)
        R.id.tabFavorites -> commitSection(LibrarySection.FAVORITES)
        R.id.tabHistory   -> commitSection(LibrarySection.HISTORY)
        R.id.tabPlaylists -> commitSection(LibrarySection.PLAYLISTS)
        R.id.tabPrivate   -> commitSection(LibrarySection.PRIVATE)
      }
    }
  }

  private fun commitSection(section: LibrarySection) {
    viewModel.section.value = section
    Timber.d("Tab selected: $section")
    updateStorageStatsVisibility()
  }

  private fun showSetPinDialog(onSet: () -> Unit, onCancel: () -> Unit) {
    val input = EditText(requireContext()).apply {
      inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
      hint = getString(R.string.private_folder_pin_hint)
    }
    AlertDialog.Builder(requireContext(), R.style.BaseMaterialDialogTheme)
      .setTitle(getString(R.string.private_folder_set_pin_title))
      .setMessage(getString(R.string.private_folder_set_pin_message))
      .setView(input)
      .setPositiveButton(getString(R.string.set_pin)) { _, _ ->
        val pin = input.text.toString()
        if (pin.length >= 4) {
          viewModel.sharedPreferences.edit().putString(pinPrefKey, pin).apply()
          onSet()
        } else {
          Toast.makeText(requireContext(), getString(R.string.private_folder_pin_too_short), Toast.LENGTH_SHORT).show()
          onCancel()
        }
      }
      .setNegativeButton(getString(R.string.cancel)) { _, _ -> onCancel() }
      .setOnCancelListener { onCancel() }
      .show()
  }

  private fun showEnterPinDialog(onCorrect: () -> Unit, onWrong: () -> Unit, onCancel: () -> Unit) {
    val input = EditText(requireContext()).apply {
      inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
      hint = getString(R.string.private_folder_pin_hint)
    }
    AlertDialog.Builder(requireContext(), R.style.BaseMaterialDialogTheme)
      .setTitle(getString(R.string.private_folder_enter_pin_title))
      .setView(input)
      .setPositiveButton(getString(R.string.confirm)) { _, _ ->
        val stored = viewModel.sharedPreferences.getString(pinPrefKey, null)
        if (input.text.toString() == stored) onCorrect() else onWrong()
      }
      .setNegativeButton(getString(R.string.cancel)) { _, _ -> onCancel() }
      .setNeutralButton(getString(R.string.private_folder_change_pin)) { _, _ ->
        isHandlingPrivateAuth = false
        showSetPinDialog(
          onSet = {
            isPrivateUnlocked = true
            lastCheckedTabId = R.id.tabPrivate
            commitSection(LibrarySection.PRIVATE)
          },
          onCancel = { binding.tabLayout.check(lastCheckedTabId) }
        )
      }
      .setOnCancelListener { onCancel() }
      .show()
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
    binding.storageStatsView.visibility =
      if (viewModel.section.value == LibrarySection.DOWNLOADS) View.VISIBLE else View.GONE
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
