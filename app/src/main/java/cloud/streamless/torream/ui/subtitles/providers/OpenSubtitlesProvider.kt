package cloud.streamless.torream.ui.subtitles.providers

import cloud.streamless.torream.BuildConfig
import cloud.streamless.torream.app
import cloud.streamless.torream.ui.subtitles.AbstractSubtitleEntities
import cloud.streamless.torream.utils.Utils.parseJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import timber.log.Timber

class OpenSubtitlesProvider : SubtitleProvider {
    override val name = "OpenSubtitles"

    private val baseUrl = "https://api.opensubtitles.com/api/v1"
    private val apiKey get() = BuildConfig.OPENSUBTITLES_API_KEY
    private val headers
        get() = mapOf(
            "Api-Key" to apiKey,
            "Content-Type" to "application/json"
        )

    @Serializable
    private data class SearchResponse(val data: List<SubData> = emptyList())

    @Serializable
    private data class SubData(val attributes: SubAttributes = SubAttributes())

    @Serializable
    private data class SubAttributes(
        val language: String = "",
        val release: String = "",
        @SerialName("hearing_impaired") val hearingImpaired: Boolean = false,
        val files: List<SubFile> = emptyList()
    )

    @Serializable
    private data class SubFile(
        @SerialName("file_id") val fileId: Int = 0,
        @SerialName("file_name") val fileName: String = ""
    )

    @Serializable
    private data class DownloadResponse(val link: String = "")

    override suspend fun search(
        query: String,
        lang: String?
    ): List<AbstractSubtitleEntities.SubtitleEntity> {
        if (apiKey.isBlank()) return emptyList()
        val params = buildMap<String, String> {
            put("query", query)
            if (!lang.isNullOrBlank()) put("languages", lang)
            put("per_page", "30")
        }
        val response = app.get("$baseUrl/subtitles", headers = headers, params = params)
        val parsed = parseJson<SearchResponse>(response.text)
        return parsed.data.mapNotNull { sub ->
            val file = sub.attributes.files.firstOrNull() ?: return@mapNotNull null
            AbstractSubtitleEntities.SubtitleEntity(
                idPrefix = "opensub",
                name = file.fileName.ifBlank { sub.attributes.release },
                lang = sub.attributes.language,
                data = file.fileId.toString(),
                source = name,
                isHearingImpaired = sub.attributes.hearingImpaired
            )
        }.also { Timber.d("[OpenSubtitles] found ${it.size} results for '$query'") }
    }

    override suspend fun getDownloadUrl(
        entity: AbstractSubtitleEntities.SubtitleEntity
    ): String? {
        if (apiKey.isBlank()) return null
        val fileId = entity.data.toIntOrNull() ?: return null
        val response = app.post(
            "$baseUrl/download",
            headers = headers,
            json = mapOf("file_id" to fileId)
        )
        val parsed = parseJson<DownloadResponse>(response.text)
        return parsed.link.takeIf { it.isNotBlank() }
    }
}
