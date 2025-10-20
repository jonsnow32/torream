package cloud.app.csplayer.utils

import android.app.Activity
import android.app.AppOpsManager
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.res.Configuration.UI_MODE_NIGHT_MASK
import android.content.res.Configuration.UI_MODE_NIGHT_NO
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.os.TransactionTooLargeException
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.annotation.IdRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.getSystemService
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import cloud.app.csplayer.R
import cloud.app.csplayer.utils.Utils.logError
import cloud.app.csplayer.utils.Utils.showToast

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

  fun Context.isNightMode() =
    resources.configuration.uiMode and UI_MODE_NIGHT_MASK != UI_MODE_NIGHT_NO


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

  fun Activity?.navigate(@IdRes navigation: Int, arguments: Bundle? = null, navOptions: NavOptions? = null) {
    try {
      if (this is FragmentActivity) {
        val navHostFragment =
          supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment?
        navHostFragment?.navController?.navigate(navigation, arguments, navOptions)
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
  fun Fragment.clipboardHelper(label: UiText, text: CharSequence) {
    val ctx = requireContext();
    try {
      ctx.let {
        val clip = ClipData.newPlainText(label.asString(ctx), text)
        val labelSuffix = txt(R.string.toast_copied).asString(ctx)
        ctx.getSystemService<ClipboardManager>()?.setPrimaryClip(clip)

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
          showToast("${label.asString(ctx)} $labelSuffix")
        }
      }
    } catch (t: Throwable) {
      Log.e("ClipboardService", "$t")
      when (t) {
        is SecurityException -> {
          showToast(R.string.clipboard_permission_error)
        }

        is TransactionTooLargeException -> {
          showToast(R.string.clipboard_too_large)
        }

        else -> {
          showToast(R.string.clipboard_unknown_error, Toast.LENGTH_LONG)
        }
      }
    }
  }
  fun Context.hasPIPPermission(): Boolean {
    val appOps =
      getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      appOps.checkOpNoThrow(
        AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
        android.os.Process.myUid(),
        packageName
      ) == AppOpsManager.MODE_ALLOWED
    } else {
      return true
    }
  }

  fun showInputMethod(view: View?) {
    if (view == null) return
    val inputMethodManager =
      view.context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager?
    inputMethodManager?.showSoftInput(view, 0)
  }

  /**
   * Sets the system bar colors in a way that avoids deprecated APIs on Android 15+.
   * Uses WindowInsetsController for bar appearance on API 30+.
   * Falls back to legacy methods on older versions.
   */
  fun Window.setSystemBarColors(statusBarColor: Int, navigationBarColor: Int, lightBars: Boolean = true) {
      when {
          Build.VERSION.SDK_INT >= 34 -> {
              // On Android 15+, avoid deprecated setStatusBarColor/setNavigationBarColor
              val controller = WindowInsetsControllerCompat(this, decorView)
              controller.isAppearanceLightStatusBars = lightBars
              controller.isAppearanceLightNavigationBars = lightBars
              // Bar colors should be set via theme or systemBarAppearance, not directly
          }
          Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
              // On Android 11-14, use WindowInsetsControllerCompat for bar appearance
              val controller = WindowInsetsControllerCompat(this, decorView)
              controller.isAppearanceLightStatusBars = lightBars
              controller.isAppearanceLightNavigationBars = lightBars
              this.statusBarColor = statusBarColor
              this.navigationBarColor = navigationBarColor
          }
          else -> {
              // On older versions, use legacy APIs
              this.statusBarColor = statusBarColor
              this.navigationBarColor = navigationBarColor
          }
      }
  }
}
