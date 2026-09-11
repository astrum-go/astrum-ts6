package br.app.astrum.ts6.audio.opus

internal class NativeOpusEncoder : AutoCloseable {
    private var handle = nativeCreate(SAMPLE_RATE, CHANNEL_COUNT, BITRATE)

    fun encode(pcm: ShortArray): ByteArray {
        check(handle != 0L) { "Opus encoder is closed" }
        require(pcm.size == FRAME_SAMPLES) { "Expected $FRAME_SAMPLES PCM samples" }
        return nativeEncode(handle, pcm)
    }

    override fun close() {
        val current = handle
        if (current == 0L) return
        handle = 0L
        nativeDestroy(current)
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
        const val CHANNEL_COUNT = 1
        const val BITRATE = 64_000
        const val FRAME_SAMPLES = 960

        init {
            System.loadLibrary("ts3opus_jni")
        }

        @JvmStatic
        private external fun nativeCreate(sampleRate: Int, channels: Int, bitrate: Int): Long

        @JvmStatic
        private external fun nativeEncode(handle: Long, pcm: ShortArray): ByteArray

        @JvmStatic
        private external fun nativeDestroy(handle: Long)
    }
}
