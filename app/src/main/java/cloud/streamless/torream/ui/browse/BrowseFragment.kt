package cloud.streamless.torream.ui.browse

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import cloud.streamless.torream.MainActivityViewModel.Companion.applyContentRect
import cloud.streamless.torream.R
import cloud.streamless.torream.databinding.FragmentBrowseBinding
import cloud.streamless.torream.model.NetworkShare
import cloud.streamless.torream.model.PlaybackData
import cloud.streamless.torream.ui.browse.network.BrowseEntry
import cloud.streamless.torream.ui.dialog.AddNetworkShareDialog
import cloud.streamless.torream.utils.PlaybackDataHelper
import cloud.streamless.torream.utils.UIHelper.navigate
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BrowseFragment : Fragment() {
  lateinit var binding: FragmentBrowseBinding
  private val viewModel: BrowseViewModel by viewModels()

  private lateinit var shareAdapter: NetworkShareAdapter
  private lateinit var entryAdapter: BrowseEntryAdapter
  private val backPressedCallback = object : OnBackPressedCallback(false) {
    override fun handleOnBackPressed() {
      viewModel.navigateUp()
    }
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    binding = FragmentBrowseBinding.inflate(layoutInflater)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    applyContentRect(binding.appBar, binding.swipeRefresh)

    binding.toolbar.inflateMenu(R.menu.browse_menu)
    binding.toolbar.setOnMenuItemClickListener { item ->
      if (item.itemId == R.id.addNetworkShare) {
        AddNetworkShareDialog().show(childFragmentManager)
        true
      } else {
        false
      }
    }
    binding.toolbar.navigationIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_baseline_arrow_back_24)
    binding.toolbar.setNavigationOnClickListener {
      if (viewModel.currentShare.value != null) {
        viewModel.navigateUp()
      } else {
        requireActivity().onBackPressedDispatcher.onBackPressed()
      }
    }

    shareAdapter = NetworkShareAdapter(
      onClick = { share -> viewModel.openShare(share) },
      onDelete = { share -> viewModel.deleteShare(share) }
    )
    entryAdapter = BrowseEntryAdapter(onClick = { entry -> onEntryClicked(entry) })

    binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())

    binding.swipeRefresh.setOnRefreshListener {
      binding.swipeRefresh.isRefreshing = false
    }

    requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback)

    observeState()
  }

  private fun onEntryClicked(entry: BrowseEntry) {
    if (entry.isDirectory) {
      viewModel.openDirectory(entry)
      return
    }
    val videoLink = viewModel.buildVideoLink(entry) ?: return
    val playbackData = PlaybackData(
      title = entry.name,
      videoLinks = listOf(videoLink),
      subtitles = emptyList(),
      videoStartIndex = 0,
      subtitleStartIndex = 0,
      isSameEpisode = true,
      hasAd = false
    )
    val bundle = PlaybackDataHelper.createBundle(playbackData)
    requireActivity().navigate(R.id.global_to_navigation_mpv_player, bundle)
  }

  private fun observeState() {
    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.shares.collect { shares ->
        shareAdapter.submitList(shares)
        if (viewModel.currentShare.value == null) {
          renderShareList(shares)
        }
      }
    }
    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.currentShare.collect { share ->
        backPressedCallback.isEnabled = share != null
        if (share == null) {
          binding.recyclerView.adapter = shareAdapter
          binding.toolbar.title = getString(R.string.browse)
          renderShareList(viewModel.shares.value)
        } else {
          binding.recyclerView.adapter = entryAdapter
          binding.toolbar.title = share.displayName
        }
      }
    }
    viewLifecycleOwner.lifecycleScope.launch {
      combine(
        viewModel.currentShare,
        viewModel.entries,
        viewModel.error,
        viewModel.isLoading
      ) { share, entries, error, loading ->
        entryAdapter.submitList(entries)
        if (share != null) {
          when {
            error != null -> {
              binding.emptyText.isVisible = true
              binding.emptyText.text = error
            }
            !loading && entries.isEmpty() -> {
              binding.emptyText.isVisible = true
              binding.emptyText.text = getString(R.string.network_share_empty)
            }
            else -> binding.emptyText.isVisible = false
          }
        }
      }.collect {}
    }
    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.isLoading.collect { loading -> binding.swipeRefresh.isRefreshing = loading }
    }
  }

  private fun renderShareList(shares: List<NetworkShare>) {
    binding.emptyText.isVisible = shares.isEmpty()
    if (shares.isEmpty()) binding.emptyText.text = getString(R.string.network_shares_empty)
  }
}
