package cloud.streamless.torream.ui.browse

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import cloud.streamless.torream.R
import cloud.streamless.torream.databinding.ItemBrowseEntryBinding
import cloud.streamless.torream.ui.browse.network.BrowseEntry
import kotlin.math.ln
import kotlin.math.pow

class BrowseEntryAdapter(
  private val onClick: (BrowseEntry) -> Unit
) : ListAdapter<BrowseEntry, BrowseEntryAdapter.ViewHolder>(DIFF) {

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    val binding = ItemBrowseEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    return ViewHolder(binding)
  }

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    holder.bind(getItem(position))
  }

  inner class ViewHolder(private val binding: ItemBrowseEntryBinding) : RecyclerView.ViewHolder(binding.root) {
    fun bind(entry: BrowseEntry) {
      binding.entryName.text = entry.name
      binding.entryIcon.setImageResource(iconFor(entry))
      binding.entrySubtitle.text = if (entry.isDirectory) "" else formatSize(entry.size)
      binding.root.setOnClickListener { onClick(entry) }
    }

    private fun iconFor(entry: BrowseEntry): Int {
      if (entry.isDirectory) return R.drawable.ic_folder
      return when (entry.name.substringAfterLast('.', "").lowercase()) {
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "3gp", "ts" -> R.drawable.outline_play_circle_24
        "mp3", "m4a", "flac", "wav", "aac", "ogg", "wma" -> R.drawable.outline_music_note_24
        else -> R.drawable.ic_baseline_insert_drive_file_24
      }
    }

    private fun formatSize(bytes: Long): String {
      if (bytes <= 0) return ""
      val units = arrayOf("B", "KB", "MB", "GB", "TB")
      val digitGroups = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.size - 1)
      return "%.1f %s".format(bytes / 1024.0.pow(digitGroups), units[digitGroups])
    }
  }

  companion object {
    private val DIFF = object : DiffUtil.ItemCallback<BrowseEntry>() {
      override fun areItemsTheSame(oldItem: BrowseEntry, newItem: BrowseEntry) = oldItem.relativePath == newItem.relativePath
      override fun areContentsTheSame(oldItem: BrowseEntry, newItem: BrowseEntry) = oldItem == newItem
    }
  }
}
