package cloud.app.csplayer.ui.feed.viewholders

import android.view.LayoutInflater
import android.view.ViewGroup
import cloud.app.csplayer.R
import cloud.app.csplayer.databinding.ItemTorrentDownloadBinding
import cloud.app.csplayer.model.TorrentDownloadStatus
import cloud.app.csplayer.ui.feed.FeedData
import cloud.app.csplayer.ui.feed.FeedViewHolder
import cloud.app.csplayer.download.DownloadRepository
import cloud.app.csplayer.ui.feed.FeedAction

class TorrentDownloadViewHolder(
  val parent: ViewGroup,
  val clickListener: FeedAction,
  val downloadRepository: DownloadRepository,
  val binding: ItemTorrentDownloadBinding = ItemTorrentDownloadBinding.inflate(
    LayoutInflater.from(parent.context), parent, false
  )
) : FeedViewHolder<FeedData.TorrentDownloadItem>(binding.root) {

  override fun bind(feed: FeedData.TorrentDownloadItem) {
    // Bind data from feed (already updated by ViewModel)
    binding.title.text = feed.title
    binding.txtTorrentName.text = feed.torrentState.name

    val progressInt = (feed.torrentState.progress * 100f).toInt().coerceIn(0, 100)
    updateUI(progressInt, feed.torrentState.status, feed.torrentState.numSeeds, feed.torrentState.numPeers)

    // Click listeners
    binding.root.setOnClickListener {
      clickListener.onItemClick(feed)
    }
    binding.root.setOnLongClickListener {
      clickListener.onItemLongClick(feed)
      true
    }
    binding.btnAction.setOnClickListener {
      clickListener.onItemClick(feed)
    }
  }

  private fun updateUI(progress: Int, status: TorrentDownloadStatus, seeds: Int, peers: Int) {
    binding.progressBar.progress = progress

    val statusText = when (status) {
      TorrentDownloadStatus.PAUSED -> parent.context.getString(R.string.paused)
      TorrentDownloadStatus.SEEDING -> parent.context.getString(R.string.seeding)
      TorrentDownloadStatus.FINISHED -> parent.context.getString(R.string.finished)
      TorrentDownloadStatus.ERROR -> parent.context.getString(R.string.error)
      else -> parent.context.getString(R.string.downloading)
    }

    binding.txtProgress.text = parent.context.getString(
      R.string.torrent_progress_with_peers,
      progress,
      statusText,
      seeds,
      peers
    )

    val actionIcon = if (status == TorrentDownloadStatus.PAUSED) {
      android.R.drawable.ic_media_play
    } else {
      android.R.drawable.ic_media_pause
    }
    binding.btnAction.setImageResource(actionIcon)
    binding.btnAction.contentDescription = if (status == TorrentDownloadStatus.PAUSED) {
      parent.context.getString(R.string.resume)
    } else {
      parent.context.getString(R.string.pause)
    }
  }
}
