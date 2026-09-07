package cloud.streamless.torream.ai.transcribe

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * Writes 16-bit mono PCM to a WAV file, patching the two size fields in the header on [close].
 * Whisper endpoints accept WAV directly, so no encoder is needed — just the 44-byte RIFF header.
 */
internal class WavChunkWriter(val file: File, private val sampleRate: Int) {

    private val out = BufferedOutputStream(FileOutputStream(file))
    private val scratch = ByteArray(8192)
    var sampleCount = 0L
        private set

    init {
        out.write(ByteArray(HEADER_SIZE)) // placeholder, rewritten in close()
    }

    fun write(samples: ShortArray, from: Int, count: Int) {
        var offset = 0
        while (offset < count) {
            val n = minOf((count - offset), scratch.size / 2)
            for (i in 0 until n) {
                val s = samples[from + offset + i].toInt()
                scratch[i * 2] = (s and 0xFF).toByte()
                scratch[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
            }
            out.write(scratch, 0, n * 2)
            offset += n
        }
        sampleCount += count
    }

    fun close() {
        out.flush()
        out.close()
        val dataSize = (sampleCount * 2).toInt()
        RandomAccessFile(file, "rw").use { raf ->
            raf.write(buildHeader(dataSize))
        }
    }

    private fun buildHeader(dataSize: Int) = ByteArray(HEADER_SIZE).apply {
        val byteRate = sampleRate * 2 // mono, 16-bit
        putAscii(0, "RIFF")
        putInt(4, 36 + dataSize)
        putAscii(8, "WAVE")
        putAscii(12, "fmt ")
        putInt(16, 16)          // PCM chunk size
        putShort(20, 1)         // format = PCM
        putShort(22, 1)         // channels = mono
        putInt(24, sampleRate)
        putInt(28, byteRate)
        putShort(32, 2)         // block align
        putShort(34, 16)        // bits per sample
        putAscii(36, "data")
        putInt(40, dataSize)
    }

    private fun ByteArray.putAscii(at: Int, value: String) {
        value.forEachIndexed { i, c -> this[at + i] = c.code.toByte() }
    }

    private fun ByteArray.putInt(at: Int, value: Int) {
        for (i in 0 until 4) this[at + i] = ((value shr (8 * i)) and 0xFF).toByte()
    }

    private fun ByteArray.putShort(at: Int, value: Int) {
        for (i in 0 until 2) this[at + i] = ((value shr (8 * i)) and 0xFF).toByte()
    }

    private companion object {
        const val HEADER_SIZE = 44
    }
}
