package cloud.app.csplayer.ui.feed.viewholders.horizontal.shelf

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import cloud.app.csplayer.databinding.ItemShelfListsAudioThreeBinding
import cloud.app.csplayer.databinding.ItemShelfListsVideoTwoBinding
import cloud.app.csplayer.ui.feed.FeedAction

sealed class ShelfViewHolder<T : ShelfItem>(itemView: View) :
  RecyclerView.ViewHolder(itemView) {
  abstract fun bind(item: T)

  class ThreeAudioViewHolder(
    parent: ViewGroup,
    val clickListener: FeedAction,
    val binding: ItemShelfListsAudioThreeBinding = ItemShelfListsAudioThreeBinding.inflate(
      LayoutInflater.from(parent.context), parent, false
    )
  ) : ShelfViewHolder<ShelfItem.ThreeItem>(binding.root) {
    override fun bind(item: ShelfItem.ThreeItem) {
//      binding.item1.bind(item.items.first)
//      item.items.second?.let { binding.item2.bind(it) }
//      item.items.third?.let { binding.item3.bind(it) }
    }
  }

  class TwoVideoViewHolder(
    parent: ViewGroup,
    val clickListener: FeedAction,
    val binding: ItemShelfListsVideoTwoBinding = ItemShelfListsVideoTwoBinding.inflate(
      LayoutInflater.from(parent.context), parent, false
    )
  ) :
    ShelfViewHolder<ShelfItem.TwoItem>(binding.root) {
    override fun bind(item: ShelfItem.TwoItem) {
//      item.items.first.let { binding.item1.bind(it) }
//      item.items.second?.let { binding.item2.bind(it) }
    }
  }
}
