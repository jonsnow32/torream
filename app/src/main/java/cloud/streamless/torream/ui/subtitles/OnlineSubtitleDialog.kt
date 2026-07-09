package cloud.streamless.torream.ui.subtitles

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import cloud.streamless.torream.R
import cloud.streamless.torream.app
import cloud.streamless.torream.databinding.DialogOnlineSubtitlesBinding
import cloud.streamless.torream.model.SubtitleData
import cloud.streamless.torream.model.SubtitleOrigin
import cloud.streamless.torream.ui.dialog.DockingDialog
import cloud.streamless.torream.ui.dialog.SelectionDialog
import cloud.streamless.torream.ui.subtitles.AbstractSubtitleEntities.SubtitleEntity
import cloud.streamless.torream.ui.subtitles.providers.OpenSubtitlesProvider
import cloud.streamless.torream.ui.subtitles.providers.SubDLProvider
import cloud.streamless.torream.ui.subtitles.providers.SubSourceProvider
import cloud.streamless.torream.ui.subtitles.providers.SubtitleProvider
import cloud.streamless.torream.utils.SubtitleHelper
import cloud.streamless.torream.utils.UIHelper.dismissSafe
import cloud.streamless.torream.utils.Utils.showToast
import cloud.streamless.torream.utils.Utils.toSubtitleMimeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.zip.ZipInputStream

class OnlineSubtitleDialog : DockingDialog() {

    companion object {
        private const val ARG_TITLE = "title"

        fun newInstance(title: String?) = OnlineSubtitleDialog().apply {
            arguments = Bundle().apply { putString(ARG_TITLE, title ?: "") }
        }
    }

    var onSubtitleSelected: ((SubtitleData) -> Unit)? = null

    private var binding: DialogOnlineSubtitlesBinding? = null
    private val videoTitle by lazy { arguments?.getString(ARG_TITLE, "") ?: "" }

    private val providers: List<SubtitleProvider> = listOf(
        OpenSubtitlesProvider(),
        SubDLProvider(),
        SubSourceProvider()
    )

    private val subtitleAdapter by lazy { SubtitleListAdapter(requireContext()) }
    private var selectedLang: String? = null

    private val languageOptions = listOf(
        "All Languages" to null,
        "English" to "en",
        "Spanish" to "es",
        "French" to "fr",
        "German" to "de",
        "Portuguese" to "pt",
        "Italian" to "it",
        "Dutch" to "nl",
        "Arabic" to "ar",
        "Chinese" to "zh",
        "Japanese" to "ja",
        "Korean" to "ko",
        "Russian" to "ru",
        "Turkish" to "tr",
        "Vietnamese" to "vi"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val b = DialogOnlineSubtitlesBinding.inflate(inflater, container, false)
        binding = b
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = binding ?: return

        b.subtitleAdapter.adapter = subtitleAdapter
        b.applyBtt.isGone = true
        b.yearBtt.isGone = true

        b.cancelBtt.setOnClickListener { dialog?.dismissSafe(activity) }

        b.subtitlesSearch.setQuery(videoTitle, false)
        b.subtitlesSearch.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (!query.isNullOrBlank()) triggerSearch(query)
                return true
            }

            override fun onQueryTextChange(newText: String?) = false
        })

        b.searchFilter.setOnClickListener { showLanguagePicker() }

        b.subtitleAdapter.setOnItemClickListener { _, _, position, _ ->
            val entity = subtitleAdapter.getItem(position) ?: return@setOnItemClickListener
            downloadAndSelect(entity)
        }

        if (videoTitle.isNotBlank()) triggerSearch(videoTitle)
    }

    private fun triggerSearch(query: String) {
        val b = binding ?: return
        subtitleAdapter.clear()
        b.searchLoadingBar.show()

        viewLifecycleOwner.lifecycleScope.launch {
            val results = providers.map { provider ->
                async(Dispatchers.IO) {
                    runCatching { provider.search(query, selectedLang) }.getOrElse { e ->
                        Timber.e(e, "[${provider.name}] search failed")
                        emptyList()
                    }
                }
            }.map { it.await() }.flatten()

            b.searchLoadingBar.hide()
            if (results.isEmpty()) {
                showToast(getString(R.string.subtitle_no_results))
            } else {
                subtitleAdapter.addAll(results)
            }
        }
    }

    private fun downloadAndSelect(entity: SubtitleEntity) {
        val ctx = context ?: return
        val b = binding ?: return
        b.searchLoadingBar.show()

        viewLifecycleOwner.lifecycleScope.launch {
            val provider = providers.find { it.name == entity.source }
            val url = withContext(Dispatchers.IO) {
                runCatching { provider?.getDownloadUrl(entity) }.getOrNull()
            }
            if (url == null) {
                b.searchLoadingBar.hide()
                showToast(getString(R.string.subtitle_download_error))
                return@launch
            }

            val file = withContext(Dispatchers.IO) {
                runCatching { downloadToCache(ctx, url, entity.name) }.getOrNull()
            }
            b.searchLoadingBar.hide()
            if (file == null) {
                showToast(getString(R.string.subtitle_download_error))
                return@launch
            }

            val subtitleData = SubtitleData(
                name = entity.name,
                url = file.toURI().toString(),
                origin = SubtitleOrigin.DOWNLOADED_FILE,
                mimeType = file.name.toSubtitleMimeType(),
                headers = emptyMap(),
                languageCode = entity.lang
            )
            onSubtitleSelected?.invoke(subtitleData)
            dialog?.dismissSafe(activity)
        }
    }

    private suspend fun downloadToCache(ctx: Context, url: String, name: String): File {
        val subsDir = File(ctx.cacheDir, "subtitles").apply { mkdirs() }
        val ext = url.substringAfterLast('.', "").lowercase().let {
            if (it.length in 2..4) it else "srt"
        }
        val safeName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(60)
        val tempFile = File(subsDir, "$safeName.$ext")
        tempFile.writeBytes(app.get(url).body.bytes())
        if (ext == "zip") {
            return extractSubFromZip(tempFile, subsDir) ?: tempFile
        }
        return tempFile
    }

    private fun extractSubFromZip(zipFile: File, destDir: File): File? {
        val subtitleExts = setOf("srt", "vtt", "ass", "ssa")
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val ext = entry.name.substringAfterLast('.').lowercase()
                if (!entry.isDirectory && ext in subtitleExts) {
                    val dest = File(destDir, File(entry.name).name)
                    dest.outputStream().use { zis.copyTo(it) }
                    return dest
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return null
    }

    private fun showLanguagePicker() {
        val names = languageOptions.map { it.first }
        val currentIndex = languageOptions.indexOfFirst { it.second == selectedLang }
            .let { if (it < 0) 0 else it }
        SelectionDialog.single(
            items = names,
            selectedIndex = currentIndex,
            name = getString(R.string.subs_subtitle_languages),
            showApply = false
        ).show(childFragmentManager) { bundle ->
            val idx = bundle?.getIntegerArrayList(SelectionDialog.ITEMS_SELECTED)
                ?.firstOrNull() ?: return@show
            selectedLang = languageOptions.getOrNull(idx)?.second
            val query = binding?.subtitlesSearch?.query?.toString() ?: return@show
            if (query.isNotBlank()) triggerSearch(query)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private class SubtitleListAdapter(context: Context) :
        ArrayAdapter<SubtitleEntity>(context, R.layout.sort_bottom_single_choice_double_text) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(
                R.layout.sort_bottom_single_choice_double_text, parent, false
            )
            val entity = getItem(position) ?: return view

            val mainText = view.findViewById<TextView>(R.id.main_text)
            val secondaryText = view.findViewById<TextView>(R.id.secondary_text)
            val drawableEnd = view.findViewById<ImageView>(R.id.drawable_end)
            val drawableStart = view.findViewById<ImageView>(R.id.drawable_start)

            val langName = SubtitleHelper.fromTwoLettersToLanguage(entity.lang)
                ?: entity.lang.uppercase()
            val flag = SubtitleHelper.getFlagFromIso(entity.lang) ?: ""
            val displayName = if (entity.name.isNotBlank()) entity.name else langName

            mainText.text = "$flag $langName · $displayName"
            secondaryText.text = entity.source
            drawableEnd.isVisible = entity.isHearingImpaired
            drawableEnd.setImageResource(R.drawable.ic_baseline_hearing_24)
            drawableStart.isGone = true

            return view
        }
    }
}
