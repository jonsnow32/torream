package com.tv.apps.zippy.utils

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
import androidx.media3.common.MimeTypes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.tv.apps.zippy.BuildConfig
import com.tv.apps.zippy.databinding.ToastBinding
import com.tv.apps.zippy.datastore.Serializer
import com.tv.apps.zippy.model.SubtitleData
import com.tv.apps.zippy.model.VideoLink
import com.tv.apps.zippy.utils.UIHelper.toPx
import kotlinx.serialization.serializer
import java.lang.ref.WeakReference
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

  /**
   * Get current stack trace as a string for debugging
   * Filters out system/VM stack frames to show only relevant application code
   */
  fun getStackTrace(): String {
    val stackTrace = Thread.currentThread().stackTrace
    val relevantFrames = stackTrace
      .filterIndexed { index, element ->
        // Skip VMStack, Thread, and Utils.getStackTrace itself
        index > 0 &&
        !element.className.startsWith("dalvik.system.VMStack") &&
        !element.className.startsWith("java.lang.Thread") &&
        !(element.className == "com.tv.apps.zippy.utils.Utils" && element.methodName == "getStackTrace")
      }
      .take(10) // Limit to 10 frames for readability

    return if (relevantFrames.isEmpty()) {
      "No relevant stack trace found"
    } else {
      relevantFrames.joinToString("\n") { element ->
        "  at ${element.className}.${element.methodName}(${element.fileName}:${element.lineNumber})"
      }
    }
  }
  private var currentToast: Toast? = null


  fun showToast(@StringRes message: Int, duration: Int? = null) {
    val act = activity ?: return
    act.runOnUiThread {
      showToast(act, act.getString(message), duration)
    }
  }

  fun showToast(message: String?, duration: Int? = null) {
    val act = activity ?: return
    act.runOnUiThread {
      showToast(act, message, duration)
    }
  }

  fun showToast(message: UiText?, duration: Int? = null) {
    val act = activity ?: return
    if (message == null) return
    act.runOnUiThread {
      showToast(act, message.asString(act), duration)
    }
  }


  @MainThread
  fun showToast(act: Activity?, text: UiText, duration: Int) {
    if (act == null) return
    text.asStringNull(act)?.let {
      showToast(act, it, duration)
    }
  }

  /** duration is Toast.LENGTH_SHORT if null*/
  @MainThread
  fun showToast(act: Activity?, @StringRes message: Int, duration: Int? = null) {
    if (act == null) return
    showToast(act, act.getString(message), duration)
  }

  const val TAG = "COMPACT"

  /** duration is Toast.LENGTH_SHORT if null*/
  @MainThread
  fun showToast(act: Activity?, message: String?, duration: Int? = null) {
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
  fun sortUrls(urls: Set<VideoLink>): List<VideoLink> {
    return urls.sortedBy { t -> t.name }
  }

  fun sortSubs(subs: Set<SubtitleData>): List<SubtitleData> {
    return subs.sortedBy { it.name }
  }

  /** Any object as json string */
  fun Any.toJson(): String {
    if (this is String) return this
    @Suppress("UNCHECKED_CAST")
    return Serializer.json.encodeToString(serializer(this::class.java) as kotlinx.serialization.KSerializer<Any>, this)
  }

  inline fun <reified T> parseJson(value: String): T {
    return Serializer.json.decodeFromString<T>(value)
  }

  inline fun <reified T> tryParseJson(value: String?): T? {
    return try {
      parseJson(value ?: return null)
    } catch (_: Exception) {
      null
    }
  }
  val base64Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

  fun decodeBase64StringToList(base64String: String): List<String> {
    val decodedBytes = decodeBase64StringToBytes(base64String)
    return decodeBytesToList(decodedBytes)
  }

  fun decodeBase64StringToBytes(base64String: String): ByteArray {
    val paddingCount = base64String.count { it == '=' }
    val estimatedSize = (base64String.length * 6 / 8) - paddingCount
    val result = ByteArray(estimatedSize)

    var byteIndex = 0
    var charIndex = 0

    while (charIndex < base64String.length) {
      var bits = 0
      var bitCount = 0

      repeat(4) {
        if (charIndex >= base64String.length) return@repeat

        val index = base64Chars.indexOf(base64String[charIndex++])
        if (index != -1) {
          bits = (bits shl 6) or index
          bitCount += 6
        }
      }

      repeat(3) {
        if (byteIndex < estimatedSize && bitCount >= 8) {
          result[byteIndex++] = (bits shr (bitCount - 8)).toByte()
          bitCount -= 8
        }
      }
    }

    return result
  }

  fun decodeBytesToList(bytes: ByteArray): List<String> {
    val result = mutableListOf<String>()
    var i = 0

    while (i < bytes.size) {
      val triplet = (base64Chars.indexOf(bytes[i].toInt().toChar()) shl 18) or
        (base64Chars.indexOf(bytes[i + 1].toInt().toChar()) shl 12) or
        (if (bytes[i + 2] != '='.code.toByte()) base64Chars.indexOf(bytes[i + 2].toInt().toChar()) shl 6 else 0) or
        if (bytes[i + 3] != '='.code.toByte()) base64Chars.indexOf(bytes[i + 3].toInt().toChar()) else 0

      val char1 = triplet shr 16 and 0xFF
      val char2 = triplet shr 8 and 0xFF
      val char3 = triplet and 0xFF

      if (bytes[i + 2] != '='.toByte()) {
        result.add(char1.toChar().toString() + char2.toChar() + char3.toChar())
      } else if (bytes[i + 3] != '='.toByte()) {
        result.add(char1.toChar().toString() + char2.toChar())
      } else {
        result.add(char1.toChar().toString())
      }

      i += 4
    }

    return result
  }


  fun getDeviceArchitecture(): String {
    val arch = System.getProperty("os.arch") ?: return "Unknown"
    return when {
      arch.startsWith("arm") -> "ARM"
      arch.startsWith("aarch64") -> "ARM64"
      arch.startsWith("x86") -> "Intel"
      arch.startsWith("x86_64") -> "Intel 64"
      else -> "Unknown"
    }
  }

  fun isARM(): Boolean {
    val arch = getDeviceArchitecture()
    return arch.startsWith("ARM")
  }

  fun isIntel(): Boolean {
    val arch = getDeviceArchitecture()
    return arch.startsWith("Intel")
  }

  /**
   * Detect if device uses 16KB page size
   * Devices with Android 15+ on certain architectures may require 16KB page alignment
   */
  fun is16KBPageSizeDevice(): Boolean {
    // Android 15 (API 35+) devices may use 16KB pages
    if (Build.VERSION.SDK_INT >= 35) {
      try {
        // Check page size through system property
        val pageSize = System.getProperty("ro.product.build.16k_page.enabled")
        if (pageSize == "true") {
          return true
        }
      } catch (e: Exception) {
        // Ignore
      }
      // Some newer ARM64 devices use 16KB pages by default
      return isARM() && Build.VERSION.SDK_INT >= 35
    }
    return false
  }

  /**
   * Check if MPV player is supported on this device
   * MPV requires native libraries that must be compiled with 16KB page size support
   */
  fun isMPVSupported(): Boolean {
    // MPV is only available on ARM devices
    if (!isARM()) {
      return false
    }
    // Check if device requires 16KB page size
    // If so, MPV is only supported if libraries were compiled with 16KB support
    if (is16KBPageSizeDevice()) {
      // For now, return false until native libraries are rebuilt with 16KB support
      // This will fallback to ExoPlayer which doesn't have this issue
      return false
    }
    return true
  }

  const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"

}

/**
 * Format bytes to human-readable file size
 * @return Formatted string like "1.5 MB", "320 KB", "2.3 GB"
 */
fun Long.formatFileSize(): String {
  if (this < 0) return "0 B"

  val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")
  var size = this.toDouble()
  var unitIndex = 0

  while (size >= 1024 && unitIndex < units.size - 1) {
    size /= 1024.0
    unitIndex++
  }

  return when {
    unitIndex == 0 -> "${this} ${units[unitIndex]}"
    size >= 100 -> String.format(java.util.Locale.US, "%.0f %s", size, units[unitIndex])
    size >= 10 -> String.format(java.util.Locale.US, "%.1f %s", size, units[unitIndex])
    else -> String.format(java.util.Locale.US, "%.2f %s", size, units[unitIndex])
  }
}

/**
 * Format milliseconds to duration string
 * @return Formatted string like "1:23:45" or "5:30"
 */
fun Long.formatDuration(): String {
  val totalSeconds = this / 1000
  val hours = totalSeconds / 3600
  val minutes = (totalSeconds % 3600) / 60
  val seconds = totalSeconds % 60

  return when {
    hours > 0 -> String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    else -> String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
  }
}
