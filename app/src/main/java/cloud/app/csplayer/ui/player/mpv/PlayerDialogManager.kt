package cloud.app.csplayer.ui.player.mpv

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.text.Editable
import android.view.LayoutInflater
import android.widget.AbsListView
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.preference.PreferenceManager
import cloud.app.csplayer.R
import cloud.app.csplayer.databinding.PlayerSelectSourceAndSubsBinding
import cloud.app.csplayer.databinding.PlayerSelectTracksBinding
import cloud.app.csplayer.databinding.PlayerSelectVideoTracksBinding
import cloud.app.csplayer.databinding.SubtitleOffsetBinding
import cloud.app.csplayer.model.SubtitleData
import cloud.app.csplayer.model.VideoLink
import cloud.app.csplayer.utils.SingleSelectionHelper.showDialog
import cloud.app.csplayer.utils.UIHelper.dismissSafe
import cloud.app.csplayer.utils.Utils.sortSubs
import kotlin.math.max

/**
 * Manages all player dialogs (speed, sources, tracks, subtitles, etc.)
 * Handles dialog lifecycle and integrates with MPV player
 */
class PlayerDialogManager(
    private val activity: Activity
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

        activity.showDialog(
            speedsText,
            speedIndex,
            activity.getString(R.string.player_speed),
            false,
            {},
            { index -> onSpeedSelected(speedsNumbers[index]) }
        )
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

        activity.showDialog(
            codecsDisplay,
            currentIndex,
            activity.getString(R.string.codec),
            false,
            {},
            { index -> onCodecSelected(codecs[index]) }
        )
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
        val ctx = activity
        val sourceDialog = Dialog(ctx, R.style.AlertDialogCustom)
        val binding = PlayerSelectSourceAndSubsBinding.inflate(LayoutInflater.from(ctx), null, false)
        sourceDialog.setContentView(binding.root)

        selectSourceDialog = sourceDialog
        sourceDialog.show()

        binding.apply {
            var sourceIndex = currentSelectedLink?.let { allLinks.indexOf(it) } ?: 0
            val sourcesArrayAdapter = ArrayAdapter<String>(ctx, R.layout.sort_bottom_single_choice)

            sourcesArrayAdapter.addAll(allLinks.mapIndexed { index, link ->
                "${index + 1}. ${link.name}"
            })

            sortProviders.choiceMode = AbsListView.CHOICE_MODE_SINGLE
            sortProviders.adapter = sourcesArrayAdapter
            sortProviders.setSelection(sourceIndex)
            sortProviders.setItemChecked(sourceIndex, true)

            sortProviders.setOnItemClickListener { _, _, which, _ ->
                sourceIndex = which
                sortProviders.setItemChecked(which, true)
            }

            sourceDialog.setOnDismissListener {
                onDismiss()
                selectSourceDialog = null
            }

            cancelBtt.setOnClickListener {
                sourceDialog.dismissSafe(activity)
            }

            applyBtt.setOnClickListener {
                allLinks.getOrNull(sourceIndex)?.let {
                    onSourceSelected(it, null)
                }
                sourceDialog.dismissSafe(activity)
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
        val ctx = activity
        val currentVideoTracks = tracks["video"]
        if (currentVideoTracks == null) {
            Toast.makeText(ctx, "No video tracks available", Toast.LENGTH_SHORT).show()
            return
        }

        var videoIndex = max((currentVideoTracks.indexOfFirst { it.selected }), 0)

        val binding = PlayerSelectVideoTracksBinding.inflate(LayoutInflater.from(ctx), null, false)
        val trackDialog = Dialog(ctx, R.style.AlertDialogCustom)
        trackDialog.setContentView(binding.root)
        trackDialog.show()
        selectVideoDialog = trackDialog

        binding.apply {
            val videosList = videoTracksList
            videoTracksHolder.isVisible = currentVideoTracks.isNotEmpty()

            val videosArrayAdapter = ArrayAdapter<String>(ctx, R.layout.sort_bottom_single_choice)
            videosArrayAdapter.addAll(currentVideoTracks.map { it.name })

            videosList.choiceMode = AbsListView.CHOICE_MODE_SINGLE
            videosList.adapter = videosArrayAdapter
            videosList.setSelection(videoIndex)
            videosList.setItemChecked(videoIndex, true)

            videosList.setOnItemClickListener { _, _, which, _ ->
                videoIndex = which
                videosList.setItemChecked(which, true)
            }

            trackDialog.setOnDismissListener {
                onDismiss()
                selectVideoDialog = null
            }

            cancelBtt.setOnClickListener {
                trackDialog.dismissSafe(activity)
            }

            applyBtt.setOnClickListener {
                onTrackSelected(videoIndex)
                trackDialog.dismissSafe(activity)
            }
        }
    }

    /**
     * Show audio and subtitle tracks selection dialog
     */
    fun showTracksDialog(
        tracks: Map<String, List<MPVView.Track>>,
        onTracksSelected: (audioIndex: Int, subtitleIndex: Int) -> Unit,
        onLoadSubtitlesFromFile: () -> Unit,
        onLoadSubtitlesOnline: () -> Unit,
        onDismiss: () -> Unit
    ) {
        val ctx = activity
        val currentAudioTracks = tracks["audio"]
        val currentSubtitleTracks = tracks["sub"]

        var audioIndexStart = max((currentAudioTracks?.indexOfFirst { it.selected } ?: 0), 0)
        var subtitleIndex = max((currentSubtitleTracks?.indexOfFirst { it.selected } ?: 0), 0)

        val binding = PlayerSelectTracksBinding.inflate(LayoutInflater.from(ctx), null, false)
        val trackDialog = Dialog(ctx, R.style.AlertDialogCustom)
        trackDialog.setContentView(binding.root)
        trackDialog.show()
        selectTrackDialog = trackDialog

        binding.apply {
            // Audio tracks setup
            currentAudioTracks?.let { audioTracks ->
                autoTracksList.apply {
                    audioTracksHolder.isVisible = audioTracks.isNotEmpty()

                    val audioArrayAdapter = ArrayAdapter<String>(ctx, R.layout.sort_bottom_single_choice)
                    audioArrayAdapter.addAll(audioTracks.map { it.name })

                    adapter = audioArrayAdapter
                    choiceMode = AbsListView.CHOICE_MODE_SINGLE
                    setSelection(audioIndexStart)
                    setItemChecked(audioIndexStart, true)

                    setOnItemClickListener { _, _, which, _ ->
                        audioIndexStart = which
                        setItemChecked(which, true)
                    }
                }
            }

            // Subtitle tracks setup
            currentSubtitleTracks?.let { subTracks ->
                sortSubtitles.apply {
                    // Add "Load from file" footer
                    val loadFromFileFooter = LayoutInflater.from(ctx)
                        .inflate(R.layout.sort_bottom_footer_add_choice, null) as TextView
                    loadFromFileFooter.text = ctx.getString(R.string.player_load_subtitles)
                    loadFromFileFooter.setOnClickListener {
                        onLoadSubtitlesFromFile()
                    }
                    addFooterView(loadFromFileFooter)

                    // Add "Load from network" footer
                    val loadFromNetworkFooter = LayoutInflater.from(ctx)
                        .inflate(R.layout.sort_bottom_footer_add_choice, null) as TextView
                    loadFromNetworkFooter.text = ctx.getString(R.string.player_load_subtitles_online)
                    loadFromNetworkFooter.setOnClickListener {
                        onLoadSubtitlesOnline()
                    }
                    addFooterView(loadFromNetworkFooter)

                    val subsArrayAdapter = ArrayAdapter<String>(ctx, R.layout.sort_bottom_single_choice)
                    subsArrayAdapter.addAll(subTracks.map { it.name })

                    adapter = subsArrayAdapter
                    choiceMode = AbsListView.CHOICE_MODE_SINGLE
                    setSelection(subtitleIndex)
                    setItemChecked(subtitleIndex, true)

                    setOnItemClickListener { _, _, which, _ ->
                        if (which > subTracks.size - 1) {
                            // Click footer view instead
                            val child = adapter.getView(which, null, this)
                            child?.performClick()
                        } else {
                            subtitleIndex = which
                            setItemChecked(which, true)
                        }
                    }
                }
            }

            // Subtitle encoding display
            subtitlesEncodingFormat.apply {
                val settingsManager = PreferenceManager.getDefaultSharedPreferences(ctx)
                val prefNames = ctx.resources.getStringArray(R.array.subtitles_encoding_list)
                val prefValues = ctx.resources.getStringArray(R.array.subtitles_encoding_values)

                val value = settingsManager.getString(ctx.getString(R.string.subtitles_encoding_key), null)
                val index = prefValues.indexOf(value)
                text = prefNames[if (index == -1) 0 else index]
            }

            trackDialog.setOnDismissListener {
                onDismiss()
                selectTrackDialog = null
            }

            cancelBtt.setOnClickListener {
                trackDialog.dismissSafe(activity)
            }

            applyBtt.setOnClickListener {
                onTracksSelected(audioIndexStart, subtitleIndex)
                trackDialog.dismissSafe(activity)
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
        val ctx = activity
        val binding = SubtitleOffsetBinding.inflate(LayoutInflater.from(ctx), null, false)

        val builder = AlertDialog.Builder(ctx, R.style.AlertDialogCustom)
            .setView(binding.root)
        val dialog = builder.create()
        dialog.show()

        val beforeOffset = currentOffset

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

            subtitleOffsetInput.text = Editable.Factory.getInstance()?.newEditable(currentOffset.toString())

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
                dialog.dismissSafe(activity)
            }

            resetBtt.setOnClickListener {
                onReset()
                dialog.dismissSafe(activity)
            }

            cancelBtt.setOnClickListener {
                onCancel(beforeOffset)
                dialog.dismissSafe(activity)
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
        Toast.makeText(activity, message, duration).show()
    }

    /**
     * Show a toast message from resource ID
     */
    fun showToast(messageResId: Int, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(activity, messageResId, duration).show()
    }
}

