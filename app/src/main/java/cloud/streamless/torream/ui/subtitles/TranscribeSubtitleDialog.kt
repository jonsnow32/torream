package cloud.streamless.torream.ui.subtitles

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import cloud.streamless.torream.R
import cloud.streamless.torream.ai.AiManager
import cloud.streamless.torream.ai.providers.AiProvider
import cloud.streamless.torream.ai.transcribe.AudioTranscriber
import cloud.streamless.torream.ai.translate.SrtVttParser
import cloud.streamless.torream.databinding.DialogTranscribeSubtitleBinding
import cloud.streamless.torream.model.SubtitleData
import cloud.streamless.torream.model.SubtitleOrigin
import cloud.streamless.torream.ui.dialog.DockingDialog
import cloud.streamless.torream.ui.dialog.SelectionDialog
import cloud.streamless.torream.utils.SubtitleHelper
import cloud.streamless.torream.utils.UIHelper.dismissSafe
import cloud.streamless.torream.utils.Utils.toSubtitleMimeType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/** Speech-to-subtitle: transcribes the playing file's audio with a Whisper provider. */
@AndroidEntryPoint
class TranscribeSubtitleDialog : DockingDialog() {

    companion object {
        private const val ARG_SOURCE = "source"
        private const val ARG_HEADERS = "headers"
        private const val ARG_DURATION = "duration"
        private const val ARG_TITLE = "title"

        fun newInstance(
            source: String,
            headers: Map<String, String>,
            durationSeconds: Double,
            title: String
        ) = TranscribeSubtitleDialog().apply {
            arguments = Bundle().apply {
                putString(ARG_SOURCE, source)
                putSerializable(ARG_HEADERS, HashMap(headers))
                putDouble(ARG_DURATION, durationSeconds)
                putString(ARG_TITLE, title)
            }
        }
    }

    var onTranscribed: ((SubtitleData) -> Unit)? = null

    @Inject lateinit var aiManager: AiManager

    private var binding: DialogTranscribeSubtitleBinding? = null

    private val languageOptions = SubtitleHelper.languages
        .filter { it.ISO_639_1.isNotBlank() }
        .distinctBy { it.ISO_639_1 }
        .sortedBy { it.languageName }

    private var providers: List<AiProvider.ProviderType> = emptyList()
    private var provider: AiProvider.ProviderType? = null

    /** null = let the model auto-detect. */
    private var language: SubtitleHelper.Language639? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val b = DialogTranscribeSubtitleBinding.inflate(inflater, container, false)
        binding = b
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = binding ?: return

        providers = aiManager.getTranscriptionProviders().map { it.providerType }
        provider = providers.firstOrNull()
        if (provider == null) {
            b.providerValue.text = getString(R.string.ai_key_not_set)
            b.transcribeBtt.isEnabled = false
            showError(getString(R.string.transcription_no_provider))
        } else {
            b.providerValue.text = provider?.name
        }
        b.languageValue.text = getString(R.string.language_auto_detect)

        b.providerRow.setOnClickListener { showProviderPicker() }
        b.languageRow.setOnClickListener { showLanguagePicker() }
        b.cancelBtt.setOnClickListener { dialog?.dismissSafe(activity) }
        b.transcribeBtt.setOnClickListener { start() }
    }

    private fun showProviderPicker() {
        val b = binding ?: return
        if (providers.isEmpty()) return
        val names = providers.map { it.name }
        SelectionDialog.single(
            names,
            providers.indexOf(provider).coerceAtLeast(0),
            getString(R.string.ai_default_provider),
            false
        ).show(childFragmentManager) { bundle ->
            val idx = bundle?.getIntegerArrayList(SelectionDialog.ITEMS_SELECTED)?.firstOrNull()
                ?: return@show
            provider = providers.getOrNull(idx)
            b.providerValue.text = provider?.name
        }
    }

    private fun showLanguagePicker() {
        val b = binding ?: return
        val names = listOf(getString(R.string.language_auto_detect)) +
            languageOptions.map { it.languageName }
        val currentIndex = language?.let { languageOptions.indexOf(it) + 1 } ?: 0
        SelectionDialog.single(names, currentIndex, getString(R.string.spoken_language), false)
            .show(childFragmentManager) { bundle ->
                val idx = bundle?.getIntegerArrayList(SelectionDialog.ITEMS_SELECTED)?.firstOrNull()
                    ?: return@show
                language = if (idx == 0) null else languageOptions.getOrNull(idx - 1)
                b.languageValue.text = language?.languageName
                    ?: getString(R.string.language_auto_detect)
            }
    }

    private fun start() {
        val b = binding ?: return
        val ctx = context ?: return
        val type = provider ?: return
        val source = arguments?.getString(ARG_SOURCE) ?: return
        @Suppress("DEPRECATION", "UNCHECKED_CAST")
        val headers = (arguments?.getSerializable(ARG_HEADERS) as? HashMap<String, String>).orEmpty()
        val duration = arguments?.getDouble(ARG_DURATION) ?: 0.0
        val title = arguments?.getString(ARG_TITLE).orEmpty()

        b.errorText.isGone = true
        b.progressContainer.isVisible = true
        b.transcribeBtt.isEnabled = false
        b.providerRow.isEnabled = false
        b.languageRow.isEnabled = false
        updateProgress(0.0, duration)

        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                val cues = AudioTranscriber(ctx.applicationContext, aiManager).transcribe(
                    source = source,
                    headers = headers,
                    provider = type,
                    language = language?.ISO_639_1,
                    durationSeconds = duration,
                    onProgress = { done, total -> updateProgress(done, total) }
                ).getOrThrow()
                writeToCache(ctx, title, SrtVttParser.serialize(cues, isVtt = false))
            }

            result.onSuccess { file ->
                val sub = SubtitleData(
                    name = getString(R.string.generated_subtitle_name, language?.languageName
                        ?: getString(R.string.language_auto_detect)),
                    url = file.toURI().toString(),
                    origin = SubtitleOrigin.DOWNLOADED_FILE,
                    mimeType = file.name.toSubtitleMimeType(),
                    headers = emptyMap(),
                    languageCode = language?.ISO_639_1 ?: ""
                )
                onTranscribed?.invoke(sub)
                dialog?.dismissSafe(activity)
            }.onFailure { e ->
                Timber.e(e, "Speech-to-subtitle failed")
                showError(e.message ?: getString(R.string.transcription_failed))
            }
        }
    }

    private suspend fun writeToCache(ctx: Context, title: String, content: String): File =
        withContext(Dispatchers.IO) {
            val subsDir = File(ctx.cacheDir, "subtitles").apply { mkdirs() }
            val safeName = title.substringBeforeLast('.')
                .replace(Regex("[^a-zA-Z0-9._-]"), "_").take(60).ifBlank { "audio" }
            File(subsDir, "${safeName}_stt_${System.currentTimeMillis()}.srt").apply {
                writeText(content)
            }
        }

    private fun updateProgress(doneSeconds: Double, totalSeconds: Double) {
        val b = binding ?: return
        val total = totalSeconds.takeIf { it > 0 } ?: doneSeconds.coerceAtLeast(1.0)
        b.transcribeProgressBar.max = total.toInt()
        b.transcribeProgressBar.progress = doneSeconds.toInt().coerceAtMost(total.toInt())
        b.progressText.text = getString(
            R.string.transcribing_progress, formatTime(doneSeconds), formatTime(total)
        )
    }

    private fun formatTime(seconds: Double): String {
        val total = seconds.toLong().coerceAtLeast(0)
        return "%02d:%02d".format(total / 60, total % 60)
    }

    private fun showError(message: String) {
        val b = binding ?: return
        b.progressContainer.isGone = true
        b.errorText.isVisible = true
        b.errorText.text = message
        b.transcribeBtt.isEnabled = provider != null
        b.providerRow.isEnabled = true
        b.languageRow.isEnabled = true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
