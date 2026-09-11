package br.app.astrum.ts6.audio.opus

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.PI
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class NativeOpusCodecTest {
    @Test
    fun encodesAndDecodesTwentyMillisecondsOfPcm() {
        val pcm = ShortArray(FRAME_SAMPLES) { index ->
            (sin(2.0 * PI * TEST_FREQUENCY * index / SAMPLE_RATE) * TEST_AMPLITUDE)
                .toInt()
                .toShort()
        }

        val encoded = NativeOpusEncoder().use { encoder -> encoder.encode(pcm) }
        val decoded = NativeOpusDecoder().use { decoder -> decoder.decode(encoded) }

        assertTrue(encoded.isNotEmpty())
        assertEquals(FRAME_SAMPLES, decoded.size)
        assertTrue(decoded.any { it.toInt() != 0 })
    }

    private companion object {
        const val SAMPLE_RATE = 48_000.0
        const val FRAME_SAMPLES = 960
        const val TEST_FREQUENCY = 1_000.0
        const val TEST_AMPLITUDE = 12_000.0
    }
}
