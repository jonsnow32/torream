package cloud.app.csplayer.ui.feed.viewholders

import android.view.LayoutInflater
import android.view.ViewGroup
import cloud.app.csplayer.R
import cloud.app.csplayer.databinding.ItemHttpDownloadBinding
import cloud.app.csplayer.ui.feed.FeedData
import cloud.app.csplayer.ui.feed.FeedViewHolder
import cloud.app.csplayer.download.DownloadRepository
import cloud.app.csplayer.download.DownloadStatus
import cloud.app.csplayer.ui.feed.FeedAction

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
    binding.txtFileName.text = feed.fileName
    updateUI(feed.progress, feed.status)

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

  private fun updateUI(progress: Int, status: DownloadStatus) {
    val p = progress.coerceIn(0, 100)
    binding.progressBar.progress = p


    val statusText = when (status) {
      DownloadStatus.PAUSED -> parent.context.getString(R.string.paused)
      DownloadStatus.COMPLETED,
      DownloadStatus.FINISHED -> parent.context.getString(R.string.finished)

      DownloadStatus.FAILED -> parent.context.getString(R.string.error)
      DownloadStatus.QUEUED,
      DownloadStatus.SEEDING,
      DownloadStatus.DOWNLOADING -> parent.context.getString(R.string.downloading)

      DownloadStatus.CANCELED -> parent.context.getString(R.string.cancel)
    }


    binding.txtProgress.text = parent.context.getString(
      R.string.download_progress_compact,
      p,
      statusText
    )

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
}
