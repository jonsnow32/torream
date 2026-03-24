package com.tv.apps.zippy.ui.dialog

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tv.apps.zippy.databinding.ItemHeaderBinding

class HeadersAdapter(
  private val onDeleteHeader: (Int) -> Unit
) : RecyclerView.Adapter<HeadersAdapter.HeaderViewHolder>() {

  private val headers = mutableListOf<Pair<String, String>>()

  fun setHeaders(newHeaders: List<Pair<String, String>>) {
    headers.clear()
    headers.addAll(newHeaders)
    notifyDataSetChanged()
  }

  fun addHeader(key: String, value: String) {
    headers.add(Pair(key, value))
    notifyItemInserted(headers.size - 1)
  }

  fun removeHeader(position: Int) {
    if (position >= 0 && position < headers.size) {
      headers.removeAt(position)
      notifyItemRemoved(position)
    }
  }

  fun getHeaders(): Map<String, String> {
    return headers.toMap()
  }

  fun isEmpty(): Boolean = headers.isEmpty()

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderViewHolder {
    val binding = ItemHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    return HeaderViewHolder(binding)
  }

  override fun onBindViewHolder(holder: HeaderViewHolder, position: Int) {
    holder.bind(headers[position], position)
  }

  override fun getItemCount(): Int = headers.size

  inner class HeaderViewHolder(
    private val binding: ItemHeaderBinding
  ) : RecyclerView.ViewHolder(binding.root) {

    fun bind(header: Pair<String, String>, position: Int) {
      binding.headerKeyText.text = header.first
      binding.headerValueText.text = header.second
      binding.deleteHeaderButton.setOnClickListener {
        onDeleteHeader(position)
      }
    }
  }
}

