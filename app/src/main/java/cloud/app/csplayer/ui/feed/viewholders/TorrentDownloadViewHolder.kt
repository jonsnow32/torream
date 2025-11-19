package cloud.app.csplayer.ui.feed.viewholders

import android.view.LayoutInflater
import android.view.ViewGroup
import cloud.app.csplayer.R
import cloud.app.csplayer.databinding.ItemTorrentDownloadBinding
import cloud.app.csplayer.model.TorrentDownloadStatus
import cloud.app.csplayer.ui.feed.FeedData
import cloud.app.csplayer.ui.feed.FeedViewHolder
import cloud.app.csplayer.download.DownloadRepository
import cloud.app.csplayer.download.DownloadStatus
import cloud.app.csplayer.ui.feed.FeedAction
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class TorrentDownloadViewHolder(
  val parent: ViewGroup,
  val clickListener: FeedAction,
  val downloadRepository: DownloadRepository,
  val binding: ItemTorrentDownloadBinding = ItemTorrentDownloadBinding.inflate(
    LayoutInflater.from(parent.context), parent, false
  )
) : FeedViewHolder<FeedData.TorrentDownloadItem>(binding.root) {

  private var updateJob: Job? = null

  override fun bind(feed: FeedData.TorrentDownloadItem) {
    updateJob?.cancel()
    binding.title.text = feed.title
    binding.txtTorrentName.text = feed.torrentState.name

    // Set progress (convert float to int percent)
    val progressInt = (feed.torrentState.progress * 100f).toInt().coerceIn(0, 100)
    binding.progressBar.progress = progressInt

    // Set progress text with status and peers/seeds
    val statusText = when (feed.torrentState.status) {
      TorrentDownloadStatus.PAUSED -> parent.context.getString(R.string.paused)
      TorrentDownloadStatus.SEEDING -> parent.context.getString(R.string.seeding)
      TorrentDownloadStatus.FINISHED -> parent.context.getString(R.string.finished)
      TorrentDownloadStatus.ERROR -> parent.context.getString(R.string.error)
      else -> parent.context.getString(R.string.downloading)
    }

    // Use localized string resource for progress to satisfy lint (avoid hard-coded formatting)
    binding.txtProgress.text = parent.context.getString(
      R.string.torrent_progress_with_peers,
      progressInt,
      statusText,
      feed.torrentState.numSeeds,
      feed.torrentState.numPeers
    )

    // Set action button icon based on pause state
    val actionIcon = if (feed.torrentState.status == TorrentDownloadStatus.PAUSED) {
      android.R.drawable.ic_media_play
    } else {
      android.R.drawable.ic_media_pause
    }
    binding.btnAction.setImageResource(actionIcon)
    binding.btnAction.contentDescription =
      if (feed.torrentState.status == TorrentDownloadStatus.PAUSED) {
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

    // Subscribe to per-task updates using a local CoroutineScope to avoid lifecycle-type mismatch in static analysis
    updateJob = CoroutineScope(Dispatchers.Main).launch {
      downloadRepository.observeState(feed.id).collect { state ->
        if (state == null) return@collect
        val p = state.progress.coerceIn(0, 100)
        binding.progressBar.progress = p
        val status = when (state.status) {
          DownloadStatus.DOWNLOADING -> TorrentDownloadStatus.DOWNLOADING
          DownloadStatus.PAUSED -> TorrentDownloadStatus.PAUSED
          DownloadStatus.SEEDING -> TorrentDownloadStatus.SEEDING
          DownloadStatus.FINISHED -> TorrentDownloadStatus.FINISHED
          else -> TorrentDownloadStatus.ERROR
        }
        val statusText2 = when (status) {
          TorrentDownloadStatus.PAUSED -> parent.context.getString(R.string.paused)
          TorrentDownloadStatus.SEEDING -> parent.context.getString(R.string.seeding)
          TorrentDownloadStatus.FINISHED -> parent.context.getString(R.string.finished)
          TorrentDownloadStatus.ERROR -> parent.context.getString(R.string.error)
          else -> parent.context.getString(R.string.downloading)
        }
        binding.txtProgress.text = parent.context.getString(
          R.string.torrent_progress_with_peers,
          p,
          statusText2,
          state.downloadSpeedBytesPerSec,
          0
        )
        val actionIcon2 =
          if (status == TorrentDownloadStatus.PAUSED) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause
        binding.btnAction.setImageResource(actionIcon2)
      }
    }
  }

  @Suppress("unused")
  fun onRecycled() {
    updateJob?.cancel()
    updateJob = null
  }
}
