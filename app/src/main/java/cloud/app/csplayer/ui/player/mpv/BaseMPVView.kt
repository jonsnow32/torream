package cloud.app.csplayer.ui.player.mpv

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView

// Contains only the essential code needed to get a picture on the screen

abstract class BaseMPVView(context: Context, attrs: AttributeSet) : SurfaceView(context, attrs),
  SurfaceHolder.Callback {
  /**
   * Initialize libmpv.
   *
   * Call this once before the view is shown.
   */
  fun initialize(configDir: String, cacheDir: String) {
    MPVLib.create(context)

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
    observeProperties()
  }

  /**
   * Deinitialize libmpv.
   *
   * Call this once before the view is destroyed.
   */
  fun destroy() {
    // Disable surface callbacks to avoid using unintialized mpv state
    holder.removeCallback(this)

    MPVLib.destroy()
  }

  protected abstract fun initOptions()
  protected abstract fun postInitOptions()

  protected abstract fun observeProperties()

  private var filePath: String? = null
  private var playList: List<String>? = null
  private var headers: Map<String, String>? = null

  /**
   * Set the first file to be played once the player is ready.
   */

  fun getHeader(additionHeaders: Map<String, String>?): Map<String, String> {

    return additionHeaders ?: emptyMap()
//    return mapOf(
//      "accept" to "*/*",
//      "sec-ch-ua" to "\"Chromium\";v=\"91\", \" Not;A Brand\";v=\"99\"",
//      "sec-ch-ua-mobile" to "?0",
//      "sec-fetch-user" to "?1",
//      "sec-fetch-mode" to "navigate",
//      "sec-fetch-dest" to "video"
//    ).plus(additionHeaders ?: emptyMap())// Adds the headers from the provider, e.g Authorization
  }

  fun playFile(filePath: String, headers: Map<String, String>?) {
    this.filePath = filePath.replace("https:///", "https://").replace("http:///", "http://")
    this.headers = headers;
    if (this.filePath != null) {
//      val headerList = getHeader(headers).entries.joinToString(",") { "${it.key}: ${it.value}" }
//      headerList?.let {
//        MPVLib.setPropertyString("http-header-fields", headerList)
//      }

      var headerList = "";
      for ((key, value) in getHeader(headers)) {
        if (key.lowercase() == "referer") {
          MPVLib.setPropertyString("referrer", value)
        } else if (key.lowercase() == "user-agent") {
          MPVLib.setPropertyString("user-agent", value)
        } else {
          if (headerList.isNotEmpty())
            headerList = headerList.plus(",")
          headerList = headerList.plus("$key: ${value.replace(",", "\\,")}");
        }
      }
      if (headerList.isNotEmpty()) {
        MPVLib.setPropertyString("http-header-fields", headerList)
      }
      MPVLib.command(arrayOf("loadfile", this.filePath))

      this.filePath = null
    } else {
      // We disable video output when the context disappears, enable it back
      MPVLib.setPropertyString("vo", voInUse)
    }
  }

  fun playPlayList(playList: List<String>, headers: Map<String, String>?) {
    this.headers = headers;
    if (this.playList != null) {
//      val headerList = getHeader(headers).entries.joinToString(",") { "${it.key}: ${it.value}" }
//      headerList?.let {
//        MPVLib.setPropertyString("http-header-fields", headerList)
//      }
      var headerList = "";
      for ((key, value) in getHeader(headers)) {
        if (key.lowercase() == "referer") {
          MPVLib.setPropertyString("referrer", value)
        } else if (key.lowercase() == "user-agent") {
          MPVLib.setPropertyString("user-agent", value)
        } else {
          if (headerList.isNotEmpty())
            headerList = headerList.plus(",")
          headerList = headerList.plus("$key: ${value.replace(",", "\\,")}");
        }
      }
      if (headerList.isNotEmpty()) {
        MPVLib.setPropertyString("http-header-fields", headerList)
      }
      playList.forEach {
        MPVLib.command(arrayOf("loadfile", it, "append-play"))
      }

      this.playList = null
    } else {
      // We disable video output when the context disappears, enable it back
      MPVLib.setPropertyString("vo", voInUse)
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

    if (filePath != null) {
      var headerList = "";
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
      MPVLib.command(arrayOf("loadfile", filePath as String))

      filePath = null
    } else if (playList != null) {
      var headerList = "";
      for ((key, value) in getHeader(headers)) {
        if (key.lowercase() == "referer") {
          MPVLib.setPropertyString("referrer", value)
        } else if (key.lowercase() == "user-agent") {
          MPVLib.setPropertyString("user-agent", value)
        } else {
          if (headerList.isNotEmpty())
            headerList = headerList.plus(",")
          headerList = headerList.plus("$key: ${value.replace(",", "\\,")}");
        }
      }
      if (headerList.isNotEmpty()) {
        MPVLib.setPropertyString("http-header-fields", headerList)
      }
      playList?.forEach {
        MPVLib.command(arrayOf("loadfile", it, "append-play"))
      }
      this.playList = null
    } else {
      // We disable video output when the context disappears, enable it back
      MPVLib.setPropertyString("vo", voInUse)
    }
  }

  override fun surfaceDestroyed(holder: SurfaceHolder) {
    Log.w(TAG, "detaching surface")
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
