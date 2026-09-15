package br.app.astrum.ts6.protocol

/**
 * Small protocol-facing facade for the opt-in UniFFI MobileSession.
 *
 * The facade intentionally contains only the API currently exported by
 * astrum-core: configuration, lifecycle state, connect, event polling, and
 * close. Voice, channel, and stream operations remain on the existing JNI
 * boundary until the Rust mobile API exposes them.
 */
class AstrumCoreMobileSession internal constructor(
    private val delegate: Delegate,
) {
    data class Config(
        val host: String,
        val port: Int = 9987,
        val nickname: String,
    )

    enum class State {
        NEW,
        CONNECTING,
        CONNECTED,
        CLOSED,
    }

    enum class CloseResult {
        CLOSED,
        ALREADY_CLOSED,
    }

    internal interface Delegate {
        fun config(): Config
        fun state(): State
        suspend fun connect()
        suspend fun nextEvent(timeoutMs: Long): String?
        suspend fun close(reason: String): CloseResult
    }

    fun config(): Config = delegate.config()

    fun state(): State = delegate.state()

    suspend fun connect() = delegate.connect()

    suspend fun nextEvent(timeoutMs: Long): String? {
        require(timeoutMs >= 0) { "timeoutMs must not be negative" }
        return delegate.nextEvent(timeoutMs)
    }

    suspend fun close(reason: String): CloseResult = delegate.close(reason)

    companion object {
        /** Test seam that does not construct or load the generated JNI binding. */
        internal fun forTesting(delegate: Delegate): AstrumCoreMobileSession =
            AstrumCoreMobileSession(delegate)
    }
}
