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

// Contains only the essential code needed to get a picture on the screen

abstract class BaseMPVView(context: Context, attrs: AttributeSet) : SurfaceView(context, attrs),
  SurfaceHolder.Callback {

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

    /* set hardcoded options */
    postInitOptions()
    // would crash before the surface is attached
    MPVLib.setOptionString("force-window", "no")
    // need to idle at least once for playFile() logic to work
    MPVLib.setOptionString("idle", "yes")
    holder.addCallback(this)

    // Log observer to catch ffmpeg / h264 errors and report to UI
    mpvLogObserver = object : MPVLib.LogObserver {
      override fun logMessage(prefix: String, level: Int, text: String) {
        try {
          // Always log for debugging
          Log.v("mpv-logger", "[$prefix] $text")

          val lower = text.lowercase()
          val now = System.currentTimeMillis()
          // If message mentions libass or fonts, surface it (they often are info-level)
          val isFontIssue = lower.contains("libass") || lower.contains("font") || lower.contains("fontconfig") || lower.contains("can't find") || lower.contains("couldn't find") || lower.contains("no such file") || lower.contains("ass:")

          if (isFontIssue || level <= MPVLib.mpvLogLevel.MPV_LOG_LEVEL_ERROR) {
            if (now - lastErrorShownAt > 3000) {
              lastErrorShownAt = now
              mainHandler.post {
                Toast.makeText(context, text, Toast.LENGTH_LONG).show()
              }
            }
          }
        } catch (_: Throwable) {
        }
      }
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
    MPVState.setInitialized(false)

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

  // Log observer to catch ffmpeg / h264 errors and report to UI
  private var mpvLogObserver: MPVLib.LogObserver? = null
  private val mainHandler = Handler(Looper.getMainLooper())
  // debounce to avoid spamming the user with repeated log messages
  private var lastErrorShownAt: Long = 0

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
    if (MPVState.isInitialized() && this.filePath != null) {
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
    if (MPVState.isInitialized() && this.playList != null) {
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

    // Store pending file/playlist for after initialization
    val pendingFile = filePath
    val pendingList = playList?.toList()
    val pendingHeaders = headers

    if (pendingFile != null) {
      var headerList = ""
      for ((key, value) in getHeader(pendingHeaders)) {
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
      MPVLib.command(arrayOf("loadfile", pendingFile as String))

      filePath = null
    } else if (pendingList != null) {
      var headerList = ""
      for ((key, value) in getHeader(pendingHeaders)) {
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
      pendingList.forEach {
        MPVLib.command(arrayOf("loadfile", it, "append-play"))
      }
      this.playList = null
    } else {
      // We disable video output when the context disappears, enable it back
      MPVLib.setPropertyString("vo", voInUse)
    }

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
      MPVState.setInitialized(true)

      // After mpv is initialized, try to apply saved subtitle style (if present).
      try {
        val savedStyle: SaveCaptionStyle? = try {
          context.getKey<SaveCaptionStyle>("subtitle_settings")
        } catch (_: Exception) {
          null
        }
        if (savedStyle != null) {
          MPVSubtitleFragment.applyToMPV(context, savedStyle)
        }
      } catch (_: Throwable) {
      }

      // Check if there's a pending playback that came in after surface creation
      if (filePath != null) {
        val file = filePath
        filePath = null
        playFile(file!!, headers)
      } else if (playList != null) {
        val list = playList
        playList = null
        playPlayList(list!!, headers)
      }
    }
    // Reduce delay from 2000ms to 100ms to start playback faster
    mpvInitHandler.postDelayed(mpvInitRunnable!!, 100)
  }

  override fun surfaceDestroyed(holder: SurfaceHolder) {
    Log.w(TAG, "detaching surface")
    // Mark not initialized before calling native teardown to ensure other threads don't call into
    // native mpv while it is being destroyed.
    // Cancel any pending initialization and mark not initialized immediately
    mpvInitRunnable?.let { mpvInitHandler.removeCallbacks(it) }
    mpvInitRunnable = null
    MPVState.setInitialized(false)

    MPVLib.setPropertyString("vo", "null")
    MPVLib.setOptionString("force-window", "no")
    MPVLib.detachSurface()
    // FIXME: race condition here because detachSurface just sets a property and that is async
  }

  override fun onDetachedFromWindow() {
    filePath = null
    super.onDetachedFromWindow()
  }

  companion object {
    private const val TAG = "mpv"
  }
}
