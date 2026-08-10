package cloud.streamless.torream.ui.subtitles

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.BundleCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MimeTypes
import cloud.streamless.torream.R
import cloud.streamless.torream.ai.AiManager
import cloud.streamless.torream.ai.translate.SrtVttParser
import cloud.streamless.torream.ai.translate.SubtitleTranslator
import cloud.streamless.torream.app
import cloud.streamless.torream.databinding.DialogTranslateSubtitleBinding
import cloud.streamless.torream.model.SubtitleData
import cloud.streamless.torream.model.SubtitleOrigin
import cloud.streamless.torream.ui.dialog.DockingDialog
import cloud.streamless.torream.ui.dialog.SelectionDialog
import cloud.streamless.torream.utils.SubtitleHelper
import cloud.streamless.torream.utils.UIHelper.dismissSafe
import cloud.streamless.torream.utils.UnifiedFileFactory
import cloud.streamless.torream.utils.Utils.toSubtitleMimeType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class TranslateSubtitleDialog : DockingDialog() {

    companion object {
        private const val ARG_SUBTITLE = "subtitle"

        fun newInstance(subtitle: SubtitleData) = TranslateSubtitleDialog().apply {
            arguments = Bundle().apply { putParcelable(ARG_SUBTITLE, subtitle) }
        }
    }

    var onTranslated: ((SubtitleData) -> Unit)? = null

    @Inject lateinit var aiManager: AiManager

    private var binding: DialogTranslateSubtitleBinding? = null
    private val sourceSubtitle by lazy {
        arguments?.let { BundleCompat.getParcelable(it, ARG_SUBTITLE, SubtitleData::class.java) }
    }

    private val languageOptions = SubtitleHelper.languages
        .filter { it.ISO_639_1.isNotBlank() }
        .distinctBy { it.ISO_639_1 }
        .sortedBy { it.languageName }

    private var targetLanguage: SubtitleHelper.Language639? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val b = DialogTranslateSubtitleBinding.inflate(inflater, container, false)
        binding = b
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = binding ?: return
        val subtitle = sourceSubtitle
        if (subtitle == null) {
            dialog?.dismissSafe(activity)
            return
        }

        targetLanguage = languageOptions.find { it.ISO_639_1 == Locale.getDefault().language }
            ?: languageOptions.find { it.ISO_639_1 == "en" }
            ?: languageOptions.firstOrNull()
        b.targetLanguageValue.text = targetLanguage?.languageName

        b.targetLanguageRow.setOnClickListener { showLanguagePicker() }
        b.cancelBtt.setOnClickListener { dialog?.dismissSafe(activity) }
        b.translateBtt.setOnClickListener { startTranslation(subtitle) }
    }

    private fun showLanguagePicker() {
        val b = binding ?: return
        val names = languageOptions.map { it.languageName }
        val currentIndex = languageOptions.indexOf(targetLanguage).let { if (it < 0) 0 else it }

        SelectionDialog.single(names, currentIndex, getString(R.string.target_language), false)
            .show(childFragmentManager) { bundle ->
                val idx = bundle?.getIntegerArrayList(SelectionDialog.ITEMS_SELECTED)?.firstOrNull()
                    ?: return@show
                targetLanguage = languageOptions.getOrNull(idx)
                b.targetLanguageValue.text = targetLanguage?.languageName
            }
    }

    private fun startTranslation(subtitle: SubtitleData) {
        val b = binding ?: return
        val lang = targetLanguage ?: return
        val ctx = context ?: return

        b.errorText.isGone = true
        b.progressContainer.isVisible = true
        b.translateBtt.isEnabled = false
        b.targetLanguageRow.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                val content = readSubtitleText(ctx, subtitle)
                val isVtt = subtitle.mimeType == MimeTypes.TEXT_VTT
                val cues = SrtVttParser.parse(content)
                if (cues.isEmpty()) error(getString(R.string.subtitle_no_results))

                val translatedCues = SubtitleTranslator.translate(
                    cues,
                    lang.languageName,
                    chatCompletion = aiManager::defaultChatCompletion,
                    onProgress = { done, total -> updateProgress(done, total) }
                ).getOrThrow()

                writeToCache(ctx, subtitle.name, SrtVttParser.serialize(translatedCues, isVtt), isVtt)
            }

            result.onSuccess { file ->
                val translatedSub = SubtitleData(
                    name = "${subtitle.name} (${lang.languageName})",
                    url = file.toURI().toString(),
                    origin = SubtitleOrigin.DOWNLOADED_FILE,
                    mimeType = file.name.toSubtitleMimeType(),
                    headers = emptyMap(),
                    languageCode = lang.ISO_639_1
                )
                onTranslated?.invoke(translatedSub)
                dialog?.dismissSafe(activity)
            }.onFailure { e ->
                Timber.e(e, "Subtitle translation failed")
                showError(e.message ?: getString(R.string.translation_failed))
            }
        }
    }

    private suspend fun readSubtitleText(ctx: Context, subtitle: SubtitleData): String =
        withContext(Dispatchers.IO) {
            if (subtitle.url.startsWith("http://", true) || subtitle.url.startsWith("https://", true)) {
                app.get(subtitle.url).text
            } else {
                UnifiedFileFactory.fromPath(ctx, subtitle.url)?.openInputStream()
                    ?.bufferedReader()?.use { it.readText() }
                    ?: error("Cannot read subtitle file")
            }
        }

    private suspend fun writeToCache(
        ctx: Context,
        originalName: String,
        content: String,
        isVtt: Boolean
    ): File = withContext(Dispatchers.IO) {
        val subsDir = File(ctx.cacheDir, "subtitles").apply { mkdirs() }
        val ext = if (isVtt) "vtt" else "srt"
        val safeName = originalName.substringBeforeLast('.')
            .replace(Regex("[^a-zA-Z0-9._-]"), "_").take(60)
        File(subsDir, "${safeName}_translated_${System.currentTimeMillis()}.$ext").apply {
            writeText(content)
        }
    }

    private fun updateProgress(done: Int, total: Int) {
        val b = binding ?: return
        b.translateProgressBar.max = total
        b.translateProgressBar.progress = done
        b.progressText.text = getString(R.string.translating_progress, done, total)
    }

    private fun showError(message: String) {
        val b = binding ?: return
        b.progressContainer.isGone = true
        b.errorText.isVisible = true
        b.errorText.text = message
        b.translateBtt.isEnabled = true
        b.targetLanguageRow.isEnabled = true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
