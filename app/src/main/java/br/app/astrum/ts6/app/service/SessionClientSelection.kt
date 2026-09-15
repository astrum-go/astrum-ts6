package br.app.astrum.ts6.app.service

import br.app.astrum.ts6.protocol.ServerConfig

/** The implementation selected for a server connection. */
internal enum class SessionClientKind {
    ASTRUM_CORE,
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
