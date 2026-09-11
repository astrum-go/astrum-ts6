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
    fun ts6OfferJsonFormat() {
        val sdpString = "v=0\r\no=- 12345 2 IN IP4 127.0.0.1\r\ns=-\r\nt=0 0\r\nm=video 9 UDP/TLS/RTP/SAVPF 96\r\n"
        val json = JSONObject().apply {
            put("cmd", "offer")
            put("args", JSONObject().apply {
                put("offer", sdpString)
            })
        }

        val parsed = JSONObject(json.toString())
        assertEquals("offer", parsed.getString("cmd"))
        val args = parsed.getJSONObject("args")
        assertEquals(sdpString, args.getString("offer"))
        assertFalse(args.has("sdp"))
    }

    @Test
    fun ts6AnswerJsonFormat() {
        val sdpString = "v=0\r\no=- 54321 2 IN IP4 127.0.0.1\r\ns=-\r\nt=0 0\r\nm=video 9 UDP/TLS/RTP/SAVPF 96\r\n"
        val json = JSONObject().apply {
            put("cmd", "answer")
            put("args", JSONObject().apply {
                put("answer", sdpString)
            })
        }

        val parsed = JSONObject(json.toString())
        assertEquals("answer", parsed.getString("cmd"))
        val args = parsed.getJSONObject("args")
        assertEquals(sdpString, args.getString("answer"))
        assertFalse(args.has("sdp"))
    }

    @Test
    fun ts6IceCandidateJsonFormat() {
        val candidateSdp = "candidate:3431023245 1 udp 2122260223 10.13.14.2 60321 typ host"
        val json = JSONObject().apply {
            put("cmd", "iceCandidate")
            put("args", JSONObject().apply {
                put("mLine", 0)
                put("mid", "0")
                put("sdp", candidateSdp)
            })
            put("type", "candidate")
            put("candidate", candidateSdp)
            put("sdpMid", "0")
            put("sdpMLineIndex", 0)
        }

        val parsed = JSONObject(json.toString())
        assertEquals("iceCandidate", parsed.getString("cmd"))
        val args = parsed.getJSONObject("args")
        assertEquals(0, args.getInt("mLine"))
        assertEquals("0", args.getString("mid"))
        assertEquals(candidateSdp, args.getString("sdp"))
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

        val multiStream = watching.copy(
            watchingStreams = listOf(
                io.github.ts3mobile.app.service.WatchedStream("stream-1", 10, "User 1", io.github.ts3mobile.protocol.StreamType.CAMERA),
                io.github.ts3mobile.app.service.WatchedStream("stream-2", 10, "User 1", io.github.ts3mobile.protocol.StreamType.SCREEN),
                io.github.ts3mobile.app.service.WatchedStream("stream-3", 20, "User 2", io.github.ts3mobile.protocol.StreamType.CAMERA),
                io.github.ts3mobile.app.service.WatchedStream("stream-4", 20, "User 2", io.github.ts3mobile.protocol.StreamType.SCREEN),
            ),
        )
        assertEquals(4, multiStream.watchingStreams.size)
        assertEquals("stream-1", multiStream.watchingStreams[0].streamId)
        assertEquals(io.github.ts3mobile.protocol.StreamType.CAMERA, multiStream.watchingStreams[0].type)
        assertEquals(io.github.ts3mobile.protocol.StreamType.SCREEN, multiStream.watchingStreams[1].type)

        val withViewers = multiStream.copy(
            autoAcceptStreamViewers = true,
            activeViewers = listOf(
                io.github.ts3mobile.app.service.StreamViewer(30, "Alice", "stream-123"),
            ),
            pendingViewerRequests = listOf(
                io.github.ts3mobile.app.service.StreamViewer(31, "Charlie", "stream-123"),
            ),
        )
        assertTrue(withViewers.autoAcceptStreamViewers)
        assertEquals(1, withViewers.activeViewers.size)
        assertEquals("Alice", withViewers.activeViewers[0].nickname)
        assertEquals(1, withViewers.pendingViewerRequests.size)
        assertEquals("Charlie", withViewers.pendingViewerRequests[0].nickname)
    }
}
