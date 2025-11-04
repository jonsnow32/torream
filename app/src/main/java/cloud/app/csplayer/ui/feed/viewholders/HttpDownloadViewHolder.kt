package cloud.app.csplayer.ui.feed.viewholders

import android.view.LayoutInflater
import android.view.ViewGroup
import cloud.app.csplayer.R
import cloud.app.csplayer.databinding.ItemHttpDownloadBinding
import cloud.app.csplayer.ui.feed.FeedClickListener
import cloud.app.csplayer.ui.feed.FeedData
import cloud.app.csplayer.ui.feed.FeedViewHolder

class HttpDownloadViewHolder(
    val parent: ViewGroup,
    val clickListener: FeedClickListener,
    val binding: ItemHttpDownloadBinding = ItemHttpDownloadBinding.inflate(
        LayoutInflater.from(parent.context), parent, false
    )
) : FeedViewHolder<FeedData.HttpDownloadItem>(binding.root) {

    override fun bind(feed: FeedData.HttpDownloadItem) {
        binding.title.text = feed.title
        binding.txtFileName.text = feed.fileName

        // Set progress
        binding.progressBar.progress = feed.progress

        // Set progress text with status
        val statusText = if (feed.isPaused) "Paused" else "Downloading"
        binding.txtProgress.text = "${feed.progress}% • $statusText"

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

        binding.btnAction.setOnClickListener {
            // TODO: Handle pause/resume action
            // clickListener.onDownloadActionClick(feed)
        }
    }
}

