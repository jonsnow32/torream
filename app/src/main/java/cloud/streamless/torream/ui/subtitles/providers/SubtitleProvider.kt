package cloud.streamless.torream.ui.subtitles.providers

import cloud.streamless.torream.ui.subtitles.AbstractSubtitleEntities

interface SubtitleProvider {
    val name: String
    suspend fun search(query: String, lang: String?): List<AbstractSubtitleEntities.SubtitleEntity>
    suspend fun getDownloadUrl(entity: AbstractSubtitleEntities.SubtitleEntity): String?
}
