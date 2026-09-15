package br.app.astrum.ts6.protocol

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AstrumCoreMobileSessionTest {
    @Test
    fun delegatesLifecycleWithoutLoadingNativeLibrary() = runTest {
        val delegate = FakeDelegate()
        val session = AstrumCoreMobileSession.forTesting(delegate)

        assertEquals(AstrumCoreMobileSession.Config("127.0.0.1", 9987, "test"), session.config())
        assertEquals(AstrumCoreMobileSession.State.NEW, session.state())
        session.connect()
        assertEquals(AstrumCoreMobileSession.State.CONNECTED, session.state())
        assertEquals("{\"type\":\"Ready\"}", session.nextEvent(0))
        session.sendVoiceFrame(4, byteArrayOf(1, 2, 3))
        assertEquals(
            AstrumCoreMobileSession.CloseResult.CLOSED,
            session.close("test"),
        )
        assertEquals(AstrumCoreMobileSession.CloseResult.ALREADY_CLOSED, session.close("again"))
        assertEquals(
            listOf("connect", "next:0", "voice:4:3", "close:test", "close:again"),
            delegate.calls,
        )
        assertEquals(4, delegate.sentCodec)
        assertArrayEquals(byteArrayOf(1, 2, 3), delegate.sentData)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeEventTimeout() = runTest {
        AstrumCoreMobileSession.forTesting(FakeDelegate()).nextEvent(-1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedVoiceCodec() = runTest {
        val session = AstrumCoreMobileSession.forTesting(
            FakeDelegate().also { it.currentState = AstrumCoreMobileSession.State.CONNECTED },
        )
        session.sendVoiceFrame(6, byteArrayOf(1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptyVoicePayload() = runTest {
        val session = AstrumCoreMobileSession.forTesting(
            FakeDelegate().also { it.currentState = AstrumCoreMobileSession.State.CONNECTED },
        )
        session.sendVoiceFrame(4, byteArrayOf())
    }

    @Test(expected = IllegalStateException::class)
    fun rejectsVoiceFrameWhenNotConnected() = runTest {
        AstrumCoreMobileSession.forTesting(FakeDelegate()).sendVoiceFrame(4, byteArrayOf(1))
    }

    private class FakeDelegate : AstrumCoreMobileSession.Delegate {
        var currentState = AstrumCoreMobileSession.State.NEW
        private var closed = false
        val calls = mutableListOf<String>()
        var sentCodec: Int? = null
        var sentData: ByteArray? = null

        override fun config() = AstrumCoreMobileSession.Config("127.0.0.1", nickname = "test")

        override fun state() = currentState

        override suspend fun connect() {
            calls += "connect"
            currentState = AstrumCoreMobileSession.State.CONNECTED
        }

        override suspend fun nextEvent(timeoutMs: Long): String? {
            calls += "next:$timeoutMs"
            return "{\"type\":\"Ready\"}"
        }

        override suspend fun sendVoiceFrame(codec: Int, data: ByteArray) {
            sentCodec = codec
            sentData = data
            calls += "voice:$codec:${data.size}"
        }

        override suspend fun close(reason: String): AstrumCoreMobileSession.CloseResult {
            calls += "close:$reason"
            if (closed) return AstrumCoreMobileSession.CloseResult.ALREADY_CLOSED
            closed = true
            currentState = AstrumCoreMobileSession.State.CLOSED
            return AstrumCoreMobileSession.CloseResult.CLOSED
        }
    }
}
