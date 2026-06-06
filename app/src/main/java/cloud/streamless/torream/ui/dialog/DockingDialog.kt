// Kotlin
package cloud.streamless.torream.ui.dialog

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import cloud.streamless.torream.R
import cloud.streamless.torream.utils.isTvOrEmulator


open class DockingDialog : DialogFragment() {
  enum class Docking {
    LEFT, RIGHT, BOTTOM, CENTER
  }
  open val widthPercentage: Float = 0.4f
  private var orientation: Docking = Docking.RIGHT
  private var resultListener: ((Bundle?) -> Unit)? = null

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val isRTL = context?.isRTL() ?: false
    orientation = when (resources.configuration.orientation) {
      Configuration.ORIENTATION_LANDSCAPE -> if(!isTvOrEmulator()) Docking.CENTER else (if (isRTL) Docking.LEFT else Docking.RIGHT)
      Configuration.ORIENTATION_PORTRAIT -> Docking.BOTTOM
      else -> Docking.BOTTOM
    }

    val dialogStyle = when (orientation) {
      Docking.RIGHT -> R.style.RightMaterialDialogTheme
      Docking.BOTTOM -> R.style.BottomMaterialDialogTheme
      Docking.LEFT -> R.style.LeftMaterialDialogTheme
      Docking.CENTER -> R.style.CenterMaterialDialogTheme
    }

    return Dialog(requireContext(), dialogStyle).apply {
      setCanceledOnTouchOutside(true)
    }
  }

  /**
   * Show the dialog with a result listener
   * @param fragmentManager The FragmentManager to use
   * @param listener Callback that receives a Bundle result when dialog is dismissed
   */
  fun show(fragmentManager: FragmentManager, listener: ((Bundle?) -> Unit)? = null) {
    if (!isAdded) {
      resultListener = listener
      show(fragmentManager, "DockingDialog")
    }
  }

  override fun onStart() {
    super.onStart()
    dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    updateWindowAttributes()
  }

  override fun onDismiss(dialog: DialogInterface) {
    super.onDismiss(dialog)
    // Trigger the result listener when dialog is dismissed
    resultListener?.invoke(getResultBundle())
  }

  /**
   * Override this method in subclass to provide custom result data
   * @return Bundle containing result data, or null if no result
   */
  open fun getResultBundle(): Bundle? = null

  fun setDocking(docking: Docking) {
    this.orientation = docking
    dialog?.window?.let { updateWindowAttributes() }
  }

  private fun updateWindowAttributes() {
    dialog?.window?.let { window ->
      val displayMetrics = resources.displayMetrics
      val params = window.attributes

      when (orientation) {
        Docking.RIGHT -> {
          params.width = (displayMetrics.widthPixels * widthPercentage).toInt()
          params.height = ViewGroup.LayoutParams.MATCH_PARENT
          params.gravity = Gravity.END
        }

        Docking.BOTTOM -> {
          params.width = ViewGroup.LayoutParams.MATCH_PARENT
          params.height = ViewGroup.LayoutParams.WRAP_CONTENT
          params.gravity = Gravity.BOTTOM
        }

        Docking.LEFT -> {
          params.width = (displayMetrics.widthPixels * widthPercentage).toInt()
          params.height = ViewGroup.LayoutParams.MATCH_PARENT
          params.gravity = Gravity.START
        }

        Docking.CENTER -> {
          params.width = (displayMetrics.widthPixels * widthPercentage).toInt()
          params.height = ViewGroup.LayoutParams.WRAP_CONTENT
          params.gravity = Gravity.CENTER
        }
      }

      window.attributes = params
    }
  }
}

private fun Context.isRTL()  = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
