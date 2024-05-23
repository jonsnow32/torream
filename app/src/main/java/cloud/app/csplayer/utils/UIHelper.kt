package cloud.app.csplayer.utils

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.annotation.IdRes
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import androidx.navigation.fragment.NavHostFragment
import cloud.app.csplayer.R
import cloud.app.csplayer.utils.Utils.logError

object UIHelper {
  val Int.toPx: Int get() = (this * Resources.getSystem().displayMetrics.density).toInt()
  val Float.toPx: Float get() = (this * Resources.getSystem().displayMetrics.density)
  val Int.toDp: Int get() = (this / Resources.getSystem().displayMetrics.density).toInt()
  val Float.toDp: Float get() = (this / Resources.getSystem().displayMetrics.density)

  fun Dialog?.dismissSafe(activity: Activity?) {
    if (this?.isShowing == true && activity?.isFinishing == false) {
      this.dismiss()
    }
  }

  fun Context.getStatusBarHeight(): Int {
    if (isTvOrEmulator()) {
      return 0
    }

    var result = 0
    val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
    if (resourceId > 0) {
      result = resources.getDimensionPixelSize(resourceId)
    }
    return result
  }

  fun fixPaddingStatusbar(v: View?) {
    if (v == null) return
    val ctx = v.context ?: return
    v.setPadding(
      v.paddingLeft,
      v.paddingTop + ctx.getStatusBarHeight(),
      v.paddingRight,
      v.paddingBottom
    )
  }

  fun Context.getNavigationBarHeight(): Int {
    var result = 0
    val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
    if (resourceId > 0) {
      result = resources.getDimensionPixelSize(resourceId)
    }
    return result
  }

  fun AlertDialog.setDefaultFocus(buttonFocus: Int = DialogInterface.BUTTON_NEGATIVE) {
    if (!context.isTvOrEmulator()) return
    this.getButton(buttonFocus).run {
      isFocusableInTouchMode = true
      requestFocus()
    }
  }

  fun Context.colorFromAttribute(attribute: Int): Int {
    val attributes = obtainStyledAttributes(intArrayOf(attribute))
    val color = attributes.getColor(0, 0)
    attributes.recycle()
    return color
  }

  fun Activity?.navigate(@IdRes navigation: Int, arguments: Bundle? = null) {
    try {
      if (this is FragmentActivity) {
        val navHostFragment =
          supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment?
        navHostFragment?.navController?.navigate(navigation, arguments)
      }
    } catch (t: Throwable) {
      logError(t)
    }
  }

  fun FragmentActivity.popCurrentPage() {
    this.onBackPressedDispatcher.onBackPressed()
  }

  fun Activity.changeStatusBarState(hide: Boolean): Int {
    return if (hide) {

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        window.insetsController?.hide(WindowInsets.Type.statusBars())

      } else {
        @Suppress("DEPRECATION")
        window.setFlags(
          WindowManager.LayoutParams.FLAG_FULLSCREEN,
          WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
      }
      0
    } else {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        window.insetsController?.show(WindowInsets.Type.statusBars())

      } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
      }

      this.getStatusBarHeight()
    }
  }

  fun Activity.showSystemUI() {

    /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, View(this)).show(WindowInsetsCompat.Type.systemBars())

    } else {*/
    /** WINDOW COMPAT IS BUGGY DUE TO FU*KED UP PLAYER AND TRAILERS **/
    window.decorView.systemUiVisibility =
      (View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
    //}

    changeStatusBarState(isLayout(LayoutMode.Emulator.id))
  }

}
