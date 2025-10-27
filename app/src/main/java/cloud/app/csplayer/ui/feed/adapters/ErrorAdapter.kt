package cloud.app.csplayer.ui.feed.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import cloud.app.csplayer.R
import cloud.app.csplayer.databinding.ItemShelfErrorBinding
import cloud.app.csplayer.ui.adapter.GridAdapter

class ErrorAdapter(
  private var errorMessage: String? = null,
  private var buttonText: String? = null,
  private val onActionClick: () -> Unit
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
    // Set custom error message if provided, otherwise use default
    if (errorMessage != null) {
      holder.binding.tvErrorMessage.text = errorMessage
    } else {
      holder.binding.tvErrorMessage.setText(R.string.error_loading)
    }

    // Set custom button text if provided, otherwise use default
    if (buttonText != null) {
      holder.binding.btnRetry.text = buttonText
    } else {
      holder.binding.btnRetry.setText(R.string.retry)
    }

    holder.binding.btnRetry.setOnClickListener {
      onActionClick()
    }
  }

  override fun getItemCount() = 1

  /**
   * Update error message dynamically
   */
  fun updateErrorMessage(message: String?, button: String? = null) {
    errorMessage = message
    buttonText = button
    notifyItemChanged(0)
  }
}

