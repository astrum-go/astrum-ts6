package io.github.ts3mobile.audio.opus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceGateTest {
    @Test
    fun attackHoldAndReleaseAreSmooth() {
        val gate = VoiceGate()
        val frame = ShortArray(FRAME_SAMPLES) { 10_000 }

        gate.processInPlace(frame, vadProbability = 0f)
        assertTrue(gate.currentGain < 1f)

        gate.processInPlace(frame, vadProbability = 0.9f)
        val firstSpeechGain = gate.currentGain
        gate.processInPlace(frame, vadProbability = 0.9f)
        val secondSpeechGain = gate.currentGain
        gate.processInPlace(frame, vadProbability = 0.9f)
        assertTrue(firstSpeechGain < secondSpeechGain)
        assertEquals(1f, gate.currentGain, 0f)

        gate.processInPlace(frame, vadProbability = 0f)
        assertEquals(1f, gate.currentGain, 0f)
        repeat(20) { gate.processInPlace(frame, vadProbability = 0f) }
        assertTrue(gate.currentGain < 1f)
        assertTrue(gate.currentGain >= 0.15f)
    }

    @Test
    fun lowVadAttenuatesInPlaceWithoutAllocatingAnotherFrame() {
        val gate = VoiceGate()
        val frame = ShortArray(FRAME_SAMPLES) { 10_000 }

        gate.processInPlace(frame, vadProbability = 0f)

        assertEquals((10_000 * gate.currentGain).toInt(), frame[0].toInt())
    }

    @Test
    fun intermediateVadDoesNotOpenAResetGate() {
        val gate = VoiceGate()

        gate.reset()
        gate.processInPlace(ShortArray(FRAME_SAMPLES) { 10_000 }, vadProbability = 0.2f)

        assertEquals(0.15f, gate.currentGain, 0f)
    }

    @Test
    fun intermediateVadContinuesAnAttackThatAlreadyStarted() {
        val gate = VoiceGate()

        gate.reset()
        gate.processInPlace(ShortArray(FRAME_SAMPLES), vadProbability = 0.9f)
        val firstAttackGain = gate.currentGain
        gate.processInPlace(ShortArray(FRAME_SAMPLES), vadProbability = 0.5f)
        val secondAttackGain = gate.currentGain
        gate.processInPlace(ShortArray(FRAME_SAMPLES), vadProbability = 0.5f)

        assertTrue(secondAttackGain > firstAttackGain)
        assertEquals(1f, gate.currentGain, 0f)
    }

    @Test
    fun intermediateVadResumesAReleaseSmoothly() {
        val gate = VoiceGate()
        val frame = ShortArray(FRAME_SAMPLES)

        repeat(3) { gate.processInPlace(frame, vadProbability = 0.9f) }
        repeat(22) { gate.processInPlace(frame, vadProbability = 0f) }
        val partialReleaseGain = gate.currentGain

        gate.processInPlace(frame, vadProbability = 0.5f)
        val resumedGain = gate.currentGain
        repeat(2) { gate.processInPlace(frame, vadProbability = 0.5f) }

        assertTrue(partialReleaseGain < 1f)
        assertTrue(resumedGain > partialReleaseGain)
        assertEquals(1f, gate.currentGain, 0f)
    }

    @Test
    fun processingBudgetIsRecordedPerFrame() {
        val gate = VoiceGate()
        val timings = LongArray(BENCHMARK_FRAMES)
        repeat(WARMUP_FRAMES) {
            gate.processInPlace(ShortArray(FRAME_SAMPLES), vadProbability = 0f)
        }
        repeat(BENCHMARK_FRAMES) { index ->
            val frame = ShortArray(FRAME_SAMPLES)
            val started = System.nanoTime()
            gate.processInPlace(frame, vadProbability = 0.7f)
            timings[index] = System.nanoTime() - started
        }
        timings.sort()
        val p95 = timings[timings.size * 95 / 100]
        System.out.println("VoiceGate 10ms frame p95=${p95 / 1_000}us")
        assertTrue("VoiceGate p95 exceeded 1ms", p95 < 1_000_000L)
    }

    @Test
    fun transientClicksDoNotOpenClosedGate() {
        val gate = VoiceGate()
        val frame = ShortArray(FRAME_SAMPLES) { 15_000 }

        // Simula clique de teclado mecânico: pico alto com VAD RNNoise falso positivo (0.60)
        // porém com detecção de transiente impulsivo alta (transientScore = 0.8)
        gate.processInPlace(frame, vadProbability = 0.60f, transientScore = 0.8f)

        // O gate deve permanecer fechado (ganho mínimo)
        assertEquals(0.15f, gate.currentGain, 0.001f)
    }

    private companion object {
        const val FRAME_SAMPLES = 480
        const val WARMUP_FRAMES = 20
        const val BENCHMARK_FRAMES = 100
    }
}
