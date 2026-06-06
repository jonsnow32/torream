package cloud.streamless.torream.ui.feed.viewholders.horizontal

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import cloud.streamless.torream.databinding.ItemShelfListBinding
import cloud.streamless.torream.ui.feed.FeedAction
import cloud.streamless.torream.ui.feed.FeedData
import cloud.streamless.torream.ui.feed.FeedViewHolder
import cloud.streamless.torream.ui.feed.viewholders.horizontal.shelf.ShelfItem
import cloud.streamless.torream.ui.feed.viewholders.horizontal.shelf.ShelfViewHolder

class HorizontalListViewHolder(
  parent: ViewGroup,
  private val clickListener: FeedAction,
  private val pool: RecyclerView.RecycledViewPool,
  private val binding: ItemShelfListBinding = ItemShelfListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
) : FeedViewHolder<FeedData.HorizontalList>(binding.root) {
  private val adapter = Adapter(clickListener)
  private val layoutManager = androidx.recyclerview.widget.LinearLayoutManager(parent.context, RecyclerView.HORIZONTAL, false)

  init {
    binding.root.setRecycledViewPool(pool)
    binding.root.layoutManager = layoutManager
    binding.root.adapter = adapter
  }

  override fun bind(feed: FeedData.HorizontalList) {

  }

  class Adapter(val listener: FeedAction): ListAdapter<ShelfItem, ShelfViewHolder<*>>(DiffCallback) {
    object DiffCallback : DiffUtil.ItemCallback<ShelfItem>() {
      override fun areItemsTheSame(oldItem: ShelfItem, newItem: ShelfItem): Boolean {
        return oldItem.id == newItem.id
      }

      override fun areContentsTheSame(oldItem: ShelfItem, newItem: ShelfItem): Boolean {
        return oldItem == newItem
      }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShelfViewHolder<*> {
      val type = ShelfItem.Type.entries[viewType]
      return when (type) {
        ShelfItem.Type.ThreeAudioItem -> ShelfViewHolder.ThreeAudioViewHolder(parent, listener)
        ShelfItem.Type.TwoVideoItem -> ShelfViewHolder.TwoVideoViewHolder(parent, listener)
      }
    }

    override fun onBindViewHolder(holder: ShelfViewHolder<*>, position: Int) {
      when(holder) {
        is ShelfViewHolder.ThreeAudioViewHolder -> holder.bind(getItem(position) as ShelfItem.ThreeItem)
        is ShelfViewHolder.TwoVideoViewHolder -> holder.bind(getItem(position) as ShelfItem.TwoItem)
      }
    }
  }
}
