package br.app.astrum.ts6.protocol

/**
 * Small protocol-facing facade for the opt-in UniFFI MobileSession.
 *
 * The facade intentionally contains only the API currently exported by
 * astrum-core: configuration, lifecycle state, connect, event polling, close,
 * and opt-in voice-frame sending. Channel and stream operations remain on the
 * existing JNI boundary.
 */
class AstrumCoreMobileSession internal constructor(
    private val delegate: Delegate,
) {
    data class Config(
        val host: String,
        val port: Int = 9987,
        val nickname: String,
        /** Optional TS3 identity material. Passwords are intentionally not part of this API. */
        val identity: String? = null,
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
        suspend fun sendVoiceFrame(codec: Int, data: ByteArray)
        suspend fun close(reason: String): CloseResult
    }

    fun config(): Config = delegate.config()

    fun state(): State = delegate.state()

    suspend fun connect() = delegate.connect()

    suspend fun nextEvent(timeoutMs: Long): String? {
        require(timeoutMs >= 0) { "timeoutMs must not be negative" }
        return delegate.nextEvent(timeoutMs)
    }

    /** Sends one Opus voice frame through the opt-in Astrum Core session. */
    suspend fun sendVoiceFrame(codec: Int, data: ByteArray) {
        require(codec == 4 || codec == 5) {
            "codec must be Opus Voice (4) or Opus Music (5)"
        }
        require(data.isNotEmpty()) { "voice frame data must not be empty" }
        check(state() == State.CONNECTED) { "session must be CONNECTED to send voice" }
        delegate.sendVoiceFrame(codec, data)
    }

    suspend fun close(reason: String): CloseResult = delegate.close(reason)

    companion object {
        /** Test seam that does not construct or load the generated UniFFI binding. */
        internal fun forTesting(delegate: Delegate): AstrumCoreMobileSession =
            AstrumCoreMobileSession(delegate)
    }
}
