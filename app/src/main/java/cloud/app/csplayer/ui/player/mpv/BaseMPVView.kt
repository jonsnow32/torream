package cloud.app.csplayer.ui.player.mpv

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import cloud.app.csplayer.model.SaveCaptionStyle
import cloud.app.csplayer.utils.DataStore.getKey
import cloud.app.csplayer.ui.subtitles.MPVSubtitleFragment
import timber.log.Timber

// Contains only the essential code needed to get a picture on the screen

abstract class BaseMPVView(context: Context, attrs: AttributeSet) : SurfaceView(context, attrs),
  SurfaceHolder.Callback {

  /**
   * Callback interface for MPV initialization completion
   */
  interface OnMPVInitializedListener {
    fun onMPVInitialized()
  }

  private var onMPVInitializedListener: OnMPVInitializedListener? = null

  /**
   * Set a listener to be notified when MPV initialization is complete
   */
  fun setOnMPVInitializedListener(listener: OnMPVInitializedListener?) {
    this.onMPVInitializedListener = listener
  }

  /**
   * Initialize libmpv.
   *
   * Call this once before the view is shown.
   */
  fun initialize(configDir: String, cacheDir: String) {
    MPVLib.create(context.applicationContext)

    /* set normal options (user-supplied config can override) */
    MPVLib.setOptionString("config", "yes")
    MPVLib.setOptionString("config-dir", configDir)
    for (opt in arrayOf("gpu-shader-cache-dir", "icc-cache-dir"))
      MPVLib.setOptionString(opt, cacheDir)
    initOptions()
    MPVLib.init()
    postInitOptions()
    MPVLib.setOptionString("force-window", "no")
    MPVLib.setOptionString("idle", "yes")
    holder.addCallback(this)

    mpvLogObserver = MPVLib.LogObserver { prefix, level, text ->
      Timber.tag("mpv-logger").v("[$prefix] $text")
    }
    MPVLib.addLogObserver(mpvLogObserver)
  }

  /**
   * Deinitialize libmpv.
   *
   * Call this once before the view is destroyed.
   */
  fun destroy() {
    // Disable surface callbacks to avoid using unintialized mpv state
    holder.removeCallback(this)

    // Unregister log observer if set
    try {
      mpvLogObserver?.let { MPVLib.removeLogObserver(it) }
    } catch (_: Throwable) {
    }
    mpvLogObserver = null

    // Cancel any pending init marker and mark it not initialized so other code won't call
    // native APIs while we're tearing down
    mpvInitRunnable?.let { mpvInitHandler.removeCallbacks(it) }
    mpvInitRunnable = null
    MPVLib.setInitialized(false)

    MPVLib.destroy()
  }

  protected abstract fun initOptions()
  protected abstract fun postInitOptions()

  protected open fun observeProperties() {
    // Default empty implementation; subclasses override to observe MPV properties
  }

  private var filePath: String? = null
  private var playList: List<String>? = null
  private var headers: Map<String, String>? = null
  // Handler to delay marking MPV as initialized to avoid races with native threads
  private val mpvInitHandler = Handler(Looper.getMainLooper())
  private var mpvInitRunnable: Runnable? = null
  private var mpvDetachRunnable: Runnable? = null
  private var mpvLogObserver: MPVLib.LogObserver? = null
  private val mainHandler = Handler(Looper.getMainLooper())

  /**
   * Set the first file to be played once the player is ready.
   */

  fun getHeader(additionHeaders: Map<String, String>?): Map<String, String> {

    return additionHeaders ?: emptyMap()
  }

  fun playFile(filePath: String, headers: Map<String, String>?) {
    this.filePath = filePath.replace("https:///", "https://").replace("http:///", "http://")
    this.headers = headers
    // Only attempt to call native MPV APIs if MPV is already initialized
    if (MPVLib.isInitialized() && this.filePath != null) {
      var headerList = ""
      for ((key, value) in getHeader(headers)) {
        if (key.lowercase() == "referer") {
          MPVLib.setPropertyString("referrer", value)
        } else if (key.lowercase() == "user-agent") {
          MPVLib.setPropertyString("user-agent", value)
        } else {
          if (headerList.isNotEmpty())
            headerList = headerList.plus(",")
          headerList = headerList.plus("$key: ${value.replace(",", "\\,")}")
        }
      }
      if (headerList.isNotEmpty()) {
        MPVLib.setPropertyString("http-header-fields", headerList)
      }
      MPVLib.command(arrayOf("loadfile", this.filePath))

      this.filePath = null
    }
  }

  fun playPlayList(playList: List<String>, headers: Map<String, String>?) {
    this.headers = headers
    // Only attempt to call native MPV APIs if MPV is already initialized
    if (MPVLib.isInitialized() && this.playList != null) {
      var headerList = ""
      for ((key, value) in getHeader(headers)) {
        if (key.lowercase() == "referer") {
          MPVLib.setPropertyString("referrer", value)
        } else if (key.lowercase() == "user-agent") {
          MPVLib.setPropertyString("user-agent", value)
        } else {
          if (headerList.isNotEmpty())
            headerList = headerList.plus(",")
          headerList = headerList.plus("$key: ${value.replace(",", "\\,")}")
        }
      }
      if (headerList.isNotEmpty()) {
        MPVLib.setPropertyString("http-header-fields", headerList)
      }
      playList.forEach {
        MPVLib.command(arrayOf("loadfile", it, "append-play"))
      }

      this.playList = null
    }
  }


  private var voInUse: String = "gpu"

  /**
   * Sets the VO to use.
   * It is automatically disabled/enabled when the surface dis-/appears.
   */
  fun setVo(vo: String) {
    voInUse = vo
    MPVLib.setOptionString("vo", vo)
  }

  // Surface callbacks

  override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
    MPVLib.setPropertyString("android-surface-size", "${width}x$height")
  }

  override fun surfaceCreated(holder: SurfaceHolder) {
    Log.w(TAG, "attaching surface")
    MPVLib.attachSurface(holder.surface)
    // This forces mpv to render subs/osd/whatever into our surface even if it would ordinarily not
    MPVLib.setOptionString("force-window", "yes")

    // We disable video output when the context disappears, enable it back
    MPVLib.setPropertyString("vo", voInUse)

    // Delay marking mpv initialized slightly to avoid races where native threads are still
    // setting up internal state. If the native instance isn't ready yet, marking initialized
    // immediately can still cause get_property/set_property errors.
    mpvInitRunnable?.let { mpvInitHandler.removeCallbacks(it) }
    mpvInitRunnable = Runnable {
      // Observe properties once mpv is likely ready. This must be done before marking initialized
      // so that any property events are handled after we register observers.
      try {
        observeProperties()
      } catch (_: Throwable) {
        // ignore
      }
      MPVLib.setInitialized(true)

      // NOTE: Subtitle styling is already applied in initOptions() before MPVLib.init()
      // Runtime subtitle style changes via MPVSubtitleFragment.applyToMPV() do NOT work reliably
      // Users must restart playback for new subtitle settings to take effect

      // Check if there's a pending playback that came in after surface creation
      // This handles the case where playFile() was called before MPV was initialized
      if (filePath != null) {
        val file = filePath
        filePath = null
        playFile(file!!, headers)
      } else if (playList != null) {
        val list = playList
        playList = null
        playPlayList(list!!, headers)
      }

      // Notify listener that MPV initialization is complete
      onMPVInitializedListener?.onMPVInitialized()
    }
    // Reduce delay from 2000ms to 100ms to start playback faster
    mpvInitHandler.postDelayed(mpvInitRunnable!!, 100)
  }

  override fun surfaceDestroyed(holder: SurfaceHolder) {
    Log.w(TAG, "detaching surface")

    // Cancel any pending initialization
    mpvInitRunnable?.let { mpvInitHandler.removeCallbacks(it) }
    mpvInitRunnable = null

    // Cancel any pending detach operation
    mpvDetachRunnable?.let { mpvInitHandler.removeCallbacks(it) }
    mpvDetachRunnable = null

    // Mark not initialized BEFORE any native calls to prevent race conditions
    MPVLib.setInitialized(false)

    // Detach surface immediately and synchronously
    // The original async approach caused crashes because the handler would execute
    // after the surface/context was already destroyed
    try {
      MPVLib.setPropertyString("vo", "null")
      MPVLib.setOptionString("force-window", "no")
      MPVLib.detachSurface()
    } catch (e: Exception) {
      Log.e(TAG, "Error during surface detachment", e)
    }
  }

  override fun onDetachedFromWindow() {
    filePath = null
    super.onDetachedFromWindow()
  }

  companion object {
    private const val TAG = "mpv"
  }
}
