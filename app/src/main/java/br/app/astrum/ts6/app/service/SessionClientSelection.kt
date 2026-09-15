package br.app.astrum.ts6.app.service

import br.app.astrum.ts6.protocol.ServerConfig
import br.app.astrum.ts6.protocol.Ts3SessionClient

/** The implementation selected for a server connection. */
internal enum class SessionClientKind {
    ASTRUM_CORE,
    UNIFFI,
    TS3J,
}

/**
 * Keeps runtime selection independent from client construction so it can be
 * tested without loading libastrum_core.so.
 */
internal fun selectSessionClientKind(
    astrumCoreRuntime: Boolean,
    config: ServerConfig,
): SessionClientKind = if (astrumCoreRuntime && config.password.isBlank()) {
    SessionClientKind.ASTRUM_CORE
} else {
    SessionClientKind.TS3J
}

/**
 * Selects the explicit backend without making the optional UniFFI classes a
 * compile-time dependency of the default APK. Passwords always remain on the
 * ts3j path because MobileSession has no password field.
 */
internal fun selectSessionClientKind(
    astrumCoreBackend: String,
    config: ServerConfig,
): SessionClientKind = when (astrumCoreBackend.trim().lowercase()) {
    "uniffi" -> if (config.password.isBlank()) SessionClientKind.UNIFFI else SessionClientKind.TS3J
    "jni" -> if (config.password.isBlank()) SessionClientKind.ASTRUM_CORE else SessionClientKind.TS3J
    "ts3j", "" -> SessionClientKind.TS3J
    else -> throw IllegalArgumentException("Unsupported astrumCoreBackend: $astrumCoreBackend")
}

/** Constructs the optional backend only when its opt-in source set is present. */
internal fun createSessionClient(kind: SessionClientKind): Ts3SessionClient = when (kind) {
    SessionClientKind.ASTRUM_CORE -> br.app.astrum.ts6.protocol.AstrumCoreSessionClient()
    SessionClientKind.TS3J -> br.app.astrum.ts6.protocol.Ts3jSessionClient()
    SessionClientKind.UNIFFI -> try {
        Class.forName("br.app.astrum.ts6.protocol.AstrumCoreUniFfiSessionClient")
            .getDeclaredConstructor()
            .newInstance() as Ts3SessionClient
    } catch (error: Throwable) {
        throw IllegalStateException(
            "UniFFI backend is not packaged; build with -PastrumCoreBackend=uniffi",
            error,
        )
    }
}
