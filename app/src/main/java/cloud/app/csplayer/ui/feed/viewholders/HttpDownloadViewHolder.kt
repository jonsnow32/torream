package cloud.app.csplayer.ui.feed.viewholders

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import cloud.app.csplayer.R
import cloud.app.csplayer.databinding.ItemHttpDownloadBinding
import cloud.app.csplayer.ui.feed.FeedData
import cloud.app.csplayer.ui.feed.FeedViewHolder
import cloud.app.csplayer.download.DownloadRepository
import cloud.app.csplayer.download.DownloadState
import cloud.app.csplayer.download.DownloadStatus
import cloud.app.csplayer.ui.feed.FeedAction
import cloud.app.csplayer.utils.UnifiedFileFactory
import cloud.app.csplayer.utils.loadThumbnail

class HttpDownloadViewHolder(
  val parent: ViewGroup,
  val clickListener: FeedAction,
  val downloadRepository: DownloadRepository,
  val binding: ItemHttpDownloadBinding = ItemHttpDownloadBinding.inflate(
    LayoutInflater.from(parent.context), parent, false
  )
) : FeedViewHolder<FeedData.HttpDownloadItem>(binding.root) {

  override fun bind(feed: FeedData.HttpDownloadItem) {
    // Bind data from feed (already updated by ViewModel)
    binding.title.text = feed.title
    updateUI(feed)
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

  private fun updateUI(feed: FeedData.HttpDownloadItem) {
    val state = feed.downloadState
    val progress = state.progress.coerceIn(0, 100)
    binding.progressBar.progress = progress

    val sizeText = formatFileSize(state.downloadedBytes)
    val speedText = formatSpeed(state.speed)
    val isDownloading = state.status == DownloadStatus.DOWNLOADING

    binding.txtProgress.text = parent.context.getString(
      R.string.download_progress_compact,
      progress,
      getStatusString(state.status)
    ) + if (isDownloading) " • $speedText • $sizeText" else " • $sizeText"

    updateButtonState(state.status, progress)
    updateFinishedUI(state)
  }

  private fun getStatusString(status: DownloadStatus): String {
    return parent.context.getString(when (status) {
      DownloadStatus.PAUSED -> R.string.paused
      DownloadStatus.COMPLETED, DownloadStatus.FINISHED -> R.string.finished
      DownloadStatus.FAILED -> R.string.error
      DownloadStatus.QUEUED, DownloadStatus.SEEDING, DownloadStatus.DOWNLOADING -> R.string.downloading
      DownloadStatus.CANCELED -> R.string.cancel
    })
  }

  private fun updateButtonState(status: DownloadStatus, progress: Int) {
    val (state, description) = when (status) {
      DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED, DownloadStatus.SEEDING ->
        "downloading" to parent.context.getString(R.string.pause)
      DownloadStatus.PAUSED ->
        "paused" to parent.context.getString(R.string.resume)
      DownloadStatus.COMPLETED, DownloadStatus.FINISHED ->
        "completed" to parent.context.getString(R.string.finished)
      DownloadStatus.FAILED ->
        "error" to parent.context.getString(R.string.error)
      DownloadStatus.CANCELED ->
        "idle" to parent.context.getString(R.string.cancel)
    }

    binding.btnAction.apply {
      when (state) {
        "downloading" -> setDownloading(progress.toFloat())
        "paused" -> setPaused()
        "completed" -> setCompleted()
        "error" -> setError()
        "idle" -> setIdle()
      }
      contentDescription = description
    }
  }

  private fun updateFinishedUI(state: DownloadState) {
    if (state.status in setOf(DownloadStatus.COMPLETED, DownloadStatus.FINISHED)) {
      state.task.targetPath.let { binding.thumbnail.loadThumbnail(it) }
      binding.apply {
        txtSavePath.text = formatSavePath(state.task.targetPath)
        txtSavePath.visibility = View.VISIBLE
      }
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

  private fun formatSavePath(targetPath: String): String {
    val file = UnifiedFileFactory.fromUri(binding.root.context, targetPath.toUri())
    return file?.filePath ?: targetPath
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
