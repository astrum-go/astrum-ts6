package io.github.ts3mobile.app.video

import io.github.ts3mobile.app.service.TeamSpeakServiceState
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebRtcSignalingTest {

    @Test
    fun offerSignalingJsonFormat() {
        val sdpString = "v=0\r\no=- 12345 2 IN IP4 127.0.0.1\r\ns=-\r\nt=0 0\r\nm=video 9 UDP/TLS/RTP/SAVPF 96\r\n"
        val json = JSONObject().apply {
            put("type", "offer")
            put("sdp", sdpString)
        }

        val parsed = JSONObject(json.toString())
        assertEquals("offer", parsed.getString("type"))
        assertEquals(sdpString, parsed.getString("sdp"))
    }

    @Test
    fun answerSignalingJsonFormat() {
        val sdpString = "v=0\r\no=- 54321 2 IN IP4 127.0.0.1\r\ns=-\r\nt=0 0\r\nm=video 9 UDP/TLS/RTP/SAVPF 96\r\n"
        val json = JSONObject().apply {
            put("type", "answer")
            put("sdp", sdpString)
        }

        val parsed = JSONObject(json.toString())
        assertEquals("answer", parsed.getString("type"))
        assertEquals(sdpString, parsed.getString("sdp"))
    }

    @Test
    fun candidateSignalingJsonFormat() {
        val candidateSdp = "candidate:1 1 UDP 2130706431 192.168.1.50 50000 typ host"
        val json = JSONObject().apply {
            put("type", "candidate")
            put("candidate", candidateSdp)
            put("sdpMid", "video")
            put("sdpMLineIndex", 0)
        }

        val parsed = JSONObject(json.toString())
        assertEquals("candidate", parsed.getString("type"))
        assertEquals(candidateSdp, parsed.getString("candidate"))
        assertEquals("video", parsed.getString("sdpMid"))
        assertEquals(0, parsed.getInt("sdpMLineIndex"))
    }

    @Test
    fun teamSpeakServiceStateTracksVideo() {
        val initial = TeamSpeakServiceState()
        assertFalse(initial.isBroadcastingCamera)
        assertTrue(initial.isFrontCamera)
        assertNull(initial.activeBroadcastStreamId)
        assertNull(initial.watchingStreamId)
        assertNull(initial.watchingStreamClientId)

        val broadcasting = initial.copy(
            isBroadcastingCamera = true,
            activeBroadcastStreamId = "stream-123",
            isFrontCamera = false,
        )
        assertTrue(broadcasting.isBroadcastingCamera)
        assertFalse(broadcasting.isFrontCamera)
        assertEquals("stream-123", broadcasting.activeBroadcastStreamId)

        val watching = broadcasting.copy(
            watchingStreamId = "stream-remote-456",
            watchingStreamClientId = 99,
        )
        assertEquals("stream-remote-456", watching.watchingStreamId)
        assertEquals(99, watching.watchingStreamClientId)
    }
}
