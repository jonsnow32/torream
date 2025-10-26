package cloud.app.csplayer.ui.feed.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import cloud.app.csplayer.databinding.ItemShelfErrorBinding
import cloud.app.csplayer.ui.adapter.GridAdapter

class ErrorAdapter(
  private val onRetryClick: () -> Unit
) : RecyclerView.Adapter<ErrorAdapter.ViewHolder>(), GridAdapter {

  class ViewHolder(val binding: ItemShelfErrorBinding) : RecyclerView.ViewHolder(binding.root)

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    val inflater = LayoutInflater.from(parent.context)
    val binding = ItemShelfErrorBinding.inflate(inflater, parent, false)
    return ViewHolder(binding)
  }

  override val adapter = this
  override fun getSpanSize(position: Int, width: Int, count: Int) = count

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    holder.binding.btnRetry.setOnClickListener {
      onRetryClick()
    }
  }

  override fun getItemCount() = 1
}

