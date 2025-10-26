package cloud.app.csplayer.ui.feed

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import cloud.app.csplayer.R
import cloud.app.csplayer.databinding.FragmentFeedBinding
import cloud.app.csplayer.ui.adapter.GridAdapter
import cloud.app.csplayer.ui.adapter.GridAdapter.Companion.configureGridLayout
import cloud.app.csplayer.ui.feed.FeedAdapter.Companion.getFeedAdapter
import cloud.app.csplayer.utils.AutoClearedValue.Companion.autoCleared
import cloud.app.csplayer.utils.observe
import dagger.hilt.android.AndroidEntryPoint


@AndroidEntryPoint
class FeedFragment : Fragment(), FeedClickListener {
  private var binding by autoCleared<FragmentFeedBinding>()
  private val viewModel: FeedViewModel by viewModels()
  private val adapter by lazy { getFeedAdapter(viewModel) }

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

    binding.toolbar.setTitle(R.string.app_name)
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
      GridAdapter.Concat(adapter, EmptyAdapter())
    )

    observe(viewModel.title) { title ->
      binding.root.contentDescription = title
    }

    binding.toolbar.setOnMenuItemClickListener {
      when (it.itemId) {
        R.id.displayType -> {

        }
        R.id.sort -> {
        }
      }
      true
    }
  }

  override fun onItemClick(item: FeedData) {
    TODO("Not yet implemented")
  }

}
