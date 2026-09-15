package br.app.astrum.ts6.protocol

import uniffi.astrum_core.MobileCloseResult
import uniffi.astrum_core.MobileSession
import uniffi.astrum_core.MobileSessionConfig
import uniffi.astrum_core.MobileSessionState

/** Factory and adapter for the generated UniFFI MobileSession. */
object AstrumCoreUniFfiMobileSession {
    fun create(config: AstrumCoreMobileSession.Config): AstrumCoreMobileSession =
        AstrumCoreMobileSession.forTesting(GeneratedDelegate(config))

    private class GeneratedDelegate(config: AstrumCoreMobileSession.Config) : AstrumCoreMobileSession.Delegate {
        private val session = MobileSession(
            MobileSessionConfig(
                host = config.host,
                port = config.port.toUShort(),
                nickname = config.nickname,
                identity = null,
            ),
        )

        override fun config(): AstrumCoreMobileSession.Config = session.config().let {
            AstrumCoreMobileSession.Config(it.host, it.port.toInt(), it.nickname)
        }

        override fun state(): AstrumCoreMobileSession.State = when (session.state()) {
            MobileSessionState.NEW -> AstrumCoreMobileSession.State.NEW
            MobileSessionState.CONNECTING -> AstrumCoreMobileSession.State.CONNECTING
            MobileSessionState.CONNECTED -> AstrumCoreMobileSession.State.CONNECTED
            MobileSessionState.CLOSED -> AstrumCoreMobileSession.State.CLOSED
        }

        override suspend fun connect() {
            session.connect()
        }

        override suspend fun nextEvent(timeoutMs: Long): String? = session.nextEvent(timeoutMs.toULong())

        override suspend fun sendVoiceFrame(codec: Int, data: ByteArray) {
            session.sendVoiceFrame(codec.toUByte(), data)
        }

        override suspend fun close(reason: String): AstrumCoreMobileSession.CloseResult = when (session.close(reason)) {
            MobileCloseResult.CLOSED -> AstrumCoreMobileSession.CloseResult.CLOSED
            MobileCloseResult.ALREADY_CLOSED -> AstrumCoreMobileSession.CloseResult.ALREADY_CLOSED
        }
    }
}
