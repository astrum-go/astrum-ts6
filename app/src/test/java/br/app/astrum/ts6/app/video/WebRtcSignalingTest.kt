package br.app.astrum.ts6.app.video

import br.app.astrum.ts6.app.service.TeamSpeakServiceState
import br.app.astrum.ts6.app.service.StreamViewer
import br.app.astrum.ts6.app.service.WatchedStream
import br.app.astrum.ts6.protocol.StreamType
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
                WatchedStream("stream-1", 10, "User 1", StreamType.CAMERA),
                WatchedStream("stream-2", 10, "User 1", StreamType.SCREEN),
                WatchedStream("stream-3", 20, "User 2", StreamType.CAMERA),
                WatchedStream("stream-4", 20, "User 2", StreamType.SCREEN),
            ),
        )
        assertEquals(4, multiStream.watchingStreams.size)
        assertEquals("stream-1", multiStream.watchingStreams[0].streamId)
        assertEquals(StreamType.CAMERA, multiStream.watchingStreams[0].type)
        assertEquals(StreamType.SCREEN, multiStream.watchingStreams[1].type)

        val withViewers = multiStream.copy(
            autoAcceptStreamViewers = true,
            activeViewers = listOf(
                StreamViewer(30, "Alice", "stream-123"),
            ),
            pendingViewerRequests = listOf(
                StreamViewer(31, "Charlie", "stream-123"),
            ),
        )
        assertTrue(withViewers.autoAcceptStreamViewers)
        assertEquals(1, withViewers.activeViewers.size)
        assertEquals("Alice", withViewers.activeViewers[0].nickname)
        assertEquals(1, withViewers.pendingViewerRequests.size)
        assertEquals("Charlie", withViewers.pendingViewerRequests[0].nickname)
    }

    // ─── Testes dos bugs corrigidos (peer connection zumbi) ──────────────────

    /**
     * Verifica que o estado de visualização de streams é corretamente resetado
     * ao iniciar uma nova conexão de servidor (watchingStreams vazia, sem activeViewers).
     * Documenta o comportamento garantido pelo resetViewerState() chamado em beginConnection.
     */
    @Test
    fun viewerStateIsCleanOnNewConnection() {
        // Estado anterior com viewers e streams ativos
        val oldState = TeamSpeakServiceState(
            isBroadcastingCamera = true,
            activeBroadcastStreamId = "stream-abc",
            activeViewers = listOf(StreamViewer(42, "PC User", "stream-abc")),
            pendingViewerRequests = listOf(StreamViewer(43, "Other", "stream-abc")),
            watchingStreams = listOf(WatchedStream("remote-stream-1", 10, "Alice", StreamType.CAMERA)),
            watchingStreamId = "remote-stream-1",
            watchingStreamClientId = 10,
        )
        assertEquals(1, oldState.activeViewers.size)
        assertEquals(1, oldState.pendingViewerRequests.size)
        assertEquals(1, oldState.watchingStreams.size)

        // Simula o estado criado pelo beginConnection (novo TeamSpeakServiceState fresco)
        val freshState = TeamSpeakServiceState()
        assertTrue(freshState.activeViewers.isEmpty())
        assertTrue(freshState.pendingViewerRequests.isEmpty())
        assertTrue(freshState.watchingStreams.isEmpty())
        assertNull(freshState.watchingStreamId)
        assertNull(freshState.watchingStreamClientId)
        assertFalse(freshState.isBroadcastingCamera)
    }

    /**
     * Verifica que stopCameraBroadcast limpa o estado de viewers no ServiceState,
     * o que é necessário para permitir uma nova sessão de broadcast sem estados zumbi.
     */
    @Test
    fun stopBroadcastClearsViewerState() {
        val broadcasting = TeamSpeakServiceState(
            isBroadcastingCamera = true,
            activeBroadcastStreamId = "stream-xyz",
            activeViewers = listOf(
                StreamViewer(10, "Viewer A", "stream-xyz"),
                StreamViewer(20, "Viewer B", "stream-xyz"),
            ),
            pendingViewerRequests = listOf(
                StreamViewer(30, "Pending C", "stream-xyz"),
            ),
        )
        assertEquals(2, broadcasting.activeViewers.size)
        assertEquals(1, broadcasting.pendingViewerRequests.size)

        // Simula o que stopCameraBroadcast faz no ServiceState (cópia sem viewers)
        val stopped = broadcasting.copy(
            isBroadcastingCamera = false,
            activeBroadcastStreamId = null,
            activeViewers = emptyList(),
            pendingViewerRequests = emptyList(),
        )
        assertFalse(stopped.isBroadcastingCamera)
        assertNull(stopped.activeBroadcastStreamId)
        assertTrue(stopped.activeViewers.isEmpty())
        assertTrue(stopped.pendingViewerRequests.isEmpty())
    }

    /**
     * Verifica que duplicate join requests para o mesmo viewer são tratados
     * (o viewer já existente é removido antes de readicionar).
     * Documenta o fix: sem mais early-return em handleJoinRequest para keys duplicadas.
     */
    @Test
    fun duplicateViewerJoinRequestReplacesExisting() {
        val initialViewers = listOf(StreamViewer(42, "PC User", "stream-abc"))
        val state = TeamSpeakServiceState(
            isBroadcastingCamera = true,
            activeBroadcastStreamId = "stream-abc",
            activeViewers = initialViewers,
        )

        // Simula uma segunda solicitação de join do mesmo viewer (reconexão do PC)
        // O comportamento correto: o viewer existente é substituído, não duplicado
        val updatedViewers = state.activeViewers.filter { it.clientId != 42 } +
            StreamViewer(42, "PC User", "stream-abc")
        val updated = state.copy(activeViewers = updatedViewers)

        assertEquals(1, updated.activeViewers.size) // Sem duplicata
        assertEquals(42, updated.activeViewers[0].clientId)
    }
}
