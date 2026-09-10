package io.github.ts3mobile.audio.opus

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class NativeTransientSuppressorTest {
    @Test
    fun suppressesImpulsiveClicks() {
        val random = Random(42)
        NativeTransientSuppressor().use { suppressor ->
            // Warm up with moderate background noise
            repeat(10) {
                val background = ShortArray(FRAME_SAMPLES) {
                    random.nextInt(-500, 501).toShort()
                }
                suppressor.processInPlace(background)
            }

            // Inject a sharp mechanical keyboard click (impulsive transient)
            val clickFrame = ShortArray(FRAME_SAMPLES) {
                random.nextInt(-500, 501).toShort()
            }
            // Add sharp spike at sample 100
            for (i in 100 until 116) {
                clickFrame[i] = (25_000 - (i - 100) * 800).toShort()
            }

            val maxBefore = clickFrame.maxOf { abs(it.toInt()) }
            val score = suppressor.processInPlace(clickFrame)
            val maxAfter = clickFrame.maxOf { abs(it.toInt()) }

            Log.i(LOG_TAG, "Transient score: $score, peak before: $maxBefore, peak after: $maxAfter")
            assertTrue("Expected transient score > 0 for click spike", score > 0.1f)
            assertTrue("Transient suppressor did not attenuate the click peak", maxAfter < maxBefore * 0.75)
        }
    }

    @Test
    fun processesTenMillisecondFramesWithinRealtimeBudget() {
        val random = Random(1234)
        val pcm = ShortArray(FRAME_SAMPLES) {
            random.nextInt(-1000, 1001).toShort()
        }

        NativeTransientSuppressor().use { suppressor ->
            val started = System.nanoTime()
            val iterations = 50
            repeat(iterations) {
                suppressor.processInPlace(pcm)
            }
            val elapsedNanos = System.nanoTime() - started
            val avgMicros = (elapsedNanos / iterations) / 1000L
            Log.i(LOG_TAG, "TransientSuppressor average frame time: ${avgMicros}us")
            assertTrue("Processing time exceeded 1ms budget", avgMicros < 1000L)
        }
    }

    private companion object {
        const val FRAME_SAMPLES = 480
        const val LOG_TAG = "TransientSuppressorTest"
    }
}
