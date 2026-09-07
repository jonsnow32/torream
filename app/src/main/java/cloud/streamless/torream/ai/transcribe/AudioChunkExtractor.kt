package cloud.streamless.torream.ai.transcribe

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.AudioFormat
import android.media.MediaFormat
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext

/**
 * Decodes the audio track of a media file into 16 kHz mono 16-bit WAV chunks — what Whisper wants,
 * and small enough (~32 KB/s, so ~9.6 MB per chunk) to stay under the providers' 25 MB upload cap.
 *
 * Chunks are handed to the caller one at a time so a two-hour file never needs its audio on disk
 * all at once; the caller is expected to upload and delete each chunk before asking for the next.
 *
 * Container support is whatever the device's [MediaExtractor] handles (MP4/MKV/WebM/TS on most
 * devices, generally not AVI) — unsupported input surfaces as a failure, not a silent empty result.
 */
object AudioChunkExtractor {

    const val TARGET_SAMPLE_RATE = 16000
    private const val CHUNK_SECONDS = 300
    private const val CHUNK_SAMPLES = TARGET_SAMPLE_RATE.toLong() * CHUNK_SECONDS
    private const val DEQUEUE_TIMEOUT_US = 10_000L

    /**
     * @param source `content://`, `file://`, a bare path, or an http(s) URL.
     * @param onChunk called per finished chunk with its start offset in seconds.
     */
    suspend fun extract(
        context: Context,
        source: String,
        headers: Map<String, String>,
        outputDir: File,
        onChunk: suspend (chunk: File, offsetSeconds: Double) -> Unit
    ) = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        try {
            extractor.setSource(context, source, headers)
            val trackIndex = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: error("No audio track in this file")

            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)!!
            val codec = MediaCodec.createDecoderByType(mime)
            try {
                codec.configure(inputFormat, null, null, 0)
                codec.start()
                decodeLoop(extractor, codec, outputDir, onChunk)
            } finally {
                runCatching { codec.stop() }
                codec.release()
            }
        } finally {
            extractor.release()
        }
    }

    private fun MediaExtractor.setSource(
        context: Context,
        source: String,
        headers: Map<String, String>
    ) {
        when {
            source.startsWith("content://") -> setDataSource(context, source.toUri(), null)
            source.startsWith("http://", true) || source.startsWith("https://", true) ->
                setDataSource(source, headers)
            source.startsWith("file://") -> setDataSource(source.toUri().path!!)
            else -> setDataSource(source)
        }
    }

    private suspend fun decodeLoop(
        extractor: MediaExtractor,
        codec: MediaCodec,
        outputDir: File,
        onChunk: suspend (File, Double) -> Unit
    ) {
        val bufferInfo = MediaCodec.BufferInfo()
        var resampler: Resampler? = null
        var channelCount = 1
        var isFloatOutput = false

        fun readOutputFormat() {
            val format = codec.outputFormat
            channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            // KEY_PCM_ENCODING is API 24+ and absent on most decoders; the literal + runCatching
            // keeps this working on minSdk 23 where the key simply isn't there.
            isFloatOutput = runCatching { format.getInteger("pcm-encoding") }
                .getOrNull() == AudioFormat.ENCODING_PCM_FLOAT
            resampler = Resampler(sampleRate, TARGET_SAMPLE_RATE)
            Timber.d("STT decode: ${sampleRate}Hz x$channelCount float=$isFloatOutput")
        }

        var writer: WavChunkWriter? = null
        var chunkIndex = 0
        var emittedSamples = 0L
        var inputDone = false

        fun startChunk(): WavChunkWriter =
            WavChunkWriter(File(outputDir, "stt_$chunkIndex.wav"), TARGET_SAMPLE_RATE)
                .also { writer = it }

        // Chunks finish mid-write while a codec output buffer is still checked out; emitting them
        // (a minutes-long upload) is deferred until after the buffer is released.
        val ready = ArrayDeque<Pair<File, Double>>()

        fun finishChunk() {
            val current = writer ?: return
            current.close()
            writer = null
            if (current.sampleCount > 0) {
                val offset = (emittedSamples - current.sampleCount).toDouble() / TARGET_SAMPLE_RATE
                ready.addLast(current.file to offset)
            } else {
                current.file.delete()
            }
            chunkIndex++
        }

        suspend fun drainReady() {
            while (ready.isNotEmpty()) {
                val (file, offset) = ready.removeFirst()
                try {
                    onChunk(file, offset)
                } finally {
                    file.delete()
                }
            }
        }

        try {
            while (true) {
                coroutineContext.ensureActive()

                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(
                                inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> readOutputFormat()

                    outIndex >= 0 -> {
                        if (bufferInfo.size > 0) {
                            if (resampler == null) readOutputFormat()
                            val buffer = codec.getOutputBuffer(outIndex)!!
                            val mono = toMono(buffer, bufferInfo, channelCount, isFloatOutput)
                            val out = resampler!!.process(mono)
                            var written = 0
                            while (written < out.size) {
                                val target = writer ?: startChunk()
                                val room = (CHUNK_SAMPLES - target.sampleCount).toInt()
                                val n = minOf(room, out.size - written)
                                target.write(out, written, n)
                                written += n
                                emittedSamples += n
                                if (target.sampleCount >= CHUNK_SAMPLES) finishChunk()
                            }
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        val endOfStream =
                            bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        if (endOfStream) finishChunk()
                        drainReady()
                        if (endOfStream) return
                    }
                }
            }
        } finally {
            writer?.let { runCatching { it.close() }; it.file.delete() }
            ready.forEach { it.first.delete() }
        }
    }

    /** Downmixes an interleaved PCM buffer to a mono float array in [-1, 1]. */
    private fun toMono(
        buffer: ByteBuffer,
        info: MediaCodec.BufferInfo,
        channelCount: Int,
        isFloat: Boolean
    ): FloatArray {
        buffer.position(info.offset)
        buffer.limit(info.offset + info.size)
        val ordered = buffer.order(ByteOrder.nativeOrder())

        return if (isFloat) {
            val src = ordered.asFloatBuffer()
            val frames = src.remaining() / channelCount
            FloatArray(frames) { f ->
                var sum = 0f
                for (c in 0 until channelCount) sum += src.get(f * channelCount + c)
                sum / channelCount
            }
        } else {
            val src = ordered.asShortBuffer()
            val frames = src.remaining() / channelCount
            FloatArray(frames) { f ->
                var sum = 0f
                for (c in 0 until channelCount) sum += src.get(f * channelCount + c) / 32768f
                sum / channelCount
            }
        }
    }

}
