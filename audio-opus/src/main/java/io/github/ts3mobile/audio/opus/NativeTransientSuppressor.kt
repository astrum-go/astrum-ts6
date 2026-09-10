package io.github.ts3mobile.audio.opus

internal class NativeTransientSuppressor(
    sampleRate: Int = DEFAULT_SAMPLE_RATE,
) : AutoCloseable {
    private var handle = nativeCreate(sampleRate)

    fun processInPlace(pcm: ShortArray): Float {
        check(handle != 0L) { "TransientSuppressor is closed" }
        if (pcm.isEmpty()) return 0f
        return nativeProcessInPlace(handle, pcm)
    }

    fun reset() {
        val current = handle
        if (current != 0L) {
            nativeReset(current)
        }
    }

    override fun close() {
        val current = handle
        if (current == 0L) return
        handle = 0L
        nativeDestroy(current)
    }

    private companion object {
        const val DEFAULT_SAMPLE_RATE = 48_000

        init {
            System.loadLibrary("ts3opus_jni")
        }

        @JvmStatic
        private external fun nativeCreate(sampleRate: Int): Long

        @JvmStatic
        private external fun nativeProcessInPlace(handle: Long, pcm: ShortArray): Float

        @JvmStatic
        private external fun nativeReset(handle: Long)

        @JvmStatic
        private external fun nativeDestroy(handle: Long)
    }
}
