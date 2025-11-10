package cloud.app.csplayer.ui.subtitles

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.media3.common.text.Cue
import cloud.app.csplayer.R
import cloud.app.csplayer.databinding.ChromecastSubtitleSettingsBinding
import cloud.app.csplayer.utils.DataStore.getKey
import cloud.app.csplayer.utils.DataStore.setKey
import cloud.app.csplayer.utils.Event
import cloud.app.csplayer.utils.GlobalEvent.onColorSelectedEvent
import cloud.app.csplayer.utils.GlobalEvent.onDialogDismissedEvent
import cloud.app.csplayer.utils.UIHelper.navigate
import cloud.app.csplayer.utils.Utils.showToast
import cloud.app.csplayer.utils.hideSystemUI
import cloud.app.csplayer.utils.isTvOrEmulator
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.google.android.gms.cast.TextTrackStyle
import com.google.android.gms.cast.TextTrackStyle.*
import cloud.app.csplayer.ui.colorpicker.ColorPickerDialog
import cloud.app.csplayer.ui.dialog.SelectionDialog
import cloud.app.csplayer.utils.Utils

const val CHROME_SUBTITLE_KEY = "chome_subtitle_settings"

@Serializable
data class SaveChromeCaptionStyle(
  @SerialName("fontFamily") var fontFamily: String? = null,
  @SerialName("fontGenericFamily") var fontGenericFamily: Int? = null,
  @SerialName("backgroundColor") var backgroundColor: Int = 0x00FFFFFF, // transparent
  @SerialName("edgeColor") var edgeColor: Int = Color.BLACK, // BLACK
  @SerialName("edgeType") var edgeType: Int = TextTrackStyle.EDGE_TYPE_OUTLINE,
  @SerialName("foregroundColor") var foregroundColor: Int = Color.WHITE,
  @SerialName("fontScale") var fontScale: Float = 1.05f,
  @SerialName("windowColor") var windowColor: Int = Color.TRANSPARENT,
)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class ChromecastSubtitlesFragment : Fragment() {
  companion object {
    val applyStyleEvent = Event<SaveChromeCaptionStyle>()
    fun push(activity: Activity?, hide: Boolean = true) {
      activity.navigate(R.id.global_to_navigation_chrome_subtitles, Bundle().apply {
        putBoolean("hide", hide)
      })
    }

    private fun getDefColor(id: Int): Int {
      return when (id) {
        0 -> Color.WHITE
        1 -> Color.BLACK
        2 -> Color.TRANSPARENT
        3 -> Color.TRANSPARENT
        else -> Color.TRANSPARENT
      }
    }

    fun Context.saveStyle(style: SaveChromeCaptionStyle) {
      this.setKey(CHROME_SUBTITLE_KEY, style)
    }

    private fun getPixels(unit: Int, size: Float): Int {
      val metrics: DisplayMetrics = Resources.getSystem().displayMetrics
      return TypedValue.applyDimension(unit, size, metrics).toInt()
    }

    fun getCurrentSavedStyle(): SaveChromeCaptionStyle {
      return Utils.activity?.getKey(CHROME_SUBTITLE_KEY) ?: defaultState
    }

    private val defaultState = SaveChromeCaptionStyle()
  }

  private fun onColorSelected(stuff: Pair<Int, Int>) {
    context?.setColor(stuff.first, stuff.second)
    if (hide)
      activity?.hideSystemUI()
  }

  private fun onDialogDismissed(id: Int) {
    if (hide)
      activity?.hideSystemUI()
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
    //subtitle_text?.setStyle(fromSaveToStyle(state))
  }

  var binding: ChromecastSubtitleSettingsBinding? = null

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View {
    val localBinding = ChromecastSubtitleSettingsBinding.inflate(inflater, container, false)
    binding = localBinding
    return localBinding.root//inflater.inflate(R.layout.chromecast_subtitle_settings, container, false)
  }

  override fun onDestroyView() {
    binding = null
    super.onDestroyView()
  }

  private lateinit var state: SaveChromeCaptionStyle
  private var hide: Boolean = true

  override fun onDestroy() {
    super.onDestroy()
    onColorSelectedEvent -= ::onColorSelected
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    hide = arguments?.getBoolean("hide") ?: true
    onColorSelectedEvent += ::onColorSelected
    onDialogDismissedEvent += ::onDialogDismissed

    state = getCurrentSavedStyle()
    context?.updateState()

    val isTvSettings = requireActivity().isTvOrEmulator()

    fun View.setFocusableInTv() {
      this.isFocusableInTouchMode = isTvSettings
    }

    fun View.setup(id: Int) {
      setFocusableInTv()

      this.setOnClickListener {
        activity?.let {
          ColorPickerDialog.newBuilder()
            .setDialogId(id)
            .setShowAlphaSlider(true)
            .setColor(getColor(id))
            .show(it)
        }
      }

      this.setOnLongClickListener {
        it.context.setColor(id, null)
        showToast(R.string.subs_default_reset_toast, Toast.LENGTH_SHORT)
        return@setOnLongClickListener true
      }
    }

    binding?.apply {
      subsTextColor.setup(0)
      subsOutlineColor.setup(1)
      subsBackgroundColor.setup(2)
    }


    val dismissCallback = {
      if (hide)
        activity?.hideSystemUI()
    }

    binding?.subsEdgeType?.setFocusableInTv()
    binding?.subsEdgeType?.setOnClickListener { textView ->
      val edgeTypes = listOf(
        Pair(
          EDGE_TYPE_NONE,
          textView.context.getString(R.string.subtitles_none)
        ),
        Pair(
          EDGE_TYPE_OUTLINE,
          textView.context.getString(R.string.subtitles_outline)
        ),
        Pair(
          EDGE_TYPE_DEPRESSED,
          textView.context.getString(R.string.subtitles_depressed)
        ),
        Pair(
          EDGE_TYPE_DROP_SHADOW,
          textView.context.getString(R.string.subtitles_shadow)
        ),
        Pair(
          EDGE_TYPE_RAISED,
          textView.context.getString(R.string.subtitles_raised)
        ),
      )
      //showBottomDialog
      SelectionDialog.single(
        edgeTypes.map { it.second },
        edgeTypes.map { it.first }.indexOf(state.edgeType),
        (textView as TextView).text.toString(),
        false
      ).show(parentFragmentManager) { bundle ->
        bundle?.apply {
          getIntegerArrayList(SelectionDialog.ITEMS_SELECTED)?.get(0)?.let { index ->
            state.edgeType = edgeTypes.map { it.first }[index]
            textView.context.updateState()
          }
          dismissCallback()
        }
      }
    }

    binding?.subsEdgeType?.setOnLongClickListener {
      state.edgeType = defaultState.edgeType
      it.context.updateState()
      showToast(R.string.subs_default_reset_toast, Toast.LENGTH_SHORT)
      return@setOnLongClickListener true
    }

    binding?.subsFontSize?.setFocusableInTv()
    binding?.subsFontSize?.setOnClickListener { textView ->
      val fontSizes = listOf(
        Pair(0.75f, "75%"),
        Pair(0.80f, "80%"),
        Pair(0.85f, "85%"),
        Pair(0.90f, "90%"),
        Pair(0.95f, "95%"),
        Pair(1.00f, "100%"),
        Pair(1.05f, textView.context.getString(R.string.normal)),
        Pair(1.10f, "110%"),
        Pair(1.15f, "115%"),
        Pair(1.20f, "120%"),
        Pair(1.25f, "125%"),
        Pair(1.30f, "130%"),
        Pair(1.35f, "135%"),
        Pair(1.40f, "140%"),
        Pair(1.45f, "145%"),
        Pair(1.50f, "150%"),
      )

      //showBottomDialog
      SelectionDialog.single(
        fontSizes.map { it.second },
        fontSizes.map { it.first }.indexOf(state.fontScale),
        (textView as TextView).text.toString(),
        false
      ).show(parentFragmentManager) { bundle ->
        bundle?.apply {
          getIntegerArrayList(SelectionDialog.ITEMS_SELECTED)?.get(0)?.let { index ->
            state.fontScale = fontSizes.map { it.first }[index]
            //textView.context.updateState() // font size not changed
          }
        }
        dismissCallback()
      }
    }

    binding?.subsFontSize?.setOnLongClickListener { _ ->
      state.fontScale = defaultState.fontScale
      //textView.context.updateState() // font size not changed
      showToast(R.string.subs_default_reset_toast, Toast.LENGTH_SHORT)
      return@setOnLongClickListener true
    }

    binding?.subsFont?.setFocusableInTv()
    binding?.subsFont?.setOnClickListener { textView ->
      val fontTypes = listOf(
        null to textView.context.getString(R.string.normal),
        "Droid Sans" to "Droid Sans",
        "Droid Sans Mono" to "Droid Sans Mono",
        "Droid Serif Regular" to "Droid Serif Regular",
        "Cutive Mono" to "Cutive Mono",
        "Short Stack" to "Short Stack",
        "Quintessential" to "Quintessential",
        "Alegreya Sans SC" to "Alegreya Sans SC",
      )

      //showBottomDialog
      SelectionDialog.single(
        fontTypes.map { it.second },
        fontTypes.map { it.first }.indexOf(state.fontFamily),
        (textView as TextView).text.toString(),
        false
      ).show(parentFragmentManager) { bundle ->
        bundle?.apply {
          getIntegerArrayList(SelectionDialog.ITEMS_SELECTED)?.get(0)?.let { index ->
            state.fontFamily = fontTypes.map { it.first }[index]
            textView.context.updateState()
          }
          dismissCallback()
        }
      }
    }

    binding?.subsFont?.setOnLongClickListener { textView ->
      state.fontFamily = defaultState.fontFamily
      textView.context.updateState()
      showToast(R.string.subs_default_reset_toast, Toast.LENGTH_SHORT)
      return@setOnLongClickListener true
    }

    binding?.cancelBtt?.setOnClickListener {
      activity?.onBackPressedDispatcher?.onBackPressed()
    }

    binding?.applyBtt?.setOnClickListener {
      it.context.saveStyle(state)
      applyStyleEvent.invoke(state)
      //it.context.fromSaveToStyle(state)
      activity?.onBackPressedDispatcher?.onBackPressed()
    }
    binding?.subtitleText?.apply {
      setCues(
        listOf(
          Cue.Builder()
            .setTextSize(
              getPixels(TypedValue.COMPLEX_UNIT_SP, 25.0f).toFloat(),
              Cue.TEXT_SIZE_TYPE_ABSOLUTE
            )
            .setText(context.getString(R.string.subtitles_example_text))
            .build()
        )
      )
    }
  }
}

