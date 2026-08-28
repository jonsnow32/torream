package cloud.streamless.torream.ui.subtitles.providers

import cloud.streamless.torream.BuildConfig
import cloud.streamless.torream.app
import cloud.streamless.torream.ui.subtitles.AbstractSubtitleEntities
import cloud.streamless.torream.utils.Utils.parseJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import timber.log.Timber

class SubDLProvider : SubtitleProvider {
    override val name = "SubDL"

    private val baseUrl = "https://api.subdl.com/api/v1"
    private val dlBase = "https://dl.subdl.com"
    private val apiKey get() = BuildConfig.SUBDL_API_KEY

    @Serializable
    private data class SearchResponse(
        val status: Boolean = false,
        val subtitles: List<SubEntry> = emptyList()
    )

    @Serializable
    private data class SubEntry(
        @SerialName("release_name") val releaseName: String = "",
        val name: String = "",
        val language: String = "",
        val url: String = "",
        val hi: Boolean = false,
        val year: Int? = null
    )

    override suspend fun search(
        query: String,
        lang: String?
    ): List<AbstractSubtitleEntities.SubtitleEntity> {
        if (apiKey.isBlank()) return emptyList()
        val params = buildMap<String, String> {
            put("film_name", query)
            put("api_key", apiKey)
            if (!lang.isNullOrBlank()) put("language", lang.uppercase())
        }
        val response = app.get("$baseUrl/subtitles", params = params)
        val parsed = parseJson<SearchResponse>(response.text)
        if (!parsed.status) return emptyList()
        return parsed.subtitles.map { sub ->
            AbstractSubtitleEntities.SubtitleEntity(
                idPrefix = "subdl",
                name = sub.releaseName.ifBlank { sub.name },
                lang = sub.language.lowercase(),
                data = dlBase + sub.url,
                source = name,
                year = sub.year,
                isHearingImpaired = sub.hi
            )
        }.also { Timber.d("[SubDL] found ${it.size} results for '$query'") }
    }

    override suspend fun getDownloadUrl(
        entity: AbstractSubtitleEntities.SubtitleEntity
    ): String? = entity.data.takeIf { it.isNotBlank() }
}
