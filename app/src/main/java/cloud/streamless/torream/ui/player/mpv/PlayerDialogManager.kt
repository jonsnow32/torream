package cloud.streamless.torream.ui.player.mpv

import android.app.Dialog
import android.content.SharedPreferences
import android.text.Editable
import android.view.LayoutInflater
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import cloud.streamless.torream.R
import cloud.streamless.torream.databinding.BottomSheetEqualizerBinding
import cloud.streamless.torream.databinding.SubtitleOffsetBinding
import cloud.streamless.torream.model.SubtitleData
import cloud.streamless.torream.model.VideoLink
import cloud.streamless.torream.ui.dialog.SelectionDialog
import cloud.streamless.torream.utils.UIHelper.dismissSafe
import com.google.android.material.chip.Chip
import kotlin.math.max

/**
 * Manages all player dialogs (speed, sources, tracks, subtitles, etc.)
 * Handles dialog lifecycle and integrates with MPV player
 */
class PlayerDialogManager(
  private val fragment: Fragment,
  private val onShowDialog: (() -> Unit)? = null,
  private val onDismissDialog: (() -> Unit)? = null
) {
  private var currentDialog: Dialog? = null

  // Dialog references for cleanup
  var selectSourceDialog: Dialog? = null
  var selectTrackDialog: Dialog? = null
  var selectVideoDialog: Dialog? = null

  /**
   * Show playback speed selection dialog
   */
  fun showSpeedDialog(currentSpeed: Float, onSpeedSelected: (Float) -> Unit) {
    onShowDialog?.invoke()
    val speedsText = listOf("0.5x", "0.75x", "1x", "1.25x", "1.5x", "1.75x", "2x")
    val speedsNumbers = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    val speedIndex = speedsNumbers.indexOf(currentSpeed)

    val dialog = SelectionDialog.single(
      speedsText,
      speedIndex,
      fragment.getString(R.string.player_speed),
      true
    )
    dialog.show(fragment.parentFragmentManager) { bundle ->
      bundle?.let {
        it.getIntegerArrayList(SelectionDialog.ITEMS_SELECTED)?.get(0)?.apply {
          onSpeedSelected(speedsNumbers[this])
        }
      }
      onDismissDialog?.invoke()
    }
  }

  /**
   * Show codec/decoder selection dialog
   */
  fun showCodecsDialog(currentCodec: String, onCodecSelected: (String) -> Unit) {
    onShowDialog?.invoke()
    val codecs = listOf("auto", "no", "auto-safe", "auto-copy", "mediacodec", "mediacodec-copy")
    val codecsDisplay = listOf(
      "Hardware (auto)",
      "Software",
      "Hardware (safe)",
      "Hardware (copy)",
      "MediaCodec",
      "MediaCodec (copy)"
    )

    val currentIndex = codecs.indexOf(currentCodec).coerceAtLeast(0)

    val dialog = SelectionDialog.single(
      codecsDisplay,
      currentIndex,
      fragment.getString(R.string.codec),
      true
    )
    dialog.show(fragment.parentFragmentManager) { bundle ->
      bundle?.let {
        it.getIntegerArrayList(SelectionDialog.ITEMS_SELECTED)?.get(0)?.apply {
          onCodecSelected(codecs[this])
        }
      }
      onDismissDialog?.invoke()
    }
  }

  /**
   * Show video source selection dialog
   */
  fun showSourcesDialog(
    allLinks: List<VideoLink>,
    currentSelectedLink: VideoLink?,
    currentSubs: Set<SubtitleData>,
    onSourceSelected: (VideoLink, SubtitleData?) -> Unit,
    onDismiss: () -> Unit
  ) {
    onShowDialog?.invoke()
    val sourceIndex = currentSelectedLink?.let { allLinks.indexOf(it) } ?: 0
    val dialog = SelectionDialog.single(
      allLinks.mapIndexed { index, link ->
        "${index + 1}. ${link.name}"
      },
      sourceIndex,
      fragment.getString(R.string.pick_source),
      true
    )
    dialog.show(fragment.parentFragmentManager) { bundle ->
      bundle?.let {
        it.getIntegerArrayList(SelectionDialog.ITEMS_SELECTED)?.get(0)?.apply {
          allLinks.getOrNull(this)?.let { item ->
            onSourceSelected(item, null)
          }
        }
      }
      onDismissDialog?.invoke()
      onDismiss()
    }
  }

  /**
   * Show video tracks selection dialog
   */
  fun showVideoTracksDialog(
    tracks: Map<String, List<MPVView.Track>>,
    onTrackSelected: (Int) -> Unit,
    onDismiss: () -> Unit
  ) {
    onShowDialog?.invoke()
    val currentVideoTracks = tracks["video"]
    if (currentVideoTracks == null) {
      Toast.makeText(fragment.requireActivity(), "No video tracks available", Toast.LENGTH_SHORT)
        .show()
      onDismissDialog?.invoke()
      return
    }
    val videoIndex = max((currentVideoTracks.indexOfFirst { it.selected }), 0)
    val dialog = SelectionDialog.single(
      currentVideoTracks.map { it.name },
      videoIndex,
      fragment.getString(R.string.video_tracks),
      true
    )
    dialog.show(fragment.parentFragmentManager) { bundle ->
      bundle?.let {
        it.getIntegerArrayList(SelectionDialog.ITEMS_SELECTED)?.get(0)?.apply {
          onTrackSelected(this)
        }
      }
      onDismissDialog?.invoke()
      onDismiss()
    }
  }

  /**
   * Show audio tracks selection dialog
   */
  fun showAudioTracksDialog(
    tracks: Map<String, List<MPVView.Track>>,
    onAudioSelected: (audioIndex: Int) -> Unit,
    onDismiss: () -> Unit
  ) {
    onShowDialog?.invoke()
    val ctx = fragment.activity ?: return
    val currentAudioTracks = tracks["audio"]
    if (currentAudioTracks == null || currentAudioTracks.isEmpty()) {
      Toast.makeText(ctx, "No audio tracks available", Toast.LENGTH_SHORT)
        .show()
      onDismissDialog?.invoke()
      return
    }
    val audioIndex = max((currentAudioTracks.indexOfFirst { it.selected }), 0)
    val dialog = SelectionDialog.single(
      currentAudioTracks.map { it.name },
      audioIndex,
      ctx.getString(R.string.video_tracks),
      true
    )
    dialog.show(fragment.parentFragmentManager) { bundle ->
      bundle?.let {
        it.getIntegerArrayList(SelectionDialog.ITEMS_SELECTED)?.get(0)?.apply {
          onAudioSelected(this)
        }
      }
      onDismissDialog?.invoke()
      onDismiss()
    }
  }

  /**
   * Show subtitle/text tracks selection dialog
   */
  fun showSubtitleTracksDialog(
    tracks: Map<String, List<MPVView.Track>>,
    onSubtitleSelected: (subtitleIndex: Int) -> Unit,
    onLoadSubtitlesFromFile: () -> Unit,
    onLoadSubtitlesOnline: () -> Unit,
    onTranslateSubtitle: () -> Unit,
    onDismiss: () -> Unit
  ) {
    onShowDialog?.invoke()
    val ctx = fragment.activity ?: return
    val currentSubtitleTracks = tracks["sub"]
    if (currentSubtitleTracks == null || currentSubtitleTracks.isEmpty()) {
      Toast.makeText(ctx, "No subtitle tracks available", Toast.LENGTH_SHORT).show()
      onDismissDialog?.invoke()
      return
    }

    val subtitleIndex = max((currentSubtitleTracks.indexOfFirst { it.selected }), 0)
    val dialog = SelectionDialog.single(
      currentSubtitleTracks.map { it.name } + listOf<String>(
        ctx.getString(R.string.load_from_file),
        ctx.getString(R.string.load_from_network),
        ctx.getString(R.string.translate_subtitle)
      ),
      subtitleIndex,
      fragment.getString(R.string.subtitle),
      false
    )
    dialog.show(fragment.parentFragmentManager) { bundle ->
      bundle?.let {
        it.getIntegerArrayList(SelectionDialog.ITEMS_SELECTED)?.get(0)?.let { index ->
          if (index == currentSubtitleTracks.size) {
            onLoadSubtitlesFromFile()
            return@let
          } else if (index == currentSubtitleTracks.size + 1) {
            onLoadSubtitlesOnline()
            return@let
          } else if (index == currentSubtitleTracks.size + 2) {
            onTranslateSubtitle()
            return@let
          } else {
            onSubtitleSelected(index)
          }

        }
      }
      onDismissDialog?.invoke()
      onDismiss()
    }
  }

  /**
   * Show subtitle offset/delay adjustment dialog
   */
  fun showSubtitleOffsetDialog(
    currentOffset: Long,
    onOffsetChanged: (Long) -> Unit,
    onReset: () -> Unit,
    onCancel: (Long) -> Unit,
    onDismiss: () -> Unit
  ) {
    val ctx = fragment.activity ?: return
    val binding = SubtitleOffsetBinding.inflate(LayoutInflater.from(ctx), null, false)

    val builder = AlertDialog.Builder(ctx, R.style.BaseMaterialDialogTheme)
      .setView(binding.root)
    val dialog = builder.create()
    onShowDialog?.invoke()
    dialog.show()

    binding.apply {
      subtitleOffsetInput.doOnTextChanged { text, _, _, _ ->
        text?.toString()?.toLongOrNull()?.let { time ->
          onOffsetChanged(time)
          val str = when {
            time > 0L -> ctx.getString(R.string.subtitle_offset_extra_hint_later_format, time)
            time < 0L -> ctx.getString(R.string.subtitle_offset_extra_hint_before_format, -time)
            else -> ctx.getString(R.string.subtitle_offset_extra_hint_none_format)
          }
          subtitleOffsetSubTitle.text = str
        }
      }

      subtitleOffsetInput.text =
        Editable.Factory.getInstance()?.newEditable(currentOffset.toString())

      fun changeBy(by: Long) {
        val current = (subtitleOffsetInput.text?.toString()?.toLongOrNull() ?: 0) + by
        subtitleOffsetInput.text = Editable.Factory.getInstance()?.newEditable(current.toString())
      }

      subtitleOffsetAdd.setOnClickListener { changeBy(100L) }
      subtitleOffsetAddMore.setOnClickListener { changeBy(1000L) }
      subtitleOffsetSubtract.setOnClickListener { changeBy(-100L) }
      subtitleOffsetSubtractMore.setOnClickListener { changeBy(-1000L) }

      dialog.setOnDismissListener {
        onDismissDialog?.invoke()
        onDismiss()
      }

      applyBtt.setOnClickListener {
        dialog.dismissSafe(ctx)
      }

      resetBtt.setOnClickListener {
        onReset()
        dialog.dismissSafe(ctx)
      }

      cancelBtt.setOnClickListener {
        onCancel(currentOffset)
        dialog.dismissSafe(ctx)
      }
    }

    currentDialog = dialog
  }

  // ========== Equalizer ==========

  private val eqPresets = linkedMapOf(
    "Flat"         to floatArrayOf(0f, 0f, 0f, 0f, 0f),
    "Bass Boost"   to floatArrayOf(8f, 4f, 0f, 0f, 0f),
    "Treble Boost" to floatArrayOf(0f, 0f, 0f, 4f, 8f),
    "Voice"        to floatArrayOf(0f, 4f, 2f, 4f, 0f),
    "Movie"        to floatArrayOf(4f, 0f, -2f, 0f, 4f)
  )

  private fun progressToDb(progress: Int) = (progress - 12).toFloat()
  private fun dbToProgress(db: Float) = (db + 12).toInt().coerceIn(0, 24)
  private fun dbLabel(db: Float) = if (db >= 0) "+${db.toInt()}" else "${db.toInt()}"

  fun loadEqGains(prefs: SharedPreferences): FloatArray {
    val raw = prefs.getString("eq_bands", null) ?: return FloatArray(5)
    return try {
      val arr = raw.split(",").map { it.toFloat() }.toFloatArray()
      if (arr.size == 5) arr else FloatArray(5)
    } catch (_: Exception) { FloatArray(5) }
  }

  fun showEqualizerDialog(
    prefs: SharedPreferences,
    onChanged: (gains: FloatArray, enabled: Boolean) -> Unit
  ) {
    val ctx = fragment.activity ?: return
    val binding = BottomSheetEqualizerBinding.inflate(LayoutInflater.from(ctx))
    onShowDialog?.invoke()

    val gains = loadEqGains(prefs).copyOf()
    var eqEnabled = prefs.getBoolean("eq_enabled", true)

    val bands = listOf(binding.eqBand0, binding.eqBand1, binding.eqBand2, binding.eqBand3, binding.eqBand4)
    val labels = listOf(binding.eqBand0Val, binding.eqBand1Val, binding.eqBand2Val, binding.eqBand3Val, binding.eqBand4Val)

    fun save() {
      prefs.edit()
        .putString("eq_bands", gains.joinToString(","))
        .putBoolean("eq_enabled", eqEnabled)
        .apply()
      onChanged(gains.copyOf(), eqEnabled)
    }

    fun syncChips(matchGains: FloatArray?) {
      (0 until binding.eqPresetChipGroup.childCount).forEach { j ->
        val chip = binding.eqPresetChipGroup.getChildAt(j) as? Chip ?: return@forEach
        chip.isChecked = matchGains != null && eqPresets[chip.text.toString()]?.contentEquals(matchGains) == true
      }
    }

    fun updateBandsAlpha() {
      binding.eqBandsContainer.alpha = if (eqEnabled) 1f else 0.4f
      bands.forEach { it.isEnabled = eqEnabled }
      binding.eqPresetChipGroup.alpha = if (eqEnabled) 1f else 0.4f
      (0 until binding.eqPresetChipGroup.childCount).forEach { j ->
        binding.eqPresetChipGroup.getChildAt(j)?.isEnabled = eqEnabled
      }
    }

    // Init band sliders from saved gains
    bands.forEachIndexed { i, sb ->
      sb.progress = dbToProgress(gains[i])
      labels[i].text = dbLabel(gains[i])
    }

    // SeekBar listeners — real-time label update, save on release
    bands.forEachIndexed { i, sb ->
      sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
          if (!fromUser) return
          gains[i] = progressToDb(progress)
          labels[i].text = dbLabel(gains[i])
          syncChips(null)
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) { save() }
      })
    }

    // Preset chips
    eqPresets.forEach { (name, presetGains) ->
      val chip = Chip(ctx).apply {
        text = name
        isCheckable = true
        isChecked = presetGains.contentEquals(gains)
      }
      chip.setOnClickListener {
        presetGains.copyInto(gains)
        bands.forEachIndexed { i, sb -> sb.progress = dbToProgress(gains[i]) }
        labels.forEachIndexed { i, tv -> tv.text = dbLabel(gains[i]) }
        syncChips(gains)
        save()
      }
      binding.eqPresetChipGroup.addView(chip)
    }

    // Enable/disable switch
    binding.eqEnabledSwitch.isChecked = eqEnabled
    binding.eqEnabledSwitch.setOnCheckedChangeListener { _, checked ->
      eqEnabled = checked
      updateBandsAlpha()
      save()
    }
    updateBandsAlpha()

    val dialog = AlertDialog.Builder(ctx, R.style.BaseMaterialDialogTheme)
      .setView(binding.root)
      .setOnDismissListener { onDismissDialog?.invoke() }
      .create()

    currentDialog = dialog
    dialog.show()

    // Fixed width — prevents stretch in landscape, consistent on all screen sizes
    val dm = ctx.resources.displayMetrics
    val targetPx = (380 * dm.density).toInt()
    val maxPx = dm.widthPixels - (32 * dm.density).toInt()
    dialog.window?.setLayout(minOf(targetPx, maxPx), android.view.WindowManager.LayoutParams.WRAP_CONTENT)
  }

  /**
   * Dismiss the current dialog
   */
  fun dismissCurrentDialog() {
    currentDialog?.dismiss()
    currentDialog = null
  }

  /**
   * Dismiss all dialogs
   */
  fun dismissAllDialogs() {
    currentDialog?.dismiss()
    selectSourceDialog?.dismiss()
    selectTrackDialog?.dismiss()
    selectVideoDialog?.dismiss()

    currentDialog = null
    selectSourceDialog = null
    selectTrackDialog = null
    selectVideoDialog = null
  }

  /**
   * Show a toast message
   */
  fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    val context = fragment.activity ?: return
    Toast.makeText(context, message, duration).show()
  }

  /**
   * Show a toast message from resource ID
   */
  fun showToast(messageResId: Int, duration: Int = Toast.LENGTH_SHORT) {
    val context = fragment.activity ?: return
    Toast.makeText(context, messageResId, duration).show()
  }
}
