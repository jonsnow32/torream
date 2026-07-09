package cloud.streamless.torream.ui.subtitles.providers

import cloud.streamless.torream.app
import cloud.streamless.torream.ui.subtitles.AbstractSubtitleEntities
import cloud.streamless.torream.utils.Utils.parseJson
import kotlinx.serialization.Serializable
import timber.log.Timber

class SubSourceProvider : SubtitleProvider {
    override val name = "SubSource"

    private val baseUrl = "https://api.subsource.net/api"

    @Serializable
    private data class SearchResponse(val found: List<ShowResult> = emptyList())

    @Serializable
    private data class ShowResult(
        val title: String = "",
        val linkName: String = "",
        val subCount: Int = 0
    )

    @Serializable
    private data class MovieResponse(val subs: List<SubEntry> = emptyList())

    @Serializable
    private data class SubEntry(
        val id: String = "",
        val lang: String = "",
        val releaseName: String = "",
        val hi: Boolean = false
    )

    override suspend fun search(
        query: String,
        lang: String?
    ): List<AbstractSubtitleEntities.SubtitleEntity> {
        val searchResp = app.get("$baseUrl/searchShow", params = mapOf("query" to query))
        val shows = parseJson<SearchResponse>(searchResp.text)
        val show = shows.found.firstOrNull() ?: return emptyList()

        val langs = listOf(if (!lang.isNullOrBlank()) lang.uppercase() else "EN")
        val movieResp = app.post(
            "$baseUrl/getMovie",
            json = mapOf("movie" to show.linkName, "langs" to langs)
        )
        val movie = parseJson<MovieResponse>(movieResp.text)
        return movie.subs.map { sub ->
            AbstractSubtitleEntities.SubtitleEntity(
                idPrefix = "subsource",
                name = sub.releaseName.ifBlank { sub.id },
                lang = sub.lang.lowercase(),
                data = sub.id,
                source = name,
                isHearingImpaired = sub.hi
            )
        }.also { Timber.d("[SubSource] found ${it.size} results for '$query'") }
    }

    override suspend fun getDownloadUrl(
        entity: AbstractSubtitleEntities.SubtitleEntity
    ): String? {
        val response = app.get("$baseUrl/downloadSub/${entity.data}")
        // Response contains a redirect URL to the actual subtitle file
        return response.url.takeIf { it.isNotBlank() && !it.contains("/downloadSub/") }
    }
}
