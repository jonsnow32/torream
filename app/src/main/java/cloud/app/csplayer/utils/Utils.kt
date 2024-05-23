package cloud.app.csplayer.utils

import android.app.Activity
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.media3.common.MimeTypes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import cloud.app.csplayer.BuildConfig
import cloud.app.csplayer.databinding.ToastBinding
import cloud.app.csplayer.ui.MainActivity
import cloud.app.csplayer.utils.UIHelper.toPx
import com.google.android.gms.cast.framework.CastSession
import java.lang.IllegalArgumentException
import java.lang.ref.WeakReference
import kotlin.math.sqrt

import kotlin.math.sqrt
object Utils {

  private var _activity: WeakReference<Activity>? = null
  var activity
    get() = _activity?.get()
    private set(value) {
      _activity = WeakReference(value)
    }

  @MainThread
  fun setActivityInstance(newActivity: Activity?) {
    activity = newActivity
  }

  fun <T> normalSafeApiCall(apiCall: () -> T): T? {
    return try {
      apiCall.invoke()
    } catch (throwable: Throwable) {
      logError(throwable)
      return null
    }
  }

  sealed class Resource<out T> {
    data class Success<out T>(val value: T) : Resource<T>()
    data class Failure(
      val isNetworkError: Boolean,
      val errorCode: Int?,
      val errorResponse: Any?, //ResponseBody
      val errorString: String,
    ) : Resource<Nothing>()

    data class Loading(val url: String? = null) : Resource<Nothing>()
  }


  const val DEBUG_EXCEPTION = "THIS IS A DEBUG EXCEPTION!"
  const val DEBUG_PRINT = "DEBUG PRINT"

  class DebugException(message: String) : Exception("$DEBUG_EXCEPTION\n$message")

  inline fun debugException(message: () -> String) {
    if (BuildConfig.DEBUG) {
      throw DebugException(message.invoke())
    }
  }

  inline fun debugPrint(tag: String = DEBUG_PRINT, message: () -> String) {
    if (BuildConfig.DEBUG) {
      Log.d(tag, message.invoke())
    }
  }

  inline fun debugWarning(message: () -> String) {
    if (BuildConfig.DEBUG) {
      logError(DebugException(message.invoke()))
    }
  }

  inline fun debugAssert(assert: () -> Boolean, message: () -> String) {
    if (BuildConfig.DEBUG && assert.invoke()) {
      throw DebugException(message.invoke())
    }
  }

  inline fun debugWarning(assert: () -> Boolean, message: () -> String) {
    if (BuildConfig.DEBUG && assert.invoke()) {
      logError(DebugException(message.invoke()))
    }
  }

  fun logError(throwable: Throwable) {
    Log.d("ApiError", "-------------------------------------------------------------------")
    Log.d("ApiError", "safeApiCall: " + throwable.localizedMessage)
    Log.d("ApiError", "safeApiCall: " + throwable.message)
    throwable.printStackTrace()
    Log.d("ApiError", "-------------------------------------------------------------------")
  }

  fun Fragment.showToast(@StringRes message: Int, duration: Int? = null) {
    val act = activity ?: return
    act.runOnUiThread {
      requireActivity().showToast(act, act.getString(message), duration)
    }
  }

  fun Fragment.showToast(message: String?, duration: Int? = null) {
    val act = activity ?: return
    act.runOnUiThread {
      requireActivity().showToast(act, message, duration)
    }
  }

  private var currentToast: Toast? = null

  @MainThread
  fun Activity.showToast(act: Activity?, message: String?, duration: Int? = null) {
    val TAG = "ShowToast"
    if (act == null || message == null) {
      Log.w(TAG, "invalid showToast act = $act message = $message")
      return
    }
    Log.i(TAG, "showToast = $message")

    try {
      currentToast?.cancel()
    } catch (e: Exception) {
      logError(e)
    }

    try {
      val binding = ToastBinding.inflate(act.layoutInflater)
      binding.text.text = message.trim()

      // custom toasts are deprecated and won't appear when cs3 sets minSDK to api30 (A11)
      val toast = Toast(act)
      toast.duration = duration ?: Toast.LENGTH_SHORT
      toast.setGravity(Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM, 0, 5.toPx)
      toast.view = binding.root
      currentToast = toast
      toast.show()

    } catch (e: Exception) {
      logError(e)
    }
  }

  fun String.toSubtitleMimeType(): String {
    return when {
      endsWith("vtt", true) -> MimeTypes.TEXT_VTT
      endsWith("srt", true) -> MimeTypes.APPLICATION_SUBRIP
      endsWith("xml", true) || endsWith("ttml", true) -> MimeTypes.APPLICATION_TTML
      else -> MimeTypes.APPLICATION_SUBRIP
    }
  }

  fun Context.isUsingMobileData(): Boolean {
    val conManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val networkInfo = conManager.allNetworks
    return networkInfo.any {
      conManager.getNetworkCapabilities(it)
        ?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
    } &&
      !networkInfo.any {
        conManager.getNetworkCapabilities(it)
          ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
      }
  }



  private var currentAudioFocusRequest: AudioFocusRequest? = null
  private var currentAudioFocusChangeListener: AudioManager.OnAudioFocusChangeListener? = null
  var onAudioFocusEvent = Event<Boolean>()

  private fun getAudioListener(): AudioManager.OnAudioFocusChangeListener? {
    if (currentAudioFocusChangeListener != null) return currentAudioFocusChangeListener
    currentAudioFocusChangeListener = AudioManager.OnAudioFocusChangeListener {
      onAudioFocusEvent.invoke(
        when (it) {
          AudioManager.AUDIOFOCUS_GAIN -> false
          AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE -> false
          AudioManager.AUDIOFOCUS_GAIN_TRANSIENT -> false
          else -> true
        }
      )
    }
    return currentAudioFocusChangeListener
  }

  fun getFocusRequest(): AudioFocusRequest? {
    if (currentAudioFocusRequest != null) return currentAudioFocusRequest
    currentAudioFocusRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).run {
        setAudioAttributes(AudioAttributes.Builder().run {
          setUsage(AudioAttributes.USAGE_MEDIA)
          setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
          build()
        })
        setAcceptsDelayedFocusGain(true)
        getAudioListener()?.let {
          setOnAudioFocusChangeListener(it)
        }
        build()
      }
    } else {
      null
    }
    return currentAudioFocusRequest
  }
  fun Activity.requestLocalAudioFocus(focusRequest: AudioFocusRequest?) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
      val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
      audioManager.requestAudioFocus(focusRequest)
    } else {
      val audioManager: AudioManager =
        getSystemService(Context.AUDIO_SERVICE) as AudioManager
      audioManager.requestAudioFocus(
        null,
        AudioManager.STREAM_MUSIC,
        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
      )
    }
  }


  data class Vector2(val x : Float, val y : Float) {
    operator fun minus(other: Vector2) = Vector2(x - other.x, y - other.y)
    operator fun plus(other: Vector2) = Vector2(x + other.x, y + other.y)
    operator fun times(other: Int) = Vector2(x * other, y * other)
    override fun toString(): String = "($x, $y)"
    fun distanceTo(other: Vector2) = (this - other).length
    private val lengthSquared by lazy { x*x + y*y }
    val length by lazy { sqrt(lengthSquared) }
  }


  abstract class DiffAdapter<T>(
    open val items: MutableList<T>,
    val comparison: (first: T, second: T) -> Boolean = { first, second ->
      first.hashCode() == second.hashCode()
    }
  ) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    override fun getItemCount(): Int {
      return items.size
    }

    fun updateList(newList: List<T>) {
      val diffResult = DiffUtil.calculateDiff(
        GenericDiffCallback(this.items, newList)
      )

      items.clear()
      items.addAll(newList)

      diffResult.dispatchUpdatesTo(this)
    }

    inner class GenericDiffCallback(
      private val oldList: List<T>,
      private val newList: List<T>
    ) :
      DiffUtil.Callback() {
      override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
        comparison(oldList[oldItemPosition], newList[newItemPosition])

      override fun getOldListSize() = oldList.size

      override fun getNewListSize() = newList.size

      override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
        oldList[oldItemPosition] == newList[newItemPosition]
    }
  }
  fun sortUrls(urls: Set<ExtractorLink>): List<ExtractorLink> {
    return urls.sortedBy { t -> -t.quality }
  }

  fun sortSubs(subs: Set<SubtitleData>): List<SubtitleData> {
    return subs.sortedBy { it.name }
  }
  const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"
}
