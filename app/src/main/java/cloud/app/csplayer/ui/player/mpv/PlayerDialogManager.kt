package cloud.app.csplayer.ui.player.mpv

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import cloud.app.csplayer.R
import cloud.app.csplayer.databinding.PlayerSelectSourceAndSubsBinding
import cloud.app.csplayer.databinding.PlayerSelectVideoTracksBinding
import cloud.app.csplayer.databinding.SubtitleOffsetBinding
import cloud.app.csplayer.utils.ExtractorLink
import cloud.app.csplayer.utils.ExtractorUri
import android.text.Editable
import android.view.LayoutInflater
import android.widget.AbsListView
import android.widget.ArrayAdapter
import androidx.core.widget.doOnTextChanged
import cloud.app.csplayer.utils.SingleSelectionHelper.showDialog
import cloud.app.csplayer.utils.UIHelper.dismissSafe

/**
 * Manages all player dialogs (speed, sources, tracks, subtitles, etc.)
 */
@Suppress("unused")
class PlayerDialogManager(
    private val context: Activity
) {
    private var currentDialog: AlertDialog? = null

    fun showSpeedDialog(currentSpeed: Float, onSpeedSelected: (Float) -> Unit) {
        val speedsText = listOf("0.5x", "0.75x", "1x", "1.25x", "1.5x", "1.75x", "2x")
        val speedsNumbers = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
        val speedIndex = speedsNumbers.indexOf(currentSpeed)

      context.showDialog(speedsText, speedIndex, context.getString(R.string.player_speed), false,
        {},
        { index -> onSpeedSelected(speedsNumbers[index]) }
      )
    }

    fun showSourcesDialog(
        allLinks: Set<Pair<ExtractorLink?, ExtractorUri?>>,
        currentLink: Pair<ExtractorLink?, ExtractorUri?>?,
        onSourceSelected: (Pair<ExtractorLink?, ExtractorUri?>) -> Unit
    ) {
        val builder = AlertDialog.Builder(context, R.style.AlertDialogCustom)
        val binding = PlayerSelectSourceAndSubsBinding.inflate(LayoutInflater.from(context))
        builder.setView(binding.root)

        val dialog = builder.create()
        currentDialog = dialog

        binding.apply {
            var sourceIndex = allLinks.indexOf(currentLink)
            val sourcesAdapter = ArrayAdapter<String>(context, R.layout.sort_bottom_single_choice)

            sourcesAdapter.addAll(allLinks.mapIndexed { index, (link, uri) ->
                "${index + 1}. ${link?.source ?: uri?.name ?: "NULL"}"
            })

            sortProviders.choiceMode = AbsListView.CHOICE_MODE_SINGLE
            sortProviders.adapter = sourcesAdapter
            sortProviders.setSelection(sourceIndex)
            sortProviders.setItemChecked(sourceIndex, true)

            sortProviders.setOnItemClickListener { _, _, which, _ ->
                sourceIndex = which
                sortProviders.setItemChecked(which, true)
            }

            cancelBtt.setOnClickListener {
                dialog.dismissSafe(null)
            }

            applyBtt.setOnClickListener {
                allLinks.elementAt(sourceIndex).let {
                    onSourceSelected(it)
                }
                dialog.dismissSafe(null)
            }
        }

        dialog.show()
    }

    fun showVideoTracksDialog(
        videoTracks: List<Track>,
        currentTrackId: Int,
        onTrackSelected: (Int) -> Unit
    ) {
        val builder = AlertDialog.Builder(context, R.style.AlertDialogCustom)
        val binding = PlayerSelectVideoTracksBinding.inflate(LayoutInflater.from(context))
        builder.setView(binding.root)

        val dialog = builder.create()
        currentDialog = dialog

        binding.apply {
            var selectedIndex = videoTracks.indexOfFirst { it.id == currentTrackId }
            val adapter = ArrayAdapter<String>(context, R.layout.sort_bottom_single_choice)

            adapter.addAll(videoTracks.map { track ->
                "${track.id}. ${track.name ?: "Track ${track.id}"}"
            })

            videoTracksList.choiceMode = AbsListView.CHOICE_MODE_SINGLE
            videoTracksList.adapter = adapter
            videoTracksList.setSelection(selectedIndex)
            videoTracksList.setItemChecked(selectedIndex, true)

            videoTracksList.setOnItemClickListener { _, _, which, _ ->
                selectedIndex = which
                videoTracksList.setItemChecked(which, true)
            }

            cancelBtt.setOnClickListener {
                dialog.dismissSafe(null)
            }

            applyBtt.setOnClickListener {
                if (selectedIndex >= 0) {
                    onTrackSelected(videoTracks[selectedIndex].id)
                }
                dialog.dismissSafe(null)
            }
        }

        dialog.show()
    }

    // Simple Track data class for dialog
    data class Track(val id: Int, val name: String?)

    fun showSubtitleOffsetDialog(
        currentOffset: Long,
        onOffsetChanged: (Long) -> Unit
    ) {
        val binding = SubtitleOffsetBinding.inflate(LayoutInflater.from(context))
        val builder = AlertDialog.Builder(context, R.style.AlertDialogCustom)
            .setView(binding.root)

        val dialog = builder.create()
        currentDialog = dialog

        val initialOffset = currentOffset

        binding.apply {
            subtitleOffsetInput.doOnTextChanged { text, _, _, _ ->
                text?.toString()?.toLongOrNull()?.let { time ->
                    onOffsetChanged(time)

                    val str = when {
                        time > 0L -> context.getString(R.string.subtitle_offset_extra_hint_later_format, time)
                        time < 0L -> context.getString(R.string.subtitle_offset_extra_hint_before_format, -time)
                        else -> context.getString(R.string.subtitle_offset_extra_hint_none_format)
                    }
                    subtitleOffsetSubTitle.text = str
                }
            }

            subtitleOffsetInput.text = Editable.Factory.getInstance()?.newEditable(currentOffset.toString())

            fun changeBy(by: Long) {
                val current = (subtitleOffsetInput.text?.toString()?.toLongOrNull() ?: 0) + by
                subtitleOffsetInput.text = Editable.Factory.getInstance()?.newEditable(current.toString())
            }

            subtitleOffsetAdd.setOnClickListener { changeBy(100L) }
            subtitleOffsetAddMore.setOnClickListener { changeBy(1000L) }
            subtitleOffsetSubtract.setOnClickListener { changeBy(-100L) }
            subtitleOffsetSubtractMore.setOnClickListener { changeBy(-1000L) }

            applyBtt.setOnClickListener {
                dialog.dismissSafe(null)
            }

            resetBtt.setOnClickListener {
                onOffsetChanged(0)
                dialog.dismissSafe(null)
            }

            cancelBtt.setOnClickListener {
                onOffsetChanged(initialOffset)
                dialog.dismissSafe(null)
            }
        }

        dialog.show()
    }

    fun showCodecsDialog(
        currentCodec: String,
        onCodecSelected: (String) -> Unit
    ) {
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

      context.showDialog(codecsDisplay,currentIndex, context.getString(R.string.codec), false,
        {},
        { index -> onCodecSelected(codecs[index]) }
      )
    }

    fun dismissCurrentDialog() {
        currentDialog?.dismiss()
        currentDialog = null
    }

    fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(context, message, duration).show()
    }
}

