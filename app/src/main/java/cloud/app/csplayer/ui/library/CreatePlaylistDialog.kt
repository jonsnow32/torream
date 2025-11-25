package cloud.app.csplayer.ui.library

import android.app.Dialog
import android.os.Bundle
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import cloud.app.csplayer.R
import cloud.app.csplayer.databinding.BottomInputDialogBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class CreatePlaylistDialog(
  private val onPlaylistCreated: (name: String, description: String?) -> Unit
) : DialogFragment() {

  private var binding: BottomInputDialogBinding? = null

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    binding = BottomInputDialogBinding.inflate(layoutInflater)

    binding?.apply {
      text1.text = getString(R.string.add_playlist)
      nginxTextInput.hint = getString(R.string.playlist_name)

      applyBtt.text = getString(R.string.create)
      cancelBtt.text = getString(R.string.cancel)

      applyBtt.setOnClickListener {
        val name = nginxTextInput.text.toString().trim()
        if (name.isNotEmpty()) {
          onPlaylistCreated(name, null)
          dismiss()
        } else {
          nginxTextInput.error = getString(R.string.playlist_name_required)
        }
      }

      cancelBtt.setOnClickListener {
        dismiss()
      }
    }

    val dialog = MaterialAlertDialogBuilder(requireContext())
      .setView(binding?.root)
      .create()

    // Show keyboard automatically
    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

    return dialog
  }

  override fun onDestroyView() {
    super.onDestroyView()
    binding = null
  }

  companion object {
    const val TAG = "CreatePlaylistDialog"
  }
}

