package br.app.astrum.ts6.audio.opus

internal class NativeRnNoiseProcessor : AutoCloseable {
    private var handle = nativeCreate()

    fun processInPlace(pcm: ShortArray): Float {
        check(handle != 0L) { "RNNoise processor is closed" }
        require(pcm.isNotEmpty() && pcm.size % FRAME_SAMPLES == 0) {
            "PCM must contain complete $FRAME_SAMPLES-sample RNNoise frames"
        }
        return nativeProcessInPlace(handle, pcm)
    }

    override fun close() {
        val current = handle
        if (current == 0L) return
        handle = 0L
        nativeDestroy(current)
    }

    private companion object {
        const val FRAME_SAMPLES = 480

        init {
            System.loadLibrary("ts3opus_jni")
        }

        @JvmStatic
        private external fun nativeCreate(): Long

        @JvmStatic
        private external fun nativeProcessInPlace(handle: Long, pcm: ShortArray): Float

        @JvmStatic
        private external fun nativeDestroy(handle: Long)
    }
}
