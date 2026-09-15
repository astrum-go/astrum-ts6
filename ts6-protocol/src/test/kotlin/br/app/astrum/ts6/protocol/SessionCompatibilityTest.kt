package br.app.astrum.ts6.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionCompatibilityTest {
    private class LegacyListener : Ts3SessionListener {
        var joined: Pair<String, Int>? = null

        override fun onStatusChanged(status: ConnectionStatus) = Unit
        override fun onSnapshotChanged(snapshot: SessionSnapshot) = Unit
        override fun onVoiceFrame(frame: VoiceFrame) = Unit

        override fun onStreamClientJoined(streamId: String, clientId: Int) {
            joined = streamId to clientId
        }
    }

    @Test
    fun threeArgumentStreamCallbackAdaptsToLegacyListener() {
        val listener = LegacyListener()

        listener.onStreamClientJoined("stream-1", 42, "offer")

        assertEquals("stream-1" to 42, listener.joined)
    }
}
