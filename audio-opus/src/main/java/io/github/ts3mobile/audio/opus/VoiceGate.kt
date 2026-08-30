package io.github.ts3mobile.audio.opus

import kotlin.math.min

/** A deterministic VAD-driven expander that operates in-place on one audio frame. */
internal class VoiceGate(
    private val openThreshold: Float = OPEN_THRESHOLD,
    private val closeThreshold: Float = CLOSE_THRESHOLD,
    private val attackFrames: Int = ATTACK_FRAMES,
    private val holdFrames: Int = HOLD_FRAMES,
    private val releaseFrames: Int = RELEASE_FRAMES,
    private val minimumGain: Float = MINIMUM_GAIN,
) {
    private var state = GateState.CLOSED
    private var gain = minimumGain
    private var attackProgressFrames = 0
    private var recoveryProgressFrames = 0
    private var remainingHoldFrames = 0

    val currentGain: Float
        get() = gain

    fun reset() {
        state = GateState.CLOSED
        gain = minimumGain
        attackProgressFrames = 0
        recoveryProgressFrames = 0
        remainingHoldFrames = 0
    }

    fun processInPlace(pcm: ShortArray, vadProbability: Float) {
        val probability = vadProbability.coerceIn(0f, 1f)
        when {
            probability >= openThreshold -> {
                continueAttack()
                remainingHoldFrames = holdFrames
            }

            probability >= closeThreshold -> {
                when (state) {
                    GateState.CLOSED -> Unit
                    GateState.ATTACKING -> {
                        advanceAttack()
                        remainingHoldFrames = holdFrames
                    }
                    GateState.RELEASING -> {
                        continueAttack()
                        remainingHoldFrames = holdFrames
                    }
                    GateState.OPEN -> {
                        remainingHoldFrames = holdFrames
                    }
                }
            }

            else -> {
                if (remainingHoldFrames > 0) {
                    remainingHoldFrames--
                } else {
                    gain = (gain - (1f - minimumGain) / releaseFrames)
                        .coerceAtLeast(minimumGain)
                    if (gain <= minimumGain) {
                        state = GateState.CLOSED
                        attackProgressFrames = 0
                        recoveryProgressFrames = 0
                    } else {
                        state = GateState.RELEASING
                        recoveryProgressFrames = 0
                    }
                }
            }
        }

        for (index in pcm.indices) {
            pcm[index] = (pcm[index].toFloat() * gain).toInt().toShort()
        }
    }

    private fun advanceAttack() {
        if (state == GateState.CLOSED) state = GateState.ATTACKING
        attackProgressFrames = min(attackProgressFrames + 1, attackFrames)
        val attackProgress = attackProgressFrames.toFloat() / attackFrames
        gain = minimumGain + (1f - minimumGain) * attackProgress
        if (attackProgressFrames == attackFrames) state = GateState.OPEN
    }

    private fun continueAttack() {
        if (state == GateState.RELEASING) {
            state = GateState.ATTACKING
            recoveryProgressFrames = min(recoveryProgressFrames + 1, attackFrames)
            val remainingSteps = attackFrames - recoveryProgressFrames + 1
            gain += (1f - gain) / remainingSteps
            if (recoveryProgressFrames == attackFrames) {
                gain = 1f
                state = GateState.OPEN
            }
        } else {
            advanceAttack()
        }
    }

    private enum class GateState {
        CLOSED,
        ATTACKING,
        RELEASING,
        OPEN,
    }

    private companion object {
        const val OPEN_THRESHOLD = 0.45f
        const val CLOSE_THRESHOLD = 0.25f
        const val ATTACK_FRAMES = 2
        const val HOLD_FRAMES = 20
        const val RELEASE_FRAMES = 30
        const val MINIMUM_GAIN = 0.15f
    }
}
