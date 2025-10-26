package cloud.app.csplayer.ui.feed.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import cloud.app.csplayer.databinding.ItemShelfLoadingBinding
import cloud.app.csplayer.ui.adapter.GridAdapter

class LoadingAdapter : RecyclerView.Adapter<LoadingAdapter.ViewHolder>(), GridAdapter {
    class ViewHolder(val binding: ItemShelfLoadingBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemShelfLoadingBinding.inflate(inflater, parent, false)
        return ViewHolder(binding)
    }

    override val adapter = this
    override fun getSpanSize(position: Int, width: Int, count: Int) = count

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        // Loading indicator will be shown automatically from the layout
    }

    override fun getItemCount() = 1
}

