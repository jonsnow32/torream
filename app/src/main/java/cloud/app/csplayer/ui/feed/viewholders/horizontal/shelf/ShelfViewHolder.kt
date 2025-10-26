package cloud.app.csplayer.ui.feed.viewholders.horizontal.shelf

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import cloud.app.csplayer.databinding.ItemShelfListsAudioThreeBinding
import cloud.app.csplayer.databinding.ItemShelfListsVideoTwoBinding
import cloud.app.csplayer.ui.feed.FeedClickListener
import cloud.app.csplayer.ui.feed.FeedData.AudioItem.Companion.bind
import cloud.app.csplayer.ui.feed.FeedData.VideoItem.Companion.bind

sealed class ShelfViewHolder<T : ShelfType>(itemView: View) :
  RecyclerView.ViewHolder(itemView) {
  abstract fun bind(item: T)

  class ThreeAudioViewHolder(
    parent: ViewGroup,
    val clickListener: FeedClickListener,
    val binding: ItemShelfListsAudioThreeBinding = ItemShelfListsAudioThreeBinding.inflate(
      LayoutInflater.from(parent.context), parent, false
    )
  ) : ShelfViewHolder<ShelfType.ThreeItem>(binding.root) {
    override fun bind(item: ShelfType.ThreeItem) {
      binding.item1.bind(item.items.first)
      item.items.second?.let { binding.item2.bind(it) }
      item.items.third?.let { binding.item3.bind(it) }
    }
  }

  class TwoVideoViewHolder(
    parent: ViewGroup,
    val clickListener: FeedClickListener,
    val binding: ItemShelfListsVideoTwoBinding = ItemShelfListsVideoTwoBinding.inflate(
      LayoutInflater.from(parent.context), parent, false
    )
  ) :
    ShelfViewHolder<ShelfType.TwoItem>(binding.root) {
    override fun bind(item: ShelfType.TwoItem) {
      item.items.first.let { binding.item1.bind(it) }
      item.items.second?.let { binding.item2.bind(it) }
    }
  }
}
