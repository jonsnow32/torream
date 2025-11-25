package cloud.app.csplayer.ui.feed.viewholders

import android.view.LayoutInflater
import android.view.ViewGroup
import cloud.app.csplayer.R
import cloud.app.csplayer.databinding.ItemTorrentDownloadBinding
import cloud.app.csplayer.download.DownloadRepository
import cloud.app.csplayer.download.DownloadStatus
import cloud.app.csplayer.ui.feed.FeedData
import cloud.app.csplayer.ui.feed.FeedViewHolder
import cloud.app.csplayer.ui.feed.FeedAction
import cloud.app.csplayer.utils.loadThumbnail

class TorrentDownloadViewHolder(
  val parent: ViewGroup,
  val clickListener: FeedAction,
  val downloadRepository: DownloadRepository,
  val binding: ItemTorrentDownloadBinding = ItemTorrentDownloadBinding.inflate(
    LayoutInflater.from(parent.context), parent, false
  )
) : FeedViewHolder<FeedData.TorrentDownloadItem>(binding.root) {

  override fun bind(feed: FeedData.TorrentDownloadItem) {
    // Bind data from feed (already updated by LibraryViewModel which observes DownloadState)
    binding.title.text = feed.title


    val progress = feed.downloadState.progress.coerceIn(0, 100)
    updateUI(feed, progress, feed.downloadState.status, feed.downloadState.numSeeds, feed.downloadState.numPeers, feed.downloadState.speed)

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

  private fun updateUI(feed: FeedData.TorrentDownloadItem, progress: Int, status: DownloadStatus, seeds: Int, peers: Int, speed: Long) {
    binding.progressBar.progress = progress

    val statusText = when (status) {
      DownloadStatus.PAUSED -> parent.context.getString(R.string.paused)
      DownloadStatus.SEEDING -> parent.context.getString(R.string.seeding)
      DownloadStatus.FINISHED, DownloadStatus.COMPLETED -> {
        // Load thumbnail from downloaded file
        feed.downloadState.task.downloadedFilePath?.let { filePath ->
          binding.thumbnail.loadThumbnail(filePath)
        }
        parent.context.getString(R.string.finished)
      }
      DownloadStatus.FAILED -> parent.context.getString(R.string.error)
      else -> parent.context.getString(R.string.downloading)
    }

    // Build progress text with size info
    val sizeText = if (feed.downloadState.totalBytes > 0) {
      "${formatFileSize(feed.downloadState.downloadedBytes)} / ${formatFileSize(feed.downloadState.totalBytes)}"
    } else {
      formatFileSize(feed.downloadState.downloadedBytes)
    }

    binding.txtProgress.text = parent.context.getString(
      R.string.torrent_progress_with_peers,
      progress,
      statusText,
      seeds,
      peers
    ) + " • ${formatSpeed(speed)} • $sizeText"

    val actionIcon = if (status == DownloadStatus.PAUSED) {
      android.R.drawable.ic_media_play
    } else {
      android.R.drawable.ic_media_pause
    }
    binding.btnAction.setImageResource(actionIcon)
    binding.btnAction.contentDescription = if (status == DownloadStatus.PAUSED) {
      parent.context.getString(R.string.resume)
    } else {
      parent.context.getString(R.string.pause)
    }
  }

  private fun formatSpeed(bytesPerSecond: Long): String {
    return when {
      bytesPerSecond <= 0 -> "0 B/s"
      bytesPerSecond < 1024 -> "$bytesPerSecond B/s"
      bytesPerSecond < 1024 * 1024 -> "${bytesPerSecond / 1024} KB/s"
      else -> "${String.format("%.1f", bytesPerSecond / (1024.0 * 1024.0))} MB/s"
    }
  }

  private fun formatFileSize(bytes: Long): String {
    return when {
      bytes <= 0 -> "0 B"
      bytes < 1024 -> "$bytes B"
      bytes < 1024 * 1024 -> "${String.format("%.1f", bytes / 1024.0)} KB"
      bytes < 1024 * 1024 * 1024 -> "${String.format("%.1f", bytes / (1024.0 * 1024.0))} MB"
      else -> "${String.format("%.2f", bytes / (1024.0 * 1024.0 * 1024.0))} GB"
    }
  }
}
