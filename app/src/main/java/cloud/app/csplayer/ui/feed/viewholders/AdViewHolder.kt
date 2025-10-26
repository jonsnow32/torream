package cloud.app.csplayer.ui.feed.viewholders

import android.view.LayoutInflater
import android.view.ViewGroup
import cloud.app.csplayer.databinding.ItemNativeAdSampleBinding
import cloud.app.csplayer.ui.feed.FeedData
import cloud.app.csplayer.ui.feed.FeedViewHolder

class AdViewHolder(
  parent: ViewGroup,
  val binding: ItemNativeAdSampleBinding = ItemNativeAdSampleBinding.inflate(
    LayoutInflater.from(parent.context), parent, false
  )
) : FeedViewHolder<FeedData.AdItem>(binding.root) {
  override fun bind(feed: FeedData.AdItem) {
    // No binding needed for static ad sample
  }
}
