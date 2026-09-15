package br.app.astrum.ts6.app.service

import br.app.astrum.ts6.protocol.ServerConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionClientSelectionTest {
    @Test
    fun defaultRuntimeUsesTs3jWithoutLoadingJni() {
        assertEquals(
            SessionClientKind.TS3J,
            selectSessionClientKind(false, ServerConfig("host", nickname = "Tester")),
        )
    }

    @Test
    fun optInUsesAstrumCoreForPasswordlessServer() {
        assertEquals(
            SessionClientKind.ASTRUM_CORE,
            selectSessionClientKind(true, ServerConfig("host", nickname = "Tester")),
        )
    }

    @Test
    fun optInFallsBackToTs3jForPasswordProtectedServer() {
        assertEquals(
            SessionClientKind.TS3J,
            selectSessionClientKind(
                true,
                ServerConfig("host", nickname = "Tester", password = "secret"),
            ),
        )
    }

    @Test
    fun whitespaceOnlyPasswordIsBlank() {
        assertEquals(
            SessionClientKind.ASTRUM_CORE,
            selectSessionClientKind(
                true,
                ServerConfig("host", nickname = "Tester", password = "   "),
            ),
        )
    }
}
