package cloud.streamless.torream.ai.transcribe

import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Streaming linear-interpolation resampler, mono float in / 16-bit PCM out. Keeps the last input
 * sample and the fractional read position across calls so buffer boundaries neither click nor drift.
 */
internal class Resampler(srcRate: Int, dstRate: Int) {

    private val step = srcRate.toDouble() / dstRate
    private var prev = 0f

    /** Read position relative to [prev], which sits at position 0. */
    private var pos = 0.0

    fun process(input: FloatArray): ShortArray {
        if (input.isEmpty()) return ShortArray(0)
        val n = input.size
        // sample(0) == prev, sample(k) == input[k - 1]
        fun sample(k: Int) = if (k <= 0) prev else input[k - 1]

        val out = ShortArray(((n - pos) / step).toInt().coerceAtLeast(0) + 1)
        var count = 0
        while (pos < n) {
            val i = floor(pos).toInt()
            val frac = (pos - i).toFloat()
            val a = sample(i)
            val b = sample(i + 1)
            val value = a + (b - a) * frac
            out[count++] = (value.coerceIn(-1f, 1f) * 32767f).roundToInt().toShort()
            pos += step
        }
        prev = input[n - 1]
        pos -= n
        return if (count == out.size) out else out.copyOf(count)
    }
}
