package cloud.app.csplayer.ui.player.mpv

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Environment
import android.preference.PreferenceManager
import android.util.AttributeSet
import android.util.Log
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
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
import kotlin.reflect.KProperty

@UnstableApi
internal class MPVView(context: Context, attrs: AttributeSet) : BaseMPVView(context, attrs) {
  @SuppressLint("LogNotTimber")
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
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      val disp = ContextCompat.getDisplayOrDefault(context)
      val refreshRate = disp.mode.refreshRate

      Timber.tag(TAG).v("Display ${disp.displayId} reports FPS of $refreshRate")
      MPVLib.setOptionString("display-fps-override", refreshRate.toString())
    } else {
      Timber.tag(TAG).v(
        "Android version too old, disabling refresh rate functionality " +
          "(${Build.VERSION.SDK_INT} < ${Build.VERSION_CODES.M})"
      )
    }

    // set non-complex options
    data class Property(val preference_name: String, val mpv_option: String)

    val opts = arrayOf(
      Property("default_audio_language", "alang"),
      Property("default_subtitle_language", "slang"),

      // vo-related
      Property("video_scale", "scale"),
      Property("video_scale_param1", "scale-param1"),
      Property("video_scale_param2", "scale-param2"),

      Property("video_downscale", "dscale"),
      Property("video_downscale_param1", "dscale-param1"),
      Property("video_downscale_param2", "dscale-param2"),

      Property("video_tscale", "tscale"),
      Property("video_tscale_param1", "tscale-param1"),
      Property("video_tscale_param2", "tscale-param2")
    )

    for ((preference_name, mpv_option) in opts) {
      val preference = sharedPreferences.getString(preference_name, "")
      if (!preference.isNullOrBlank())
        MPVLib.setOptionString(mpv_option, preference)
    }

    val debandMode = sharedPreferences.getString("video_debanding", "")
    if (debandMode == "gradfun") {
      // lower the default radius (16) to improve performance
      MPVLib.setOptionString("vf", "gradfun=radius=12")
    } else if (debandMode == "gpu") {
      MPVLib.setOptionString("deband", "yes")
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

    // Additional emulator-specific settings to prevent goldfish decoder
    if (context.isTvOrEmulator()) {
      // Completely disable hardware decoding and force software fallback
      MPVLib.setOptionString("hwdec-codecs", "")
      MPVLib.setOptionString("vd-lavc-software-fallback", "yes")
      MPVLib.setOptionString("ad-lavc-downmix", "yes")
      // Force libavcodec software decoder
      MPVLib.setOptionString("vd", "lavc")
      // Disable MediaCodec entirely to prevent goldfish selection
      MPVLib.setOptionString("android-surface-size", "0x0")
      Log.v(TAG, "Emulator detected: Forcing software decoding to prevent goldfish decoder issues")
    } else {
      MPVLib.setOptionString("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")
    }
    MPVLib.setOptionString("ao", "audiotrack,opensles")
    MPVLib.setOptionString("tls-verify", "yes")
    MPVLib.setOptionString("tls-ca-file", "${this.context.filesDir.path}/cacert.pem")
    MPVLib.setOptionString("input-default-bindings", "yes")
    // Limit demuxer cache since the defaults are too high for mobile devices
    val cacheMegs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) 64 else 32
    MPVLib.setOptionString("demuxer-max-bytes", "${cacheMegs * 1024 * 1024}")
    MPVLib.setOptionString("demuxer-max-back-bytes", "${cacheMegs * 1024 * 1024}")
    //
    val screenshotDir =
      Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
    screenshotDir.mkdirs()
    MPVLib.setOptionString("screenshot-directory", screenshotDir.path)
    MPVLib.setOptionString("vd-lavc-film-grain", "cpu")

    // Audio decoding robustness: ignore errors to prevent crashes on corrupted AAC streams
    MPVLib.setOptionString("ad-lavc-error-recognition", "2")  // 2 = ignore errors
    MPVLib.setOptionString("ad-lavc-skip-frame", "nokey")    // Skip non-key frames on errors

    // Video decoding robustness: ignore errors to prevent crashes on corrupted H.264 streams
    MPVLib.setOptionString("vd-lavc-error-recognition", "2")  // 2 = ignore errors
    MPVLib.setOptionString("vd-lavc-skip-frame", "nokey")    // Skip non-key frames on errors

    // Subtitle/font settings: allow overriding via preferences and ensure a fonts dir for libass
    val subtitleFontsDir = java.io.File(this.context.filesDir, "fonts")
    if (!subtitleFontsDir.exists()) subtitleFontsDir.mkdirs()
    // If there are no fonts shipped to the fonts dir, copy a bundled fallback font so libass
    // has a usable font. This helps when external font copying failed and prevents libass
    // from rendering with a missing font.
    try {
      val hasAnyFonts = subtitleFontsDir.listFiles()?.any { it.isFile } == true
      if (!hasAnyFonts) {
        try {
          // Try to copy a fallback font if available from assets
          // Font resources (R.font.*) cannot be directly copied, so we skip this
          // libass will use its built-in fallback font
        } catch (_: Exception) {
          // ignore — best effort fallback
        }
      }
    } catch (_: Exception) {}
    MPVLib.setOptionString("sub-fonts-dir", subtitleFontsDir.path)

    // Try to load full saved subtitle style (preferred) from DataStore
    try {
      // DataStore.getKey is an extension used elsewhere; import path: cloud.app.csplayer.utils.DataStore.getKey
      val savedStyle: SaveCaptionStyle? = try {
        context.getKey<SaveCaptionStyle>("subtitle_settings")
      } catch (ex: Exception) {
        null
      }

      if (savedStyle != null) {
        // helper to convert Android ARGB color int to mpv color string (#RRGGBB or #AARRGGBB)
        fun colorToMpvHex(color: Int): String {
          val a = (color ushr 24) and 0xFF
          val r = (color ushr 16) and 0xFF
          val g = (color ushr 8) and 0xFF
          val b = color and 0xFF
          return if (a in 0..254) String.format("#%02X%02X%02X%02X", a, r, g, b) else String.format("#%02X%02X%02X", r, g, b)
        }

        // apply colors (use MPVLib to set options before native init completes)
        MPVLib.setOptionString("sub-color", colorToMpvHex(savedStyle.foregroundColor))
        MPVLib.setOptionString("sub-bg-color", colorToMpvHex(savedStyle.backgroundColor))
        MPVLib.setOptionString("sub-window-color", colorToMpvHex(savedStyle.edgeColor))
        MPVLib.setOptionString("sub-border-color", colorToMpvHex(savedStyle.edgeColor))

        // font size (convert SP -> px)
        savedStyle.fixedTextSize?.let { sp ->
          val px = android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_SP,
            sp,
            resources.displayMetrics
          ).toInt()
          MPVLib.setOptionString("sub-font-size", px.toString())
        }

        // edge / shadow mapping
        when (savedStyle.edgeType) {
          androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE -> MPVLib.setOptionString("sub-border-size", "0")
          androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE -> MPVLib.setOptionString("sub-border-size", "2")
          else -> MPVLib.setOptionString("sub-shadow-offset", "2")
        }

        // try to copy any selected font file into filesDir/fonts and set sub-font to filename (without ext)
        savedStyle.typefaceFilePath?.let { path ->
          try {
            val src = java.io.File(path)
            if (src.exists()) {
              val dst = java.io.File(this.context.filesDir, "fonts/${src.name}")
              if (!dst.exists()) {
                src.copyTo(dst)
              }
              val fontName = dst.nameWithoutExtension
              MPVLib.setOptionString("sub-font", fontName)
            }
          } catch (e: Exception) {
            // ignore
          }
        }

      } else {
        // fallback to older per-key SharedPreferences approach
        val subFont = sharedPreferences.getString("subtitle_font", "")
        val subFontSize = sharedPreferences.getString("subtitle_font_size", "")
        val subColor = sharedPreferences.getString("subtitle_color", "")
        val subBorderColor = sharedPreferences.getString("subtitle_border_color", "")
        val subShadowColor = sharedPreferences.getString("subtitle_shadow_color", "")

        if (!subFont.isNullOrBlank()) MPVLib.setOptionString("sub-font", subFont)
        if (!subFontSize.isNullOrBlank()) MPVLib.setOptionString("sub-font-size", subFontSize)
        if (!subColor.isNullOrBlank()) MPVLib.setOptionString("sub-color", subColor)
        if (!subBorderColor.isNullOrBlank()) MPVLib.setOptionString("sub-border-color", subBorderColor)
        if (!subShadowColor.isNullOrBlank()) MPVLib.setOptionString("sub-shadow-color", subShadowColor)
      }
    } catch (e: Exception) {
      // If DataStore utilities are not accessible for whatever reason, fall back to preferences
      val subFont = sharedPreferences.getString("subtitle_font", "")
      val subFontSize = sharedPreferences.getString("subtitle_font_size", "")
      val subColor = sharedPreferences.getString("subtitle_color", "")
      val subBorderColor = sharedPreferences.getString("subtitle_border_color", "")
      val subShadowColor = sharedPreferences.getString("subtitle_shadow_color", "")

      if (!subFont.isNullOrBlank()) MPVLib.setOptionString("sub-font", subFont)
      if (!subFontSize.isNullOrBlank()) MPVLib.setOptionString("sub-font-size", subFontSize)
      if (!subColor.isNullOrBlank()) MPVLib.setOptionString("sub-color", subColor)
      if (!subBorderColor.isNullOrBlank()) MPVLib.setOptionString("sub-border-color", subBorderColor)
      if (!subShadowColor.isNullOrBlank()) MPVLib.setOptionString("sub-shadow-color", subShadowColor)
    }

  }

  override fun postInitOptions() {
    // we need to call write-watch-later manually
    MPVLib.setOptionString("save-position-on-quit", "no")
  }

  fun onPointerEvent(event: MotionEvent): Boolean {
    assert(event.isFromSource(InputDevice.SOURCE_CLASS_POINTER))
    if (event.actionMasked == MotionEvent.ACTION_SCROLL) {
      val h = event.getAxisValue(MotionEvent.AXIS_HSCROLL)
      val v = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
      if (h > 0)
        MPVLib.command(arrayOf("keypress", "WHEEL_RIGHT", "$h"))
      else if (h < 0)
        MPVLib.command(arrayOf("keypress", "WHEEL_LEFT", "${-h}"))
      if (v > 0)
        MPVLib.command(arrayOf("keypress", "WHEEL_UP", "$v"))
      else if (v < 0)
        MPVLib.command(arrayOf("keypress", "WHEEL_DOWN", "${-v}"))
      return true
    }
    return false
  }

  fun onKey(event: KeyEvent): Boolean {
    if (event.action == KeyEvent.ACTION_MULTIPLE)
      return false
    if (KeyEvent.isModifierKey(event.keyCode))
      return false

    var mapped = KeyMapping.map.get(event.keyCode)
    if (mapped == null) {
      // Fallback to produced glyph
      if (!event.isPrintingKey) {
        if (event.repeatCount == 0)
          Log.d(TAG, "Unmapped non-printable key ${event.keyCode}")
        return false
      }

      val ch = event.unicodeChar
      if (ch.and(KeyCharacterMap.COMBINING_ACCENT) != 0)
        return false // dead key
      mapped = ch.toChar().toString()
    }

    if (event.repeatCount > 0)
      return true // eat event but ignore it, mpv has its own key repeat

    val mod: MutableList<String> = mutableListOf()
    event.isShiftPressed && mod.add("shift")
    event.isCtrlPressed && mod.add("ctrl")
    event.isAltPressed && mod.add("alt")
    event.isMetaPressed && mod.add("meta")

    val action = if (event.action == KeyEvent.ACTION_DOWN) "keydown" else "keyup"
    mod.add(mapped)
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
      Property("track-list", MPV_FORMAT_NODE),
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
    for (list in tracks.values) {
      list.clear()
      // pseudo-track to allow disabling audio/subs
      list.add(Track(0, context.getString(R.string.track_off)))
    }
    val count = MPVApi.getPropertyInt("track-list/count") ?: return
    // Note that because events are async, properties might disappear at any moment
    // so use ?: continue instead of !!
    for (i in 0 until count) {
      val type = MPVApi.getPropertyString("track-list/$i/type") ?: continue
      if (!tracks.containsKey(type)) {
        Log.w(TAG, "Got unknown track type: $type")
        continue
      }
      val mpvId = MPVApi.getPropertyInt("track-list/$i/id") ?: continue

      val title = MPVApi.getPropertyString("track-list/$i/title")
      val selected = MPVApi.getPropertyBoolean("track-list/$i/selected")
      val decoder = MPVApi.getPropertyString("track-list/$i/decoder")
      var trackName = if(title.isNullOrEmpty()) null else context.getString(
        R.string.ui_track_text, mpvId,title
      );

      when (type) {
        "video" -> {
          if (trackName.isNullOrEmpty()) {
            val demux_w = MPVApi.getPropertyInt("track-list/$i/demux-w")
            val demux_h = MPVApi.getPropertyInt("track-list/$i/demux-h")

            trackName = context.getString(
              R.string.ui_video_track_text,
              mpvId,
              demux_w,
              demux_h,
            )
          }
        }

        "audio" -> {
          if (trackName.isNullOrEmpty()) {
            val audioChannel = MPVApi.getPropertyString("track-list/$i/audio-channels")
            val lang = MPVApi.getPropertyString("track-list/$i/lang")
            if (!lang.isNullOrEmpty())
              trackName = context.getString(R.string.ui_audio_track, mpvId, audioChannel, lang)
          }


        }

        "sub" -> {
          if (trackName.isNullOrEmpty()) {
            val lang = MPVApi.getPropertyString("track-list/$i/lang")
            if (!lang.isNullOrEmpty())
              trackName = context.getString(R.string.ui_track_text, mpvId, lang)
          }
        }

        else -> {
          context.getString(R.string.ui_track_text, mpvId, context.getString(R.string.unknown))
        }
      }

      if (trackName.isNullOrEmpty()) {
        trackName =
          context.getString(R.string.ui_track_text, mpvId, context.getString(R.string.unknown))
      }

      tracks.getValue(type).add(
        Track(
          mpvId = mpvId,
          name = trackName,
          selected = selected ?: false
        )
      )

    }
  }

  data class PlaylistItem(val index: Int, val filename: String, val title: String?)

  fun loadPlaylist(): MutableList<PlaylistItem> {
    val playlist = mutableListOf<PlaylistItem>()
    val count = MPVApi.getPropertyInt("playlist-count") ?: return playlist
    for (i in 0 until count) {
      val filename = MPVApi.getPropertyString("playlist/$i/filename") ?: continue
      val title = MPVApi.getPropertyString("playlist/$i/title")
      playlist.add(PlaylistItem(index = i, filename = filename, title = title))
    }
    return playlist
  }

  data class Chapter(val index: Int, val title: String?, val time: Double)

  fun loadChapters(): MutableList<Chapter> {
    val chapters = mutableListOf<Chapter>()
    val count = MPVApi.getPropertyInt("chapter-list/count") ?: return chapters
    for (i in 0 until count) {
      val title = MPVApi.getPropertyString("chapter-list/$i/title")
      val time = MPVApi.getPropertyDouble("chapter-list/$i/time") ?: continue
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
    get() = MPVApi.getPropertyDouble("time-pos/full")
    set(progress) {
      if (progress != null && progress >= 0.0) {
        MPVLib.setPropertyDouble("time-pos", progress)
      }
    }

  /** name of currently active hardware decoder or "no" */
  val hwdecActive: String
    get() = MPVApi.getPropertyString("hwdec-current") ?: "no"

  var playbackSpeed: Double?
    get() = MPVApi.getPropertyDouble("speed")
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
   * Returns the video aspect ratio. Rotation is taken into account.z
   */
  fun getVideoAspect(): Double? {
    return MPVApi.getPropertyDouble("video-params/aspect")?.let {
      if (it < 0.001)
        return 0.0
      val rot = MPVApi.getPropertyInt("video-params/rotate") ?: 0
      if (rot % 180 == 90)
        1.0 / it
      else
        it
    }
  }

  class TrackDelegate(private val name: String) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): Int {
      // Try integer getter first
      val intVal = MPVApi.getPropertyInt(name)
      if (intVal != null) return intVal
      // Fallback: try string getter and parse integer if present
      val s = try { MPVApi.getPropertyString(name) } catch (_: Exception) { null }
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

  // Commands

  fun cyclePause() = MPVLib.command(arrayOf("cycle", "pause"))
  fun cycleAudio() = MPVLib.command(arrayOf("cycle", "audio"))
  fun cycleSub() = MPVLib.command(arrayOf("cycle", "sub"))
  fun cycleHwdec() = MPVLib.command(arrayOf("cycle-values", "hwdec", "auto", "no"))

  fun cycleSpeed() {
    val speeds = arrayOf(0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0)
    val currentSpeed = playbackSpeed ?: 1.0
    val index = speeds.indexOfFirst { it > currentSpeed }
    playbackSpeed = speeds[if (index == -1) 0 else index]
  }

  fun getRepeat(): Int {
    return when (MPVLib.getPropertyString("loop-playlist") +
      MPVLib.getPropertyString("loop-file")) {
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
    // Use the 'shuffle' property to store the shuffled state, since changing
    // it at runtime doesn't do anything.
    val state = getShuffle()
    val newState = if (cycle) state.xor(value) else value
    if (state == newState)
      return
    MPVLib.command(arrayOf(if (newState) "playlist-shuffle" else "playlist-unshuffle"))
    MPVLib.setPropertyBoolean("shuffle", newState)
  }

  companion object {
    private const val TAG = "mpv"
  }

}
