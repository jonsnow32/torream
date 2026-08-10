package cloud.streamless.torream.ai.translate

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleTranslatorTest {

    private fun cue(i: Int, text: String) = SubtitleCue(i, "00:00:0$i.000", "00:00:0${i + 1}.000", text)

    @Test
    fun `empty cues returns success with empty list`() = runBlocking {
        val result = SubtitleTranslator.translate(emptyList(), "es", { _, _ -> Result.success("") })
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `rejects more than MAX_CUES`() = runBlocking {
        val cues = (1..SubtitleTranslator.MAX_CUES + 1).map { cue(it % 9, "text $it") }
        val result = SubtitleTranslator.translate(cues, "es", { _, _ -> Result.success("") })
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `translates single chunk and preserves timing`() = runBlocking {
        val cues = listOf(cue(1, "Hello"), cue(2, "World"))
        val progressCalls = mutableListOf<Pair<Int, Int>>()

        val result = SubtitleTranslator.translate(
            cues, "es",
            chatCompletion = { _, _ -> Result.success("1. Hola\n2. Mundo") },
            onProgress = { done, total -> progressCalls.add(done to total) }
        )

        assertTrue(result.isSuccess)
        val translated = result.getOrThrow()
        assertEquals(listOf("Hola", "Mundo"), translated.map { it.text })
        assertEquals(cues[0].startTime, translated[0].startTime)
        assertEquals(cues[1].endTime, translated[1].endTime)
        assertEquals(listOf(2 to 2), progressCalls)
    }

    @Test
    fun `aborts on line count mismatch`() = runBlocking {
        val cues = listOf(cue(1, "Hello"), cue(2, "World"))

        val result = SubtitleTranslator.translate(
            cues, "es",
            chatCompletion = { _, _ -> Result.success("1. Hola") } // missing line 2
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `propagates provider failure`() = runBlocking {
        val cues = listOf(cue(1, "Hello"))
        val error = RuntimeException("boom")

        val result = SubtitleTranslator.translate(
            cues, "es",
            chatCompletion = { _, _ -> Result.failure(error) }
        )

        assertTrue(result.isFailure)
        assertEquals(error, result.exceptionOrNull())
    }

    @Test
    fun `splits large cue lists into multiple chunks`() = runBlocking {
        val cues = (1..45).map { cue(it % 9, "text $it") }
        var callCount = 0

        val result = SubtitleTranslator.translate(
            cues, "es",
            chatCompletion = { _, userPrompt ->
                callCount++
                val lineCount = userPrompt.lines().size
                Result.success((1..lineCount).joinToString("\n") { "$it. translated" })
            }
        )

        assertTrue(result.isSuccess)
        assertEquals(2, callCount) // 45 cues / 40-per-chunk => 2 chunks
        assertEquals(45, result.getOrThrow().size)
    }
}
