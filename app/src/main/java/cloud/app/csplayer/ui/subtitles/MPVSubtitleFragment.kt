package cloud.app.csplayer.ui.subtitles

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import cloud.app.csplayer.R
import cloud.app.csplayer.databinding.FragmentSubtitleSettingsBinding
import cloud.app.csplayer.model.SaveCaptionStyle
import cloud.app.csplayer.ui.colorpicker.ColorPickerDialog
import cloud.app.csplayer.ui.player.mpv.MPVApi
import cloud.app.csplayer.ui.player.mpv.MPVState
import cloud.app.csplayer.utils.DataStore.getKey
import cloud.app.csplayer.utils.DataStore.setKey
import cloud.app.csplayer.utils.Event
import cloud.app.csplayer.utils.GlobalEvent
import cloud.app.csplayer.utils.LayoutMode
import cloud.app.csplayer.utils.SingleSelectionHelper.showDialog
import cloud.app.csplayer.utils.UIHelper
import cloud.app.csplayer.utils.UIHelper.navigate
import cloud.app.csplayer.utils.Utils
import cloud.app.csplayer.utils.hideSystemUI
import cloud.app.csplayer.utils.isLayout
import com.google.android.material.appbar.MaterialToolbar
import java.io.File

/**
 * MPVSubtitleFragment
 * Similar to SubtitlesFragment but applies subtitle style/settings directly to mpv via MPVLib.
 */
@OptIn(UnstableApi::class)
class MPVSubtitleFragment : Fragment() {
  companion object {
    val applyStyleEvent = Event<SaveCaptionStyle>()

    private fun getDefColor(id: Int): Int {
      return when (id) {
        0 -> Color.WHITE
        1 -> Color.BLACK
        2 -> Color.TRANSPARENT
        3 -> Color.TRANSPARENT
        else -> Color.TRANSPARENT
      }
    }

    private fun Context.getCurrentSavedStyle(): SaveCaptionStyle {
      return this.getKey<SaveCaptionStyle>("subtitle_settings") ?: SaveCaptionStyle(
          getDefColor(0),
          getDefColor(2),
          getDefColor(3),
          CaptionStyleCompat.EDGE_TYPE_OUTLINE,
          getDefColor(1),
          null,
          null,
          20,
          25.0f,
      )
    }
    fun push(activity: Activity?, hide: Boolean = true) {
      activity.navigate(R.id.global_to_navigation_mpv_subtitles, Bundle().apply {
        putBoolean("hide", hide)
      })
    }
    private fun Context.getSavedFonts(): List<File> {
      val externalFiles = getExternalFilesDir(null) ?: return emptyList()
      val fontDir = File(externalFiles.absolutePath + "/Fonts").also { it.mkdirs() }
      return fontDir.listFiles()?.mapNotNull {
        if (it != null && (it.name.endsWith(".ttf", true) || it.name.endsWith(".otf", true))) it else null
      }?.toList() ?: emptyList()
    }

    private fun getPixels(size: Float): Int {
      val metrics: DisplayMetrics = Resources.getSystem().displayMetrics
      return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, size, metrics).toInt()
    }

    private fun colorToMpvHex(color: Int): String {
      // produce #AARRGGBB if alpha != 255, else #RRGGBB
      val a = Color.alpha(color)
      val r = Color.red(color)
      val g = Color.green(color)
      val b = Color.blue(color)
      return if (a in 0..254) String.format("#%02X%02X%02X%02X", a, r, g, b) else String.format("#%02X%02X%02X", r, g, b)
    }

    private fun copyFontToMpvFontsDir(context: Context, srcPath: String?): String? {
      if (srcPath.isNullOrBlank()) return null
      try {
        val src = File(srcPath)
        if (!src.exists()) return null
        val fontsDir = File(context.filesDir, "fonts")
        if (!fontsDir.exists()) fontsDir.mkdirs()
        val dst = File(fontsDir, src.name)
        if (!dst.exists()) {
          src.copyTo(dst)
        }
        return dst.absolutePath
      } catch (_: Exception) {
        return null
      }
    }

    // Return true if MPV native instance appears to be initialized and usable.
    private fun isMPVInitialized(): Boolean {
      // Use MPVState instead of probing native MPV to avoid calling into native code before init.
      return MPVState.isInitialized()
    }

    // Try applying to mpv; if mpv isn't initialized yet, retry a few times with a delay.
    fun applyToMPV(context: Context, style: SaveCaptionStyle) {
      tryApplyToMPV(context, style, attemptsLeft = 6)
    }

    private fun tryApplyToMPV(context: Context, style: SaveCaptionStyle, attemptsLeft: Int) {
      if (!isMPVInitialized()) {
        if (attemptsLeft <= 0) {
          // Give up after retries
          return
        }
        // Schedule a retry on the main looper after a short delay
        Handler(Looper.getMainLooper()).postDelayed({
          tryApplyToMPV(context, style, attemptsLeft - 1)
        }, 300)
        return
      }

      // mpv is initialized; perform the actual apply
      applyToMPVInternal(context, style)
    }

    // The original apply code moved here to be called when mpv is ready.
    private fun applyToMPVInternal(context: Context, style: SaveCaptionStyle) {
      // Colors
      val fg = colorToMpvHex(style.foregroundColor)
      val bg = colorToMpvHex(style.backgroundColor)
      val window = colorToMpvHex(style.windowColor)
      val edge = colorToMpvHex(style.edgeColor)

      Log.d("MPVSubtitle", "Applying subtitle style - fg: $fg, bg: $bg, window: $window, edge: $edge, fontSize: ${style.fixedTextSize}")

      // Get current subtitle track ID and current position
      val curSid = MPVApi.getPropertyInt("sid") ?: -1
      val curTime = MPVApi.getPropertyDouble("time-pos") ?: 0.0
      // Check subtitle format/codec
      val subCodec = MPVApi.getPropertyString("track-list/$curSid/codec")
      val subTitle = MPVApi.getPropertyString("track-list/$curSid/title")
      Log.d("MPVSubtitle", "Current subtitle track ID: $curSid, time: $curTime")
      Log.d("MPVSubtitle", "Subtitle codec: $subCodec, title: $subTitle")

      // CRITICAL: For runtime subtitle styling to work, we need to:
      // 1. Set the override FIRST before any other subtitle property
      // 2. Use "yes" instead of "strip" for runtime changes (strip only works at init)
      // 3. Clear any cached subtitle state
      // 4. Force complete subtitle track reload

      try {
        // First, completely disable subtitles
        MPVApi.command(arrayOf("set", "sid", "no"))
        Log.d("MPVSubtitle", "Disabled subtitles")
      } catch (e: Exception) {
        Log.e("MPVSubtitle", "Failed to disable subtitles", e)
      }

      // Wait a moment for MPV to process the disable
      Thread.sleep(10)

      // Ensure subtitles are visible
      try {
        MPVApi.command(arrayOf("set", "sub-visibility", "yes"))
        Log.d("MPVSubtitle", "Enabled subtitle visibility")
      } catch (e: Exception) {
        Log.e("MPVSubtitle", "Failed to enable subtitle visibility", e)
      }

      // Set override mode - "yes" works better for runtime than "strip"
      try {
        MPVApi.command(arrayOf("set", "sub-ass-override", "yes"))
        Log.d("MPVSubtitle", "Set sub-ass-override to yes")
      } catch (e: Exception) {
        Log.e("MPVSubtitle", "Failed to set sub-ass-override", e)
      }

      // Apply font size first (most important)
      style.fixedTextSize?.let { sp ->
        val px = getPixels(sp)
        Log.d("MPVSubtitle", "Setting sub-font-size to $px pixels")
        try {
          MPVApi.command(arrayOf("set", "sub-font-size", px.toString()))
        } catch (e: Exception) {
          Log.e("MPVSubtitle", "Failed to set sub-font-size", e)
        }
      }

      // Apply colors
      try {
        MPVApi.command(arrayOf("set", "sub-color", fg))
        Log.d("MPVSubtitle", "Set sub-color to $fg")
      } catch (e: Exception) {
        Log.e("MPVSubtitle", "Failed to set sub-color", e)
      }

      try {
        MPVApi.command(arrayOf("set", "sub-border-color", edge))
      } catch (e: Exception) {
        Log.e("MPVSubtitle", "Failed to set sub-border-color", e)
      }

      // Border/shadow based on edge type
      when (style.edgeType) {
        CaptionStyleCompat.EDGE_TYPE_NONE -> {
          MPVApi.command(arrayOf("set", "sub-border-size", "0"))
          MPVApi.command(arrayOf("set", "sub-shadow-offset", "0"))
        }
        CaptionStyleCompat.EDGE_TYPE_OUTLINE -> {
          MPVApi.command(arrayOf("set", "sub-border-size", "2.5"))
          MPVApi.command(arrayOf("set", "sub-shadow-offset", "0"))
        }
        else -> {
          MPVApi.command(arrayOf("set", "sub-border-size", "0"))
          MPVApi.command(arrayOf("set", "sub-shadow-offset", "1.5"))
        }
      }

      // Font if specified
      val copied = copyFontToMpvFontsDir(context, style.typefaceFilePath)
      if (copied != null) {
        val fontFile = File(copied)
        try {
          MPVApi.command(arrayOf("set", "sub-font", fontFile.nameWithoutExtension))
        } catch (_: Exception) {
        }
      }

      // Additional settings
      try {
        MPVApi.command(arrayOf("set", "sub-scale", "1.0"))
        MPVApi.command(arrayOf("set", "sub-blur", "0"))
      } catch (_: Exception) {
      }

      // CRITICAL: Force MPV to reload subtitle rendering engine
      try {
        MPVApi.command(arrayOf("sub-reload"))
        Log.d("MPVSubtitle", "Reloaded subtitle rendering")
      } catch (e: Exception) {
        Log.e("MPVSubtitle", "Failed to reload subtitles", e)
      }

      // Re-enable subtitle track if it was active
      if (curSid > 0) {
        // Use a longer delay to ensure settings are applied
        Handler(Looper.getMainLooper()).postDelayed({
          try {
            MPVApi.command(arrayOf("set", "sid", curSid.toString()))
            Log.d("MPVSubtitle", "Re-enabled subtitle track $curSid")

            // Force subtitle position update to current time to refresh display
            Handler(Looper.getMainLooper()).postDelayed({
              try {
                MPVApi.command(arrayOf("seek", "0", "relative+exact"))
                Log.d("MPVSubtitle", "Refreshed subtitle display")
              } catch (_: Exception) {
              }
            }, 50)
          } catch (e: Exception) {
            Log.e("MPVSubtitle", "Failed to re-enable subtitles", e)
          }
        }, 150)
      }
    }
  }

  private fun onColorSelected(stuff: Pair<Int, Int>) {
    context?.setColor(stuff.first, stuff.second)
    if (hide)
      activity?.hideSystemUI()
  }

  @Suppress("UNUSED_PARAMETER")
  private fun onDialogDismissed(id: Int) {
    if (hide)
      activity?.hideSystemUI()
  }

  private fun Context.setColor(id: Int, color: Int?) {
    val realColor = color ?: getDefColor(id)
    when (id) {
      0 -> state.foregroundColor = realColor
      1 -> state.edgeColor = realColor
      2 -> state.backgroundColor = realColor
      3 -> state.windowColor = realColor

      else -> Unit
    }
    updateState()
  }

  private fun Context.updateState() {
    val captionStyle = CaptionStyleCompat(
        state.foregroundColor,
        state.backgroundColor,
        state.windowColor,
        state.edgeType,
        state.edgeColor,
        state.typefaceFilePath?.let {
            try {
                Typeface.createFromFile(File(it))
            } catch (_: Exception) {
                null
            }
        } ?: state.typeface?.let { ResourcesCompat.getFont(this, it) } ?: Typeface.SANS_SERIF
    )
    binding?.subtitleText?.setStyle(captionStyle)
    val text = getString(R.string.subtitles_example_text)
    val fixedText = if (state.upperCase) text.uppercase() else text
    state.fixedTextSize?.let {
      binding?.subtitleText?.setCues(
        listOf(
          Cue.Builder()
            .setTextSize(
              getPixels(it).toFloat(),
              Cue.TEXT_SIZE_TYPE_ABSOLUTE
            )
            .setText(fixedText)
            .build()
        )
      )
    }

    binding?.applyBtt?.callOnClick()
  }

  private fun getColor(id: Int): Int {
    val color = when (id) {
      0 -> state.foregroundColor
      1 -> state.edgeColor
      2 -> state.backgroundColor
      3 -> state.windowColor

      else -> Color.TRANSPARENT
    }

    return if (color == Color.TRANSPARENT) Color.BLACK else color
  }

  override fun onDestroyView() {
    binding = null
    super.onDestroyView()
  }

  var binding: FragmentSubtitleSettingsBinding? = null
  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?
  ): View {
    val localBinding = FragmentSubtitleSettingsBinding.inflate(inflater, container, false)
    binding = localBinding
    return localBinding.root
  }

  private lateinit var state: SaveCaptionStyle
  private var hide: Boolean = true

  override fun onDestroy() {
    super.onDestroy()
    GlobalEvent.onColorSelectedEvent -= ::onColorSelected
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    hide = arguments?.getBoolean("hide") ?: true
    GlobalEvent.onColorSelectedEvent += ::onColorSelected
    GlobalEvent.onDialogDismissedEvent += ::onDialogDismissed
    binding?.subsImportText?.text = getString(R.string.subs_import_text).format(
      context?.filesDir?.absolutePath.toString() + "/fonts"
    )

    val settingsToolbar = view.findViewById<MaterialToolbar>(R.id.subtitle_settings_toolbar) ?: return
    settingsToolbar.apply {
      setTitle(title)
      setNavigationIcon(R.drawable.ic_baseline_arrow_back_24)
      children.firstOrNull { it is ImageView }?.tag = getString(R.string.tv_no_focus_tag)
      setNavigationOnClickListener { activity?.onBackPressedDispatcher?.onBackPressed() }
    }

      UIHelper.fixPaddingStatusbar(binding?.subsRoot)

    state = requireContext().getCurrentSavedStyle()
    context?.updateState()

    val isTvTrueSettings = requireActivity().isLayout(LayoutMode.Tv.id)
    fun View.setFocusableInTv() { this.isFocusableInTouchMode = isTvTrueSettings }

    fun View.setup(id: Int) {
      setFocusableInTv()
      this.setOnClickListener {
        activity?.let {
          ColorPickerDialog.newBuilder()
            .setDialogId(id)
            .setShowAlphaSlider(true)
            .setColor(getColor(id))
            .setAllowCustom(!context.isLayout(LayoutMode.Tv.id))
            .show(it)
        }
      }

      this.setOnLongClickListener {
        it.context.setColor(id, null)
          Utils.showToast(R.string.subs_default_reset_toast, Toast.LENGTH_SHORT)
        return@setOnLongClickListener true
      }
    }

    binding?.apply {
      subsTextColor.setup(0)
      subsOutlineColor.setup(1)
      subsBackgroundColor.setup(2)
      subsWindowColor.setup(3)

      val dismissCallback = { if (hide) activity?.hideSystemUI() }

      subsSubtitleElevation.setOnClickListener { textView ->
        // reuse same UI logic as original fragment for elevation selection
        val suffix = "dp"
        val elevationTypes = listOf(
          Pair(0, textView.context.getString(R.string.none)),
          Pair(10, "10$suffix"),
          Pair(20, "20$suffix"),
          Pair(30, "30$suffix"),
        )
        activity.showDialog(
          elevationTypes.map { it.second },
          elevationTypes.map { it.first }.indexOf(state.elevation),
          (textView as TextView).text.toString(),
          false,
          dismissCallback
        ) { index ->
          state.elevation = elevationTypes.map { it.first }[index]
          textView.context.updateState()
        }
      }

      subsFontSize.setOnClickListener { textView ->
        val suffix = "sp"
        val fontSizes = listOf(
          Pair(null, textView.context.getString(R.string.normal)),
          Pair(16f, "16$suffix"),
          Pair(20f, "20$suffix"),
          Pair(24f, "24$suffix"),
          Pair(28f, "28$suffix"),
        )
        activity?.showDialog(
          fontSizes.map { it.second },
          fontSizes.map { it.first }.indexOf(state.fixedTextSize),
          (textView as TextView).text.toString(),
          false,
          dismissCallback
        ) { index ->
          state.fixedTextSize = fontSizes.map { it.first }[index]
          textView.context.updateState()
        }
      }

      subsFont.setOnClickListener { textView ->
        val fontTypes = listOf(
          Pair(null, textView.context.getString(R.string.normal)),
          Pair(R.font.trebuchet_ms, "Trebuchet MS"),
          Pair(R.font.open_sans, "Open Sans"),
        )
        val savedFontTypes = textView.context.getSavedFonts()
        val savedNames = savedFontTypes.map { it.name }

        val fontList = fontTypes.map { it.second } + savedNames
        val currentIndex = if (state.typefaceFilePath != null) {
          val idx = savedFontTypes.indexOfFirst { it.absolutePath == state.typefaceFilePath }
          if (idx >= 0) fontTypes.size + idx else fontTypes.indexOfFirst { it.first == state.typeface }
        } else fontTypes.indexOfFirst { it.first == state.typeface }

        activity?.showDialog(
          fontList,
          currentIndex,
          (textView as TextView).text.toString(),
          false,
          dismissCallback
        ) { index ->
          if (index < fontTypes.size) {
            state.typeface = fontTypes[index].first
            state.typefaceFilePath = null
          } else {
            val idx = index - fontTypes.size
            state.typefaceFilePath = savedFontTypes.getOrNull(idx)?.absolutePath
            state.typeface = null
          }
          textView.context.updateState()
        }
      }

      cancelBtt.setOnClickListener { activity?.onBackPressedDispatcher?.onBackPressed() }

      applyBtt.setOnClickListener {
        // Save locally and apply to mpv
        context?.setKey("subtitle_settings", state)
        applyToMPV(requireContext(), state)
        applyStyleEvent.invoke(state)
      }

      resetAllSubtitleSettings.setOnClickListener {
        state = SaveCaptionStyle(
            getDefColor(0),
            getDefColor(2),
            getDefColor(3),
            CaptionStyleCompat.EDGE_TYPE_OUTLINE,
            getDefColor(1),
            null,
            null,
            20,
            25.0f,
        )
        applyBtt.callOnClick()
        subtitleText.context.updateState()
      }
    }
  }

}
