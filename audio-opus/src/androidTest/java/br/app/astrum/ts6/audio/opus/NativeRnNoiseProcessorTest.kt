package br.app.astrum.ts6.audio.opus

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.sqrt
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class NativeRnNoiseProcessorTest {
    @Test
    fun suppressesStationaryNoise() {
        val random = Random(4103)
        var inputEnergy = 0.0
        var outputEnergy = 0.0
        var sampleCount = 0L

        NativeRnNoiseProcessor().use { processor ->
            repeat(TEST_FRAMES) { frameIndex ->
                val pcm = ShortArray(FRAME_SAMPLES) {
                    random.nextInt(-NOISE_AMPLITUDE, NOISE_AMPLITUDE + 1).toShort()
                }
                if (frameIndex >= WARMUP_FRAMES) inputEnergy += pcm.energy()
                processor.processInPlace(pcm)
                if (frameIndex >= WARMUP_FRAMES) {
                    outputEnergy += pcm.energy()
                    sampleCount += pcm.size
                }
            }
        }

        val inputRms = sqrt(inputEnergy / sampleCount)
        val outputRms = sqrt(outputEnergy / sampleCount)
        Log.i(LOG_TAG, "RNNoise stationary RMS $inputRms -> $outputRms")
        assertTrue("RNNoise did not reduce stationary noise", outputRms < inputRms * 0.65)
    }

    @Test
    fun processesTenMillisecondFramesWithinRealtimeBudget() {
        val random = Random(9987)
        val source = ShortArray(FRAME_SAMPLES) {
            random.nextInt(-NOISE_AMPLITUDE, NOISE_AMPLITUDE + 1).toShort()
        }
        val timings = LongArray(BENCHMARK_FRAMES)

        NativeRnNoiseProcessor().use { processor ->
            repeat(WARMUP_FRAMES) { processor.processInPlace(source.copyOf()) }
            repeat(BENCHMARK_FRAMES) { index ->
                val pcm = source.copyOf()
                val started = System.nanoTime()
                processor.processInPlace(pcm)
                timings[index] = System.nanoTime() - started
            }
        }

        timings.sort()
        val p50 = timings[timings.size / 2]
        val p95 = timings[(timings.size * 95 / 100).coerceAtMost(timings.lastIndex)]
        val max = timings.last()
        Log.i(
            LOG_TAG,
            "RNNoise 10ms processing " +
                "p50=${p50 / 1_000}us p95=${p95 / 1_000}us max=${max / 1_000}us",
        )
        assertTrue("RNNoise P95 exceeded its 10ms realtime budget", p95 < FRAME_DURATION_NANOS)
    }

    private fun ShortArray.energy(): Double = sumOf { sample ->
        val value = sample.toDouble()
        value * value
    }

    private companion object {
        const val FRAME_SAMPLES = 480
        const val FRAME_DURATION_NANOS = 10_000_000L
        const val LOG_TAG = "TS3_AUDIO_TEST"
        const val NOISE_AMPLITUDE = 2_000
        const val WARMUP_FRAMES = 30
        const val TEST_FRAMES = 180
        const val BENCHMARK_FRAMES = 300
    }
}
