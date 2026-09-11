package br.app.astrum.ts6.audio.opus

import kotlin.math.sqrt

internal object ParticipantGainMixer {
    data class Input(
        val samples: ShortArray,
        val gain: Float,
    )

    fun mix(inputs: List<Input>, sampleCount: Int): ShortArray {
        require(sampleCount >= 0)
        val audible = inputs.filter { it.gain > 0f && it.samples.isNotEmpty() }
        if (audible.isEmpty()) return ShortArray(0)

        val output = ShortArray(sampleCount)
        val normalization = if (audible.size == 1) {
            1.0
        } else {
            1.0 / sqrt(audible.size.toDouble())
        }
        for (sampleIndex in output.indices) {
            var sum = 0.0
            audible.forEach { input ->
                if (sampleIndex < input.samples.size) {
                    sum += input.samples[sampleIndex] * input.gain
                }
            }
            output[sampleIndex] = (sum * normalization)
                .toLong()
                .coerceIn(Short.MIN_VALUE.toLong(), Short.MAX_VALUE.toLong())
                .toShort()
        }
        return output
    }
}
