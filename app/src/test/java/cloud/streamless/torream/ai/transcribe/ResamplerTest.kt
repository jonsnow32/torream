package cloud.streamless.torream.ai.transcribe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class ResamplerTest {

    @Test
    fun `pass-through rate keeps sample count`() {
        val resampler = Resampler(16000, 16000)
        val out = resampler.process(FloatArray(1000) { 0.5f })
        assertEquals(1000, out.size)
    }

    @Test
    fun `3x downsample produces a third of the samples`() {
        val resampler = Resampler(48000, 16000)
        val out = resampler.process(FloatArray(4800) { 0f })
        assertEquals(1600, out.size)
    }

    /** Chunk boundaries must not lose or duplicate samples — that would drift subtitle timing. */
    @Test
    fun `split input yields the same total as one buffer`() {
        val samples = FloatArray(48000) { sin(2 * PI * 440 * it / 48000.0).toFloat() }

        val whole = Resampler(48000, 16000).process(samples).size

        val chunked = Resampler(48000, 16000).let { r ->
            samples.toList().chunked(1024)
                .sumOf { r.process(it.toFloatArray()).size }
        }

        assertEquals(whole, chunked)
        assertEquals(16000, whole)
    }

    @Test
    fun `interpolates linearly between input samples`() {
        // src 2 Hz -> dst 4 Hz: outputs land at 0, 0.5, 1.0, ... of the input grid.
        val out = Resampler(2, 4).process(floatArrayOf(0f, 1f))
        // positions 0(prev=0), 0.5(=0.0), 1.0(=input[0]=0), 1.5(=0.5) -> 4 samples
        assertEquals(4, out.size)
        assertEquals(0, out[0].toInt())
        assertTrue(abs(out[3] - 16384) < 64)
    }

    @Test
    fun `clamps values outside the normalised range`() {
        val out = Resampler(16000, 16000).process(floatArrayOf(5f, -5f))
        assertTrue(out.all { it in -32767..32767 })
    }
}
