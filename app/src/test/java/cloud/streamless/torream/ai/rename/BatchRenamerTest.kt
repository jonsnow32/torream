package cloud.streamless.torream.ai.rename

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchRenamerTest {

    @Test
    fun `empty list returns success with empty list`() = runBlocking {
        val result = BatchRenamer.suggestNames(emptyList()) { _, _ -> Result.success("") }
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `rejects more than MAX_FILES`() = runBlocking {
        val names = (1..BatchRenamer.MAX_FILES + 1).map { "file$it.mkv" }
        val result = BatchRenamer.suggestNames(names) { _, _ -> Result.success("") }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `pairs old and new names in order`() = runBlocking {
        val names = listOf("The.Matrix.1999.720p.mkv", "matrix2-1080p-x264.mkv")

        val result = BatchRenamer.suggestNames(names) { _, _ ->
            Result.success("1. The Matrix (1999).mkv\n2. The Matrix Reloaded (2003).mkv")
        }

        assertTrue(result.isSuccess)
        val pairs = result.getOrThrow()
        assertEquals(
            listOf(
                "The.Matrix.1999.720p.mkv" to "The Matrix (1999).mkv",
                "matrix2-1080p-x264.mkv" to "The Matrix Reloaded (2003).mkv"
            ),
            pairs
        )
    }

    @Test
    fun `aborts on count mismatch`() = runBlocking {
        val names = listOf("a.mkv", "b.mkv")

        val result = BatchRenamer.suggestNames(names) { _, _ -> Result.success("1. A.mkv") }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `propagates provider failure`() = runBlocking {
        val error = RuntimeException("boom")
        val result = BatchRenamer.suggestNames(listOf("a.mkv")) { _, _ -> Result.failure(error) }

        assertTrue(result.isFailure)
        assertEquals(error, result.exceptionOrNull())
    }
}
