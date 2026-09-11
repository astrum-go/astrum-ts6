package br.app.astrum.ts6.audio.opus

internal class NativeOpusDecoder : AutoCloseable {
    private var handle = nativeCreate(SAMPLE_RATE, CHANNEL_COUNT)

    fun decode(packet: ByteArray?, frameSize: Int = MAX_FRAME_SIZE): ShortArray {
        check(handle != 0L) { "Opus decoder is closed" }
        require(frameSize in 1..MAX_FRAME_SIZE) { "Invalid Opus frame size: $frameSize" }
        return nativeDecode(handle, packet, frameSize)
    }

    fun reset() {
        check(handle != 0L) { "Opus decoder is closed" }
        nativeReset(handle)
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
        const val MAX_FRAME_SIZE = 5_760

        init {
            System.loadLibrary("ts3opus_jni")
        }

        @JvmStatic
        private external fun nativeCreate(sampleRate: Int, channels: Int): Long

        @JvmStatic
        private external fun nativeDecode(handle: Long, packet: ByteArray?, frameSize: Int): ShortArray

        @JvmStatic
        private external fun nativeReset(handle: Long)

        @JvmStatic
        private external fun nativeDestroy(handle: Long)
    }
}
