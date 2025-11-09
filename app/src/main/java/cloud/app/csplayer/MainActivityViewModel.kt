package cloud.app.csplayer

import android.content.Context
import android.view.View
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModel
import cloud.app.csplayer.utils.observe
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

@HiltViewModel
class MainActivityViewModel @Inject constructor() : ViewModel() {
  private val navSafeRect = MutableStateFlow(SafeRect())
  private val systemSafeRect = MutableStateFlow(SafeRect())
  private val bannerAdSafeRect = MutableStateFlow(SafeRect())
  private val notchedSafeRect =  MutableStateFlow(SafeRect())

  val contentRect = combine(navSafeRect, systemSafeRect, bannerAdSafeRect, notchedSafeRect) { nav, system, banner, notch ->
    nav.add(system, banner, notch)
  }

  fun setNavSafeRect(safeRect: SafeRect) {
    navSafeRect.value = safeRect
  }

  fun setBannerAdSafeRect(safeRect: SafeRect) {
    bannerAdSafeRect.value = safeRect
  }
  fun setNotchSafeRect(safeRect: SafeRect) {
    notchedSafeRect.value = safeRect
  }

  fun setSystemSafeRect(context: Context, insets: WindowInsetsCompat) {
    val system = insets.getInsets(WindowInsetsCompat.Type.systemBars())
    val inset = system.run {
      if (context.isRTL()) SafeRect(top, bottom, right, left)
      else SafeRect(top, bottom, left, right)
    }
    systemSafeRect.value = inset
  }

  data class SafeRect(
    val top: Int = 0,
    val bottom: Int = 0,
    val start: Int = 0,
    val end: Int = 0
  ) {
    fun add(vararg insets: SafeRect) = insets.fold(this) { acc, it ->
      SafeRect(
        acc.top + it.top,
        acc.bottom + it.bottom,
        acc.start + it.start,
        acc.end + it.end
      )
    }
  }

  companion object {
    fun Context.isRTL() =
      resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL

    fun Fragment.applyContentRect() {
      val viewModel by activityViewModels<MainActivityViewModel>()
      observe(viewModel.contentRect) {
        view?.setPadding(it.start, it.top, it.end, it.bottom)
      }
    }

    fun Fragment.applyNotch(view: View) {
      val viewModel by activityViewModels<MainActivityViewModel>()
      observe(viewModel.notchedSafeRect) {
        view.setPadding(it.start, it.top, it.end, it.bottom)
      }
    }
  }
}
