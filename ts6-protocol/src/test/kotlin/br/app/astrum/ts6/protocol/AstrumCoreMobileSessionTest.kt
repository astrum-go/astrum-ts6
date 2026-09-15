package br.app.astrum.ts6.protocol

import kotlinx.coroutines.test.runTest
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
        assertEquals(
            AstrumCoreMobileSession.CloseResult.CLOSED,
            session.close("test"),
        )
        assertEquals(AstrumCoreMobileSession.CloseResult.ALREADY_CLOSED, session.close("again"))
        assertEquals(listOf("connect", "next:0", "close:test", "close:again"), delegate.calls)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeEventTimeout() = runTest {
        AstrumCoreMobileSession.forTesting(FakeDelegate()).nextEvent(-1)
    }

    private class FakeDelegate : AstrumCoreMobileSession.Delegate {
        private var currentState = AstrumCoreMobileSession.State.NEW
        private var closed = false
        val calls = mutableListOf<String>()

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

        override suspend fun close(reason: String): AstrumCoreMobileSession.CloseResult {
            calls += "close:$reason"
            if (closed) return AstrumCoreMobileSession.CloseResult.ALREADY_CLOSED
            closed = true
            currentState = AstrumCoreMobileSession.State.CLOSED
            return AstrumCoreMobileSession.CloseResult.CLOSED
        }
    }
}
