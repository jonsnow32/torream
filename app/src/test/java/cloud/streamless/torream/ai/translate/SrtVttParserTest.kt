package cloud.streamless.torream.ai.translate

import org.junit.Assert.assertEquals
import org.junit.Test

class SrtVttParserTest {

    @Test
    fun `parses basic SRT`() {
        val srt = """
            1
            00:00:01,000 --> 00:00:04,000
            Hello there

            2
            00:00:05,500 --> 00:00:06,250
            How are you?
        """.trimIndent()

        val cues = SrtVttParser.parse(srt)

        assertEquals(2, cues.size)
        assertEquals("00:00:01.000", cues[0].startTime)
        assertEquals("00:00:04.000", cues[0].endTime)
        assertEquals("Hello there", cues[0].text)
        assertEquals("How are you?", cues[1].text)
    }

    @Test
    fun `parses multi-line cue text`() {
        val srt = """
            1
            00:00:01,000 --> 00:00:04,000
            Line one
            Line two
        """.trimIndent()

        val cues = SrtVttParser.parse(srt)

        assertEquals(1, cues.size)
        assertEquals("Line one\nLine two", cues[0].text)
    }

    @Test
    fun `parses VTT and skips header`() {
        val vtt = """
            WEBVTT

            00:00:01.000 --> 00:00:04.000
            Hello there

            00:00:05.500 --> 00:00:06.250
            How are you?
        """.trimIndent()

        val cues = SrtVttParser.parse(vtt)

        assertEquals(2, cues.size)
        assertEquals("00:00:01.000", cues[0].startTime)
        assertEquals("Hello there", cues[0].text)
    }

    @Test
    fun `serialize round-trips SRT`() {
        val cues = listOf(
            SubtitleCue(1, "00:00:01.000", "00:00:04.000", "Hello there"),
            SubtitleCue(2, "00:00:05.500", "00:00:06.250", "How are you?")
        )

        val srt = SrtVttParser.serialize(cues, isVtt = false)
        val reparsed = SrtVttParser.parse(srt)

        assertEquals(cues.map { it.startTime to it.text }, reparsed.map { it.startTime to it.text })
        assert(srt.contains("00:00:01,000 --> 00:00:04,000"))
    }

    @Test
    fun `serialize emits WEBVTT header with dot separator`() {
        val cues = listOf(SubtitleCue(1, "00:00:01.000", "00:00:04.000", "Hello there"))

        val vtt = SrtVttParser.serialize(cues, isVtt = true)

        assert(vtt.startsWith("WEBVTT\n\n"))
        assert(vtt.contains("00:00:01.000 --> 00:00:04.000"))
    }

    @Test
    fun `ignores blank content`() {
        assertEquals(0, SrtVttParser.parse("").size)
        assertEquals(0, SrtVttParser.parse("WEBVTT\n\n").size)
    }
}
