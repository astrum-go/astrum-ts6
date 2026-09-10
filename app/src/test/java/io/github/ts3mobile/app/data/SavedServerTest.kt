package io.github.ts3mobile.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedServerTest {

    @Test
    fun testDisplayNameDefaultsToHostWhenNameIsBlank() {
        val server = SavedServer(
            name = "",
            host = "voice.ts3.com",
            port = 9987,
            nickname = "PlayerOne",
        )
        assertEquals("voice.ts3.com", server.displayName)
    }

    @Test
    fun testDisplayNameUsesCustomNameWhenPresent() {
        val server = SavedServer(
            name = "Servidor da Guilda",
            host = "voice.ts3.com",
            port = 9987,
            nickname = "PlayerOne",
        )
        assertEquals("Servidor da Guilda", server.displayName)
    }

    @Test
    fun testHostPortDisplay() {
        val defaultPort = SavedServer(host = "voice.ts3.com", port = 9987)
        assertEquals("voice.ts3.com", defaultPort.hostPortDisplay)

        val customPort = SavedServer(host = "voice.ts3.com", port = 10011)
        assertEquals("voice.ts3.com:10011", customPort.hostPortDisplay)
    }

    @Test
    fun testValidation() {
        val emptyHost = SavedServer(host = "")
        assertNotNull(emptyHost.validationError())

        val invalidPort = SavedServer(host = "1.2.3.4", port = 0)
        assertNotNull(invalidPort.validationError())

        val shortNick = SavedServer(host = "1.2.3.4", nickname = "ab")
        assertNotNull(shortNick.validationError())

        val valid = SavedServer(host = "1.2.3.4", port = 9987, nickname = "ValidNick")
        assertNull(valid.validationError())
    }

    @Test
    fun testSerializationAndDeserialization() {
        val originalList = listOf(
            SavedServer(
                id = "id-1",
                name = "Servidor 1",
                host = "ts1.example.com",
                port = 9987,
                nickname = "User1",
                password = "secretPassword",
                lastConnectedAt = 123456789L,
            ),
            SavedServer(
                id = "id-2",
                name = "Servidor 2",
                host = "ts2.example.com",
                port = 9988,
                nickname = "User2",
                password = "",
                lastConnectedAt = 987654321L,
            ),
        )

        val json = SavedServerSerializer.serialize(originalList)
        assertTrue(json.contains("ts1.example.com"))
        assertTrue(json.contains("ts2.example.com"))

        val restored = SavedServerSerializer.deserialize(json)
        assertEquals(2, restored.size)

        assertEquals("id-1", restored[0].id)
        assertEquals("Servidor 1", restored[0].name)
        assertEquals("ts1.example.com", restored[0].host)
        assertEquals(9987, restored[0].port)
        assertEquals("User1", restored[0].nickname)
        assertEquals("secretPassword", restored[0].password)
        assertEquals(123456789L, restored[0].lastConnectedAt)

        assertEquals("id-2", restored[1].id)
        assertEquals("Servidor 2", restored[1].name)
        assertEquals("ts2.example.com", restored[1].host)
        assertEquals(9988, restored[1].port)
        assertEquals("User2", restored[1].nickname)
        assertEquals("", restored[1].password)
        assertEquals(987654321L, restored[1].lastConnectedAt)
    }

    @Test
    fun testDeserializeEmptyOrCorruptStringReturnsEmptyList() {
        assertTrue(SavedServerSerializer.deserialize(null).isEmpty())
        assertTrue(SavedServerSerializer.deserialize("").isEmpty())
        assertTrue(SavedServerSerializer.deserialize("invalid json string").isEmpty())
    }
}
