package cloud.streamless.torream.ui.browse

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import cloud.streamless.torream.databinding.ItemNetworkShareBinding
import cloud.streamless.torream.model.NetworkShare

class NetworkShareAdapter(
  private val onClick: (NetworkShare) -> Unit,
  private val onDelete: (NetworkShare) -> Unit
) : ListAdapter<NetworkShare, NetworkShareAdapter.ViewHolder>(DIFF) {

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    val binding = ItemNetworkShareBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    return ViewHolder(binding)
  }

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    holder.bind(getItem(position))
  }

  inner class ViewHolder(private val binding: ItemNetworkShareBinding) : RecyclerView.ViewHolder(binding.root) {
    fun bind(share: NetworkShare) {
      binding.shareName.text = share.displayName
      binding.shareSubtitle.text = "${share.protocol} - ${share.host}"
      binding.root.setOnClickListener { onClick(share) }
      binding.shareDelete.setOnClickListener { onDelete(share) }
    }
  }

  companion object {
    private val DIFF = object : DiffUtil.ItemCallback<NetworkShare>() {
      override fun areItemsTheSame(oldItem: NetworkShare, newItem: NetworkShare) = oldItem.id == newItem.id
      override fun areContentsTheSame(oldItem: NetworkShare, newItem: NetworkShare) = oldItem == newItem
    }
  }
}
