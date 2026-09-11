package br.app.astrum.ts6.audio.opus

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ParticipantGainMixerTest {
    @Test
    fun appliesIndependentGainAndIgnoresMutedInputs() {
        val output = ParticipantGainMixer.mix(
            inputs = listOf(
                ParticipantGainMixer.Input(shortArrayOf(1_000, -1_000), 1.5f),
                ParticipantGainMixer.Input(shortArrayOf(20_000, 20_000), 0f),
            ),
            sampleCount = 2,
        )

        assertArrayEquals(shortArrayOf(1_500, -1_500), output)
    }

    @Test
    fun normalizesMultipleTalkersAndClampsBoostedSamples() {
        val normalized = ParticipantGainMixer.mix(
            inputs = listOf(
                ParticipantGainMixer.Input(shortArrayOf(10_000), 1f),
                ParticipantGainMixer.Input(shortArrayOf(10_000), 1f),
            ),
            sampleCount = 1,
        )
        val clipped = ParticipantGainMixer.mix(
            inputs = listOf(ParticipantGainMixer.Input(shortArrayOf(30_000), 2f)),
            sampleCount = 1,
        )

        assertEquals(14_142, normalized.single().toInt())
        assertEquals(Short.MAX_VALUE, clipped.single())
    }
}
