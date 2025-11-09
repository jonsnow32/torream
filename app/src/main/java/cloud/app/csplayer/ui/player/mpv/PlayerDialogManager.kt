package cloud.app.csplayer.ui.player.mpv

import android.app.Dialog
import android.text.Editable
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import cloud.app.csplayer.R
import cloud.app.csplayer.databinding.SubtitleOffsetBinding
import cloud.app.csplayer.model.SubtitleData
import cloud.app.csplayer.model.VideoLink
import cloud.app.csplayer.ui.dialog.SelectionDialog
import cloud.app.csplayer.utils.UIHelper.dismissSafe
import kotlin.math.max

/**
 * Manages all player dialogs (speed, sources, tracks, subtitles, etc.)
 * Handles dialog lifecycle and integrates with MPV player
 */
class PlayerDialogManager(
  private val fragment: Fragment
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
    val speedsText = listOf("0.5x", "0.75x", "1x", "1.25x", "1.5x", "1.75x", "2x")
    val speedsNumbers = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    val speedIndex = speedsNumbers.indexOf(currentSpeed)

    SelectionDialog.single(
      speedsText,
      speedIndex,
      fragment.getString(R.string.player_speed),
      true
    ).show(fragment.parentFragmentManager) { bundle ->
      bundle?.let {
        it.getIntegerArrayList(SelectionDialog.ITEMS_SELECTED)?.get(0)?.apply {
          onSpeedSelected(speedsNumbers[this])
        }
      }
    }
  }

  /**
   * Show codec/decoder selection dialog
   */
  fun showCodecsDialog(currentCodec: String, onCodecSelected: (String) -> Unit) {
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

    SelectionDialog.single(
      codecsDisplay,
      currentIndex,
      fragment.getString(R.string.codec),
      true
    ).show(fragment.parentFragmentManager) { bundle ->
      bundle?.let {
        it.getIntegerArrayList(SelectionDialog.ITEMS_SELECTED)?.get(0)?.apply {
          onCodecSelected(codecs[this])
        }
      }
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

    val sourceIndex = currentSelectedLink?.let { allLinks.indexOf(it) } ?: 0
    SelectionDialog.single(
      allLinks.mapIndexed { index, link ->
        "${index + 1}. ${link.name}"
      },
      sourceIndex,
      fragment.getString(R.string.pick_source),
      true
    ).show(fragment.parentFragmentManager) { bundle ->
      bundle?.let {
        it.getIntegerArrayList(SelectionDialog.ITEMS_SELECTED)?.get(0)?.apply {
          allLinks.getOrNull(this)?.let { item ->
            onSourceSelected(item, null)
          }
        }
      }
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
    val currentVideoTracks = tracks["video"]
    if (currentVideoTracks == null) {
      Toast.makeText(fragment.requireActivity(), "No video tracks available", Toast.LENGTH_SHORT)
        .show()
      return
    }
    val videoIndex = max((currentVideoTracks.indexOfFirst { it.selected }), 0)
    SelectionDialog.single(
      currentVideoTracks.map { it.name },
      videoIndex,
      fragment.getString(R.string.video_tracks),
      true
    ).show(fragment.parentFragmentManager) { bundle ->
      bundle?.let {
        it.getIntegerArrayList(SelectionDialog.ITEMS_SELECTED)?.get(0)?.apply {
          onTrackSelected(this)
        }
      }
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
    val ctx = fragment.activity ?: return
    val currentAudioTracks = tracks["audio"]
    if (currentAudioTracks == null || currentAudioTracks.isEmpty()) {
      Toast.makeText(ctx, "No audio tracks available", Toast.LENGTH_SHORT)
        .show()
      return
    }
    val audioIndex = max((currentAudioTracks.indexOfFirst { it.selected }), 0)
    SelectionDialog.single(
      currentAudioTracks.map { it.name },
      audioIndex,
      ctx.getString(R.string.video_tracks),
      true
    ).show(fragment.parentFragmentManager) { bundle ->
      bundle?.let {
        it.getIntegerArrayList(SelectionDialog.ITEMS_SELECTED)?.get(0)?.apply {
          onAudioSelected(this)
        }
      }
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
    onDismiss: () -> Unit
  ) {
    val ctx = fragment.activity ?: return
    val currentSubtitleTracks = tracks["sub"]
    if (currentSubtitleTracks == null || currentSubtitleTracks.isEmpty()) {
      Toast.makeText(ctx, "No subtitle tracks available", Toast.LENGTH_SHORT).show()
      return
    }

    val subtitleIndex = max((currentSubtitleTracks.indexOfFirst { it.selected }), 0)
    SelectionDialog.single(
      currentSubtitleTracks.map { it.name } + listOf<String>("Load from file", "Load from network"),
      subtitleIndex,
      fragment.getString(R.string.video_tracks),
      true
    ).show(fragment.parentFragmentManager) { bundle ->
      bundle?.let {
        it.getIntegerArrayList(SelectionDialog.ITEMS_SELECTED)?.get(0)?.let { index ->
          if (index == currentSubtitleTracks.size) {
            onLoadSubtitlesFromFile()
            return@let
          } else if (index == currentSubtitleTracks.size + 1) {
            onLoadSubtitlesOnline()
            return@let
          } else {
            onSubtitleSelected(index)
          }

        }
      }
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

    val builder = AlertDialog.Builder(ctx)
      .setView(binding.root)
    val dialog = builder.create()
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

