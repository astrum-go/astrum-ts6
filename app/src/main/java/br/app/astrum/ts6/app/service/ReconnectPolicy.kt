package br.app.astrum.ts6.app.service

internal class ReconnectPolicy(
    private val delaysMs: List<Long> = DEFAULT_DELAYS_MS,
) {
    init {
        require(delaysMs.isNotEmpty())
        require(delaysMs.all { it >= 0L })
    }

    fun delayForAttempt(attempt: Int): Long {
        require(attempt > 0)
        return delaysMs[(attempt - 1).coerceAtMost(delaysMs.lastIndex)]
    }

    private companion object {
        val DEFAULT_DELAYS_MS = listOf(1_000L, 2_000L, 4_000L, 8_000L, 15_000L, 30_000L)
    }
}
