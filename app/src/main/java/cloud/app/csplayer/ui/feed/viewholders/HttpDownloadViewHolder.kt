package cloud.app.csplayer.ui.feed.viewholders

import android.view.LayoutInflater
import android.view.ViewGroup
import cloud.app.csplayer.R
import cloud.app.csplayer.databinding.ItemHttpDownloadBinding
import cloud.app.csplayer.ui.feed.FeedData
import cloud.app.csplayer.ui.feed.FeedViewHolder
import cloud.app.csplayer.download.DownloadRepository
import cloud.app.csplayer.ui.feed.FeedAction
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * HttpDownloadViewHolder now subscribes to per-task updates from DownloadRepository
 * and updates its UI in real-time. onRecycled() cancels the subscription.
 */

class HttpDownloadViewHolder(
  val parent: ViewGroup,
  val clickListener: FeedAction,
  val downloadRepository: DownloadRepository,
  val binding: ItemHttpDownloadBinding = ItemHttpDownloadBinding.inflate(
    LayoutInflater.from(parent.context), parent, false
  )
) : FeedViewHolder<FeedData.HttpDownloadItem>(binding.root) {

  private var updateJob: Job? = null

  override fun bind(feed: FeedData.HttpDownloadItem) {
    // cancel any previous collector
    updateJob?.cancel()

    // initial snapshot bind
    binding.title.text = feed.title
    binding.txtFileName.text = feed.fileName

    // Set progress (ensure 0..100 int)
    val progressInt = feed.progress.coerceIn(0, 100)
    binding.progressBar.progress = progressInt

    // Set progress text with status
    val statusText =
      if (feed.isPaused) parent.context.getString(R.string.paused) else parent.context.getString(R.string.downloading)
    binding.txtProgress.text =
      parent.context.getString(R.string.download_progress_compact, progressInt, statusText)

    // Set action button icon based on pause state
    val actionIcon = if (feed.isPaused) {
      android.R.drawable.ic_media_play
    } else {
      android.R.drawable.ic_media_pause
    }
    binding.btnAction.setImageResource(actionIcon)
    binding.btnAction.contentDescription = if (feed.isPaused) {
      parent.context.getString(R.string.resume)
    } else {
      parent.context.getString(R.string.pause)
    }

    // Click listeners
    binding.root.setOnClickListener {
      clickListener.onItemClick(feed)
    }
    binding.root.setOnLongClickListener {
      clickListener.onItemLongClick(feed)
      true
    }
    binding.btnAction.setOnClickListener {
      // Propagate action via onItemClick for now (adapter/VM should distinguish by id)
      clickListener.onItemClick(feed)
    }

    // Start lifecycle-aware collection to receive realtime updates for this task
    updateJob = CoroutineScope(Dispatchers.Main).launch {
      downloadRepository.observeState(feed.id).collect { state ->
        if (state == null) return@collect
        val p = state.progress.coerceIn(0, 100)
        binding.progressBar.progress = p
        val paused = state.status.name == "PAUSED" || state.status.name == "CANCELED"
        val stText =
          if (paused) parent.context.getString(R.string.paused) else parent.context.getString(R.string.downloading)
        binding.txtProgress.text =
          parent.context.getString(R.string.download_progress_compact, p, stText)
        val icon =
          if (paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause
        binding.btnAction.setImageResource(icon)
      }
    }
  }

  @Suppress("unused")
  fun onRecycled() {
    updateJob?.cancel()
    updateJob = null
  }
}
