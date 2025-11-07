package cloud.app.csplayer.ui.feed.viewholders

import android.view.LayoutInflater
import android.view.ViewGroup
import cloud.app.csplayer.R
import cloud.app.csplayer.databinding.ItemTorrentDownloadBinding
import cloud.app.csplayer.model.TorrentDownloadStatus
import cloud.app.csplayer.ui.feed.FeedClickListener
import cloud.app.csplayer.ui.feed.FeedData
import cloud.app.csplayer.ui.feed.FeedViewHolder

class TorrentDownloadViewHolder(
    val parent: ViewGroup,
    val clickListener: FeedClickListener,
    val binding: ItemTorrentDownloadBinding = ItemTorrentDownloadBinding.inflate(
        LayoutInflater.from(parent.context), parent, false
    )
) : FeedViewHolder<FeedData.TorrentDownloadItem>(binding.root) {

    override fun bind(feed: FeedData.TorrentDownloadItem) {
        binding.title.text = feed.title
        binding.txtTorrentName.text = feed.torrentState.name

        // Set progress
        binding.progressBar.progress = feed.torrentState.progress.toInt()

        // Set progress text with status
        val statusText = if (feed.torrentState.status == TorrentDownloadStatus.PAUSED) "Paused" else "Downloading"
        binding.txtProgress.text = "${feed.torrentState.progress.toInt()}% • $statusText"

        binding.txtProgress.text = "${feed.torrentState.progress}% • $statusText • ${feed.torrentState.numSeeds} seeds, ${feed.torrentState.numPeers} peers"

        // Set action button icon based on pause state
        val actionIcon = if (feed.torrentState.status == TorrentDownloadStatus.PAUSED) {
            android.R.drawable.ic_media_play
        } else {
            android.R.drawable.ic_media_pause
        }
        binding.btnAction.setImageResource(actionIcon)
        binding.btnAction.contentDescription = if (feed.torrentState.status == TorrentDownloadStatus.PAUSED) {
            parent.context.getString(R.string.resume)
        } else {
            parent.context.getString(R.string.pause)
        }

        // Click listeners
        binding.root.setOnClickListener {
            clickListener.onItemClick(feed)
        }

        binding.btnAction.setOnClickListener {
            // TODO: Handle pause/resume action for torrent
            // clickListener.onTorrentActionClick(feed)
        }
    }
}

