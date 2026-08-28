package cloud.streamless.torream.ui.subtitles.providers

import cloud.streamless.torream.app
import cloud.streamless.torream.ui.subtitles.AbstractSubtitleEntities
import cloud.streamless.torream.utils.SubtitleHelper
import cloud.streamless.torream.utils.Utils.parseJson
import kotlinx.serialization.Serializable
import timber.log.Timber
import java.net.URLEncoder

/**
 * Free, no-API-key subtitle source that mirrors what the Stremio app uses:
 * Cinemeta resolves a title to an IMDB id, then the OpenSubtitles v3 Stremio
 * addon returns subtitles for that id.
 */
class StremioProvider : SubtitleProvider {
    override val name = "Stremio"

    private val cinemetaBase = "https://v3-cinemeta.strem.io"
    private val subtitlesBase = "https://opensubtitles-v3.strem.io"

    @Serializable
    private data class CatalogResponse(val metas: List<Meta> = emptyList())

    @Serializable
    private data class Meta(
        val id: String = "",
        val name: String = "",
        val releaseInfo: String = ""
    )

    @Serializable
    private data class SubtitlesResponse(val subtitles: List<SubEntry> = emptyList())

    @Serializable
    private data class SubEntry(
        val id: String = "",
        val url: String = "",
        val lang: String = ""
    )

    override suspend fun search(
        query: String,
        lang: String?
    ): List<AbstractSubtitleEntities.SubtitleEntity> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val catalogResp = app.get("$cinemetaBase/catalog/movie/top/search=$encodedQuery.json")
        val movie = parseJson<CatalogResponse>(catalogResp.text).metas.firstOrNull()
            ?: return emptyList()

        val subsResp = app.get("$subtitlesBase/subtitles/movie/${movie.id}.json")
        val subtitles = parseJson<SubtitlesResponse>(subsResp.text).subtitles

        return subtitles.mapNotNull { sub ->
            val twoLetterLang = SubtitleHelper.fromThreeLettersToLanguage(sub.lang)
                ?.let { SubtitleHelper.fromLanguageToTwoLetters(it, false) }
                ?: sub.lang
            if (!lang.isNullOrBlank() && !twoLetterLang.equals(lang, ignoreCase = true)) return@mapNotNull null
            AbstractSubtitleEntities.SubtitleEntity(
                idPrefix = "stremio",
                name = "${movie.name} ${movie.releaseInfo}".trim(),
                lang = twoLetterLang,
                data = sub.url,
                source = name,
                year = movie.releaseInfo.take(4).toIntOrNull()
            )
        }.also { Timber.d("[Stremio] found ${it.size} results for '$query'") }
    }

    override suspend fun getDownloadUrl(
        entity: AbstractSubtitleEntities.SubtitleEntity
    ): String? = entity.data.takeIf { it.isNotBlank() }
}
