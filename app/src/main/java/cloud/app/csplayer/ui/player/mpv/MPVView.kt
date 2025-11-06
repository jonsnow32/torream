package cloud.app.csplayer.ui.player.mpv

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Environment
import android.preference.PreferenceManager
import android.util.AttributeSet
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import cloud.app.csplayer.R
import cloud.app.csplayer.ui.player.mpv.MPVLib.mpvFormat.MPV_FORMAT_DOUBLE
import cloud.app.csplayer.ui.player.mpv.MPVLib.mpvFormat.MPV_FORMAT_FLAG
import cloud.app.csplayer.ui.player.mpv.MPVLib.mpvFormat.MPV_FORMAT_INT64
import cloud.app.csplayer.ui.player.mpv.MPVLib.mpvFormat.MPV_FORMAT_NODE
import cloud.app.csplayer.ui.player.mpv.MPVLib.mpvFormat.MPV_FORMAT_NONE
import cloud.app.csplayer.ui.player.mpv.MPVLib.mpvFormat.MPV_FORMAT_STRING
import cloud.app.csplayer.utils.isTvOrEmulator
import cloud.app.csplayer.utils.DataStore.getKey
import cloud.app.csplayer.model.SaveCaptionStyle
import timber.log.Timber
import java.io.File
import kotlin.reflect.KProperty

class MPVView(context: Context, attrs: AttributeSet) : BaseMPVView(context, attrs) {

  // Cache expensive operations
  private val displayMetrics by lazy { resources.displayMetrics }

  // Lazy property that just returns the path without disk I/O
  // Directory will be created asynchronously in postInitOptions()
  private val subtitleFontsDir: File by lazy {
    File(context.filesDir, "fonts")
  }

  override fun initOptions() {
    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    // apply phone-optimized defaults
    MPVLib.setOptionString("profile", "fast")

    // vo
    setVo(
      if (sharedPreferences.getBoolean("gpu_next", false))
        "gpu-next"
      else
        "gpu"
    )

    // hwdec - Force software decoding on emulators to prevent goldfish decoder issues
    val hwdec = if (sharedPreferences.getBoolean("hardware_decoding", true) && !context.isTvOrEmulator()) {
      "auto"
    } else {
      "no"
    }

    // vo: set display fps as reported by android
    val disp = ContextCompat.getDisplayOrDefault(context)
    val refreshRate = disp.mode.refreshRate

    Timber.tag(TAG).v("Display ${disp.displayId} reports FPS of $refreshRate")
    MPVLib.setOptionString("display-fps-override", refreshRate.toString())

    // set non-complex options - optimized with apply
    val opts = arrayOf(
      "default_audio_language" to "alang",
      "default_subtitle_language" to "slang",
      "video_scale" to "scale",
      "video_scale_param1" to "scale-param1",
      "video_scale_param2" to "scale-param2",
      "video_downscale" to "dscale",
      "video_downscale_param1" to "dscale-param1",
      "video_downscale_param2" to "dscale-param2",
      "video_tscale" to "tscale",
      "video_tscale_param1" to "tscale-param1",
      "video_tscale_param2" to "tscale-param2"
    )

    for ((preference_name, mpv_option) in opts) {
      sharedPreferences.getString(preference_name, null)?.takeIf { it.isNotBlank() }?.let {
        MPVLib.setOptionString(mpv_option, it)
      }
    }

    // Deband mode optimization
    when (sharedPreferences.getString("video_debanding", "")) {
      "gradfun" -> MPVLib.setOptionString("vf", "gradfun=radius=12")
      "gpu" -> MPVLib.setOptionString("deband", "yes")
    }

    val vidsync = sharedPreferences.getString(
      "video_sync",
      resources.getString(R.string.pref_video_interpolation_sync_default)
    )
    MPVLib.setOptionString("video-sync", vidsync!!)

    if (sharedPreferences.getBoolean("video_interpolation", false))
      MPVLib.setOptionString("interpolation", "yes")

    if (sharedPreferences.getBoolean("gpudebug", false))
      MPVLib.setOptionString("gpu-debug", "yes")

    if (sharedPreferences.getBoolean("video_fastdecode", false)) {
      MPVLib.setOptionString("vd-lavc-fast", "yes")
      MPVLib.setOptionString("vd-lavc-skiploopfilter", "nonkey")
    }

    MPVLib.setOptionString("gpu-context", "android")
    MPVLib.setOptionString("opengl-es", "yes")
    MPVLib.setOptionString("hwdec", hwdec)

    MPVLib.setOptionString("ao", "audiotrack,opensles")
    MPVLib.setOptionString("tls-verify", "yes")
    MPVLib.setOptionString("tls-ca-file", "${context.filesDir.path}/cacert.pem")
    MPVLib.setOptionString("input-default-bindings", "yes")

    // Limit demuxer cache since the defaults are too high for mobile devices
    val cacheMegs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) 64 else 32
    val cacheBytes = (cacheMegs * 1024 * 1024).toString()
    MPVLib.setOptionString("demuxer-max-bytes", cacheBytes)
    MPVLib.setOptionString("demuxer-max-back-bytes", cacheBytes)

    // Screenshot directory setup - just pass the path, let MPV create it when needed
    val screenshotDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
    MPVLib.setOptionString("screenshot-directory", screenshotDir.path)
    MPVLib.setOptionString("vd-lavc-film-grain", "cpu")

    // Audio/Video decoding robustness: ignore errors to prevent crashes
    MPVLib.setOptionString("ad-lavc-error-recognition", "2")  // 2 = ignore errors
    MPVLib.setOptionString("ad-lavc-skip-frame", "nokey")
    MPVLib.setOptionString("vd-lavc-error-recognition", "2")
    MPVLib.setOptionString("vd-lavc-skip-frame", "nokey")

    // Subtitle/font settings: use cached subtitleFontsDir
    MPVLib.setOptionString("sub-fonts-dir", subtitleFontsDir.path)
    MPVLib.setOptionString("sub-ass-override", "yes")
    MPVLib.setOptionString("sub-visibility", "yes")

    Timber.tag(TAG).v("Set sub-ass-override to yes and sub-visibility to yes - will use custom subtitle styling")

    // Load and apply subtitle settings - optimized with extracted method
    applySubtitleSettings(sharedPreferences)
  }

  /**
   * Optimized method to apply subtitle settings from DataStore or SharedPreferences
   * Eliminates duplicate code and improves maintainability
   */
  private fun applySubtitleSettings(sharedPreferences: android.content.SharedPreferences) {
    // Try to load saved subtitle style from DataStore first
    val savedStyle: SaveCaptionStyle? = runCatching {
      context.getKey<SaveCaptionStyle>("subtitle_settings")
    }.getOrNull()

    if (savedStyle != null) {
      applySubtitleStyle(savedStyle)
    } else {
      // Fallback to SharedPreferences
      applyLegacySubtitleSettings(sharedPreferences)
    }
  }

  /**
   * Apply subtitle style from SaveCaptionStyle object
   */
  private fun applySubtitleStyle(style: SaveCaptionStyle) {
    // Apply colors
    MPVLib.setOptionString("sub-color", colorToMpvHex(style.foregroundColor))
    MPVLib.setOptionString("sub-bg-color", colorToMpvHex(style.backgroundColor))
    MPVLib.setOptionString("sub-window-color", colorToMpvHex(style.edgeColor))
    MPVLib.setOptionString("sub-border-color", colorToMpvHex(style.edgeColor))

    // Font size (convert SP -> px) - use cached displayMetrics
    style.fixedTextSize?.let { sp ->
      val px = android.util.TypedValue.applyDimension(
        android.util.TypedValue.COMPLEX_UNIT_SP,
        sp,
        displayMetrics
      ).toInt()
      MPVLib.setOptionString("sub-font-size", px.toString())
    }

    // Edge / shadow mapping
    when (style.edgeType) {
      androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE ->
        MPVLib.setOptionString("sub-border-size", "0")
      androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE ->
        MPVLib.setOptionString("sub-border-size", "2")
      else ->
        MPVLib.setOptionString("sub-shadow-offset", "2")
    }

    // Copy and set font file if specified
    style.typefaceFilePath?.let { path ->
      runCatching {
        val src = java.io.File(path)
        if (src.exists()) {
          val dst = java.io.File(subtitleFontsDir, src.name)
          if (!dst.exists()) {
            src.copyTo(dst, overwrite = false)
          }
          MPVLib.setOptionString("sub-font", dst.nameWithoutExtension)
        }
      }
    }
  }

  /**
   * Apply legacy subtitle settings from SharedPreferences
   */
  private fun applyLegacySubtitleSettings(prefs: android.content.SharedPreferences) {
    prefs.getString("subtitle_font", null)?.takeIf { it.isNotBlank() }?.let {
      MPVLib.setOptionString("sub-font", it)
    }

    prefs.getString("subtitle_font_size", null)?.takeIf { it.isNotBlank() }?.let { size ->
      size.toDoubleOrNull()?.takeIf { it > 0 }?.let {
        MPVLib.setOptionString("sub-font-size", it.toString())
      }
    }

    prefs.getString("subtitle_color", null)?.takeIf { it.isNotBlank() }?.let {
      MPVLib.setOptionString("sub-color", it)
    }

    prefs.getString("subtitle_border_color", null)?.takeIf { it.isNotBlank() }?.let {
      MPVLib.setOptionString("sub-border-color", it)
    }

    prefs.getString("subtitle_shadow_color", null)?.takeIf { it.isNotBlank() }?.let {
      MPVLib.setOptionString("sub-shadow-color", it)
    }
  }

  /**
   * Convert Android ARGB color int to mpv color string (#RRGGBB or #AARRGGBB)
   * Optimized with bit shifting
   */
  private fun colorToMpvHex(color: Int): String {
    val a = (color ushr 24) and 0xFF
    val r = (color ushr 16) and 0xFF
    val g = (color ushr 8) and 0xFF
    val b = color and 0xFF
    return if (a in 0..254) {
      String.format("#%02X%02X%02X%02X", a, r, g, b)
    } else {
      String.format("#%02X%02X%02X", r, g, b)
    }
  }

  override fun postInitOptions() {
    // we need to call write-watch-later manually
    MPVLib.setOptionString("save-position-on-quit", "no")

    // Ensure directories exist asynchronously to avoid StrictMode violations
    post {
      ensureDirectoriesExist()
    }
  }

  /**
   * Ensure required directories exist (called off main thread via post)
   */
  private fun ensureDirectoriesExist() {
    try {
      // Create subtitle fonts directory if it doesn't exist
      if (!subtitleFontsDir.exists()) {
        subtitleFontsDir.mkdirs()
        Timber.tag(TAG).v("Created subtitle fonts directory: ${subtitleFontsDir.path}")
      }

      // Create screenshot directory if it doesn't exist
      val screenshotDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
      if (!screenshotDir.exists()) {
        screenshotDir.mkdirs()
        Timber.tag(TAG).v("Created screenshot directory: ${screenshotDir.path}")
      }
    } catch (e: Exception) {
      Timber.tag(TAG).w(e, "Failed to create player directories")
    }
  }

  fun onPointerEvent(event: MotionEvent): Boolean {
    if (!event.isFromSource(InputDevice.SOURCE_CLASS_POINTER)) return false

    if (event.actionMasked == MotionEvent.ACTION_SCROLL) {
      val h = event.getAxisValue(MotionEvent.AXIS_HSCROLL)
      val v = event.getAxisValue(MotionEvent.AXIS_VSCROLL)

      when {
        h > 0 -> MPVLib.command(arrayOf("keypress", "WHEEL_RIGHT", "$h"))
        h < 0 -> MPVLib.command(arrayOf("keypress", "WHEEL_LEFT", "${-h}"))
      }

      when {
        v > 0 -> MPVLib.command(arrayOf("keypress", "WHEEL_UP", "$v"))
        v < 0 -> MPVLib.command(arrayOf("keypress", "WHEEL_DOWN", "${-v}"))
      }

      return true
    }
    return false
  }

  fun onKey(event: KeyEvent): Boolean {
    if (event.action == KeyEvent.ACTION_MULTIPLE) return false
    if (KeyEvent.isModifierKey(event.keyCode)) return false

    var mapped = KeyMapping.map[event.keyCode]
    if (mapped == null) {
      // Fallback to produced glyph
      if (!event.isPrintingKey) {
        if (event.repeatCount == 0)
          Timber.tag(TAG).d("Unmapped non-printable key ${event.keyCode}")
        return false
      }

      val ch = event.unicodeChar
      if (ch.and(KeyCharacterMap.COMBINING_ACCENT) != 0)
        return false // dead key
      mapped = ch.toChar().toString()
    }

    if (event.repeatCount > 0)
      return true // eat event but ignore it, mpv has its own key repeat

    val mod = buildList {
      if (event.isShiftPressed) add("shift")
      if (event.isCtrlPressed) add("ctrl")
      if (event.isAltPressed) add("alt")
      if (event.isMetaPressed) add("meta")
      add(mapped)
    }

    val action = if (event.action == KeyEvent.ACTION_DOWN) "keydown" else "keyup"
    MPVLib.command(arrayOf(action, mod.joinToString("+")))

    return true
  }

  override fun observeProperties() {
    // This observes all properties needed by MPVView, MPVActivity or other classes
    data class Property(val name: String, val format: Int = MPV_FORMAT_NONE)

    val p = arrayOf(
      Property("time-pos", MPV_FORMAT_INT64),
      Property("duration/full", MPV_FORMAT_DOUBLE),
      Property("pause", MPV_FORMAT_FLAG),
      Property("paused-for-cache", MPV_FORMAT_FLAG),
      Property("speed", MPV_FORMAT_STRING),
      Property("track-list/count", MPV_FORMAT_INT64),
      Property("video-params/aspect", MPV_FORMAT_DOUBLE),
      Property("video-params/rotate", MPV_FORMAT_DOUBLE),
      Property("playlist-pos", MPV_FORMAT_INT64),
      Property("playlist-count", MPV_FORMAT_INT64),
      Property("video-format", MPV_FORMAT_STRING),
      Property("media-title", MPV_FORMAT_STRING),
      Property("metadata", MPV_FORMAT_NODE),
      Property("loop-playlist"),
      Property("loop-file"),
      Property("shuffle", MPV_FORMAT_FLAG),
      Property("hwdec-current", MPV_FORMAT_STRING),
      Property("end_file", MPV_FORMAT_NODE)
    )

    for ((name, format) in p)
      MPVLib.observeProperty(name, format)
  }

  fun addObserver(o: MPVLib.EventObserver) {
    MPVLib.addObserver(o)
  }

  fun removeObserver(o: MPVLib.EventObserver) {
    MPVLib.removeObserver(o)
  }

  data class Track(val mpvId: Int, val name: String, val selected: Boolean = false)

  var tracks = mapOf<String, MutableList<Track>>(
    "audio" to arrayListOf(),
    "video" to arrayListOf(),
    "sub" to arrayListOf()
  )


  fun loadTracks() {
    if (!MPVLib.isInitialized()) {
      Timber.tag(TAG).w("loadTracks() called before MPV initialization - skipping")
      return
    }

    Timber.tag(TAG).d("loadTracks() called - checking track-list/count")
    val count = MPVLib.getPropertyInt("track-list/count")

    if (count == null) {
      Timber.tag(TAG).w("loadTracks() - track-list/count returned null (MPV not ready)")
      return
    }

    if (count == 0) {
      Timber.tag(TAG).w("loadTracks() - track-list/count is 0 (no tracks available yet)")
      return
    }

    Timber.tag(TAG).d("loadTracks() - found $count tracks")

    for (list in tracks.values) {
      list.clear()
      // pseudo-track to allow disabling audio/subs
      list.add(Track(0, context.getString(R.string.track_off)))
    }

    // Note that because events are async, properties might disappear at any moment
    for (i in 0 until count) {
      val type = MPVLib.getPropertyString("track-list/$i/type") ?: continue
      if (!tracks.containsKey(type)) {
        Timber.tag(TAG).w("Got unknown track type: $type")
        continue
      }
      val mpvId = MPVLib.getPropertyInt("track-list/$i/id") ?: continue

      val title = MPVLib.getPropertyString("track-list/$i/title")
      val selected = MPVLib.getPropertyBoolean("track-list/$i/selected") ?: false

      val trackName = when {
        !title.isNullOrEmpty() -> context.getString(R.string.ui_track_text, mpvId, title)
        type.contains("video") -> {
          val demux_w = MPVLib.getPropertyInt("track-list/$i/demux-w")
          val demux_h = MPVLib.getPropertyInt("track-list/$i/demux-h")
          context.getString(R.string.ui_video_track_text, mpvId, demux_w, demux_h)
        }
        type == "audio" -> {
          val audioChannel = MPVLib.getPropertyString("track-list/$i/audio-channels")
          val lang = MPVLib.getPropertyString("track-list/$i/lang")
          if (!lang.isNullOrEmpty()) {
            context.getString(R.string.ui_audio_track, mpvId, audioChannel, lang)
          } else {
            context.getString(R.string.ui_track_text, mpvId, context.getString(R.string.unknown))
          }
        }
        type == "sub" -> {
          val lang = MPVLib.getPropertyString("track-list/$i/lang")
          if (!lang.isNullOrEmpty()) {
            context.getString(R.string.ui_track_text, mpvId, lang)
          } else {
            context.getString(R.string.ui_track_text, mpvId, context.getString(R.string.unknown))
          }
        }
        else -> context.getString(R.string.ui_track_text, mpvId, context.getString(R.string.unknown))
      }

      tracks.getValue(type).add(
        Track(
          mpvId = mpvId,
          name = trackName,
          selected = selected
        )
      )
    }
  }

  data class PlaylistItem(val index: Int, val filename: String, val title: String?)

  fun loadPlaylist(): MutableList<PlaylistItem> {
    val playlist = mutableListOf<PlaylistItem>()
    val count = MPVLib.getPropertyInt("playlist-count") ?: return playlist
    for (i in 0 until count) {
      val filename = MPVLib.getPropertyString("playlist/$i/filename") ?: continue
      val title = MPVLib.getPropertyString("playlist/$i/title")
      playlist.add(PlaylistItem(index = i, filename = filename, title = title))
    }
    return playlist
  }

  data class Chapter(val index: Int, val title: String?, val time: Double)

  fun loadChapters(): MutableList<Chapter> {
    val chapters = mutableListOf<Chapter>()
    val count = MPVLib.getPropertyInt("chapter-list/count") ?: return chapters
    for (i in 0 until count) {
      val title = MPVLib.getPropertyString("chapter-list/$i/title")
      val time = MPVLib.getPropertyDouble("chapter-list/$i/time") ?: continue
      chapters.add(
        Chapter(
          index = i,
          title = title,
          time = time
        )
      )
    }
    return chapters
  }

  // Property getters/setters

  var paused: Boolean?
    get() = MPVLib.getPropertyBoolean("pause")
    set(paused) = MPVLib.setPropertyBoolean("pause", paused!!)

  var timePos: Double?
    get() = MPVLib.getPropertyDouble("time-pos/full")
    set(progress) {
      if (progress != null && progress >= 0.0) {
        MPVLib.setPropertyDouble("time-pos", progress)
      }
    }

  /** name of currently active hardware decoder or "no" */
  val hwdecActive: String
    get() = MPVLib.getPropertyString("hwdec-current") ?: "no"

  var playbackSpeed: Double?
    get() = MPVLib.getPropertyDouble("speed")
    set(speed) = MPVLib.setPropertyDouble("speed", speed!!)

  var subDelay: Double?
    get() = MPVLib.getPropertyDouble("sub-delay")
    set(speed) = MPVLib.setPropertyDouble("sub-delay", speed!!)

  var secondarySubDelay: Double?
    get() = MPVLib.getPropertyDouble("secondary-sub-delay")
    set(speed) = MPVLib.setPropertyDouble("secondary-sub-delay", speed!!)

  val estimatedVfFps: Double?
    get() = MPVLib.getPropertyDouble("estimated-vf-fps")


  /**
   * Returns the video aspect ratio. Rotation is taken into account.
   */
  fun getVideoAspect(): Double? {
    val aspect = MPVLib.getPropertyDouble("video-params/aspect") ?: return null
    if (aspect < 0.001) return 0.0

    val rot = MPVLib.getPropertyInt("video-params/rotate") ?: 0
    return if (rot % 180 == 90) 1.0 / aspect else aspect
  }

  class TrackDelegate(private val name: String) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): Int {
      // Try integer getter first
      val intVal = MPVLib.getPropertyInt(name)
      if (intVal != null) return intVal
      // Fallback: try string getter and parse integer if present
      val s = try { MPVLib.getPropertyString(name) } catch (_: Exception) { null }
      return s?.toIntOrNull() ?: -1
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
      if (value == -1)
        MPVLib.setPropertyString(name, "no")
      else
        MPVLib.setPropertyInt(name, value)
    }
  }

  var vid: Int by TrackDelegate("vid")
  var sid: Int by TrackDelegate("sid")
  var secondarySid: Int by TrackDelegate("secondary-sid")
  var aid: Int by TrackDelegate("aid")

  fun getRepeat(): Int {
    val loopPlaylist = MPVLib.getPropertyString("loop-playlist") ?: "no"
    val loopFile = MPVLib.getPropertyString("loop-file") ?: "no"
    return when ("$loopPlaylist$loopFile") {
      "noinf" -> 2
      "infno" -> 1
      else -> 0
    }
  }

  fun cycleRepeat() {
    val state = getRepeat()
    when (state) {
      0, 1 -> {
        MPVLib.setPropertyString("loop-playlist", if (state == 1) "no" else "inf")
        MPVLib.setPropertyString("loop-file", if (state == 1) "inf" else "no")
      }

      2 -> MPVLib.setPropertyString("loop-file", "no")
    }
  }

  fun getShuffle(): Boolean {
    return MPVLib.getPropertyBoolean("shuffle")
  }

  fun changeShuffle(cycle: Boolean, value: Boolean = true) {
    val state = getShuffle()
    val newState = if (cycle) state.xor(value) else value
    if (state == newState) return

    MPVLib.command(arrayOf(if (newState) "playlist-shuffle" else "playlist-unshuffle"))
    MPVLib.setPropertyBoolean("shuffle", newState)
  }

  // Helper function to change subtitle font size at runtime
  fun setSubtitleFontSize(size: String) {
    if (!MPVLib.isInitialized()) return

    size.toDoubleOrNull()?.takeIf { it > 0 }?.let { fontSize ->
      runCatching {
        MPVLib.command(arrayOf("set", "sub-font-size", fontSize.toString()))
      }
    }
  }

  // Helper function to change subtitle font size from SP to pixels
  fun setSubtitleFontSizeFromSp(sp: Float) {
    if (!MPVLib.isInitialized()) return

    runCatching {
      val px = android.util.TypedValue.applyDimension(
        android.util.TypedValue.COMPLEX_UNIT_SP,
        sp,
        displayMetrics
      ).toInt()
      MPVLib.command(arrayOf("set", "sub-font-size", px.toString()))
    }
  }

  companion object {
    private const val TAG = "mpv"
  }
}
