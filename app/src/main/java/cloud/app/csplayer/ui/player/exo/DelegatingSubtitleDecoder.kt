package cloud.app.csplayer.ui.player.exo

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.SimpleSubtitleDecoder
import androidx.media3.extractor.text.Subtitle
import androidx.media3.extractor.text.SubtitleParser

@OptIn(UnstableApi::class)
internal class DelegatingSubtitleDecoder(
    name: String,
    private val subtitleParser: SubtitleParser
) :
    SimpleSubtitleDecoder(name) {
    override fun decode(data: ByteArray, length: Int, reset: Boolean): Subtitle {
        if (reset) {
            subtitleParser.reset()
        }
        return subtitleParser.parseToLegacySubtitle(data,  /* offset= */0, length)
    }
    fun getParser(): SubtitleParser {
        return subtitleParser
    }
}
