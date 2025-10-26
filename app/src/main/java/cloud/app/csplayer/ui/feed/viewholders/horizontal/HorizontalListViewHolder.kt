package cloud.app.csplayer.ui.feed.viewholders.horizontal

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import cloud.app.csplayer.databinding.ItemShelfListBinding
import cloud.app.csplayer.ui.feed.FeedClickListener
import cloud.app.csplayer.ui.feed.FeedData
import cloud.app.csplayer.ui.feed.FeedViewHolder
import cloud.app.csplayer.ui.feed.viewholders.horizontal.shelf.ShelfType
import cloud.app.csplayer.ui.feed.viewholders.horizontal.shelf.ShelfViewHolder

class HorizontalListViewHolder(
  parent: ViewGroup,
  private val clickListener: FeedClickListener,
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

  class Adapter(val listener: FeedClickListener): ListAdapter<ShelfType, ShelfViewHolder<*>>(DiffCallback) {
    object DiffCallback : DiffUtil.ItemCallback<ShelfType>() {
      override fun areItemsTheSame(oldItem: ShelfType, newItem: ShelfType): Boolean {
        return oldItem.id == newItem.id
      }

      override fun areContentsTheSame(oldItem: ShelfType, newItem: ShelfType): Boolean {
        return oldItem == newItem
      }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShelfViewHolder<*> {
      val type = ShelfType.Type.entries[viewType]
      return when (type) {
        ShelfType.Type.ThreeAudioItem -> ShelfViewHolder.ThreeAudioViewHolder(parent, listener)
        ShelfType.Type.TwoVideoItem -> ShelfViewHolder.TwoVideoViewHolder(parent, listener)
      }
    }

    override fun onBindViewHolder(holder: ShelfViewHolder<*>, position: Int) {
      when(holder) {
        is ShelfViewHolder.ThreeAudioViewHolder -> holder.bind(getItem(position) as ShelfType.ThreeItem)
        is ShelfViewHolder.TwoVideoViewHolder -> holder.bind(getItem(position) as ShelfType.TwoItem)
      }
    }
  }
}
