package br.app.astrum.ts6.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerConfigTest {
    @Test
    fun validConfigNormalizesWhitespace() {
        val config = ServerConfig("  voice.example.com ", 9987, " Monet ").normalized()

        assertEquals("voice.example.com", config.host)
        assertEquals("Monet", config.nickname)
        assertNull(config.validationError())
    }

    @Test
    fun rejectsInvalidPortAndShortNickname() {
        assertEquals(
            "Port must be between 1 and 65535",
            ServerConfig("host", 0, "valid").validationError(),
        )
        assertEquals(
            "Nickname must contain 3 to 30 characters",
            ServerConfig("host", 9987, "x").validationError(),
        )
    }
}

