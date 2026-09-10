package io.github.ts3mobile.audio.opus

import android.content.Context
import com.kaleyra.noise_filter.DeepFilterNet
import com.rikorose.deepfilternet.NativeDeepFilterNet
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

internal class NativeDeepFilterProcessor(
    context: Context,
    attenuationLimit: Float = DEFAULT_ATTENUATION_LIMIT,
) : AutoCloseable {
    private val nativeFilter: DeepFilterNet?
    private val directBuffer: ByteBuffer?
    private val modelReady = AtomicBoolean(false)
    private val isClosed = AtomicBoolean(false)
    private val lock = Any()

    init {
        var filter: DeepFilterNet? = null
        var buffer: ByteBuffer? = null
        try {
            val df = NativeDeepFilterNet(
                context = context.applicationContext,
                attenuationLimit = attenuationLimit,
            )
            df.onModelLoaded {
                modelReady.set(true)
                runCatching {
                    df.setPostFilterBeta(DEFAULT_POST_FILTER_BETA)
                }
            }
            val frameLengthBytes = df.frameLength
            val bufferSize = if (frameLengthBytes > 0) frameLengthBytes.toInt() else DEFAULT_FRAME_BYTES
            buffer = ByteBuffer.allocateDirect(bufferSize.coerceAtLeast(DEFAULT_FRAME_BYTES))
                .order(ByteOrder.LITTLE_ENDIAN)
            filter = df
        } catch (error: Throwable) {
            System.err.println("TS3_AUDIO: failed to initialize DeepFilterNet: ${error.message}")
            filter = null
            buffer = null
        }
        nativeFilter = filter
        directBuffer = buffer
    }

    val isReady: Boolean
        get() = modelReady.get() && !isClosed.get() && nativeFilter != null

    fun processInPlace(pcm: ShortArray): Float {
        if (isClosed.get() || !modelReady.get() || nativeFilter == null || directBuffer == null) return 0f
        if (pcm.isEmpty()) return 0f

        synchronized(lock) {
            if (isClosed.get() || !modelReady.get()) return 0f
            val buffer = directBuffer
            val sampleCount = pcm.size
            val requiredBytes = sampleCount * 2

            if (buffer.capacity() < requiredBytes) {
                return 0f
            }

            buffer.clear()
            for (i in 0 until sampleCount) {
                buffer.putShort(pcm[i])
            }
            buffer.flip()

            val metric = nativeFilter.processFrame(buffer)
            if (metric >= 0f) {
                buffer.position(0)
                for (i in 0 until sampleCount) {
                    pcm[i] = buffer.getShort()
                }
                return metric
            }
            return 0f
        }
    }

    override fun close() {
        if (isClosed.compareAndSet(false, true)) {
            synchronized(lock) {
                runCatching { nativeFilter?.release() }
            }
        }
    }

    companion object {
        const val DEFAULT_ATTENUATION_LIMIT = 100.0f
        const val DEFAULT_POST_FILTER_BETA = 0.05f
        const val DEFAULT_FRAME_SAMPLES = 480
        const val DEFAULT_FRAME_BYTES = DEFAULT_FRAME_SAMPLES * 2
    }
}
