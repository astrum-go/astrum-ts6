package io.github.ts3mobile.protocol

import com.github.manevolent.ts3j.command.SingleCommand
import com.github.manevolent.ts3j.command.parameter.CommandSingleParameter
import com.github.manevolent.ts3j.protocol.ProtocolRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Ts6StreamProtocolTest {

    @Test
    fun streamTypeParsing() {
        assertEquals(StreamType.CAMERA, StreamType.fromValue("1"))
        assertEquals(StreamType.WINDOW, StreamType.fromValue("3"))
        assertEquals(StreamType.SCREEN, StreamType.fromValue("2"))
        assertEquals(StreamType.CAMERA, StreamType.fromValue("cameras"))
        assertEquals(StreamType.CAMERA, StreamType.fromValue("camera"))
        assertEquals(StreamType.SCREEN, StreamType.fromValue("screens"))
        assertEquals(StreamType.SCREEN, StreamType.fromValue("screen"))
        assertEquals(StreamType.SCREEN, StreamType.fromValue(null, "Tela 0"))
        assertEquals(StreamType.SCREEN, StreamType.fromValue(null, "Screen 1"))
        assertEquals(StreamType.WINDOW, StreamType.fromValue(null, "DevTools - tsui://default/index.html#/dashboard"))
        assertEquals(StreamType.WINDOW, StreamType.fromValue("window"))
        assertEquals(StreamType.CAMERA, StreamType.fromValue(null, "Integrated Camera"))
        assertEquals(StreamType.CAMERA, StreamType.fromValue("unknown"))
        assertEquals(StreamType.CAMERA, StreamType.fromValue(null))
        assertEquals(1, StreamType.CAMERA.value)
        assertEquals(2, StreamType.SCREEN.value)
        assertEquals(3, StreamType.WINDOW.value)
    }

    @Test
    fun snapshotStoreTracksActiveStreams() {
        val store = SessionSnapshotStore()
        val participant = Ts3Participant(id = 10, channelId = 1, nickname = "Bob", isTalking = false, isInputMuted = false, isOutputMuted = false)
        store.putParticipant(participant)

        var snapshot = store.snapshot()
        val pBefore = snapshot.participants.first { it.id == 10 }
        assertFalse(pBefore.hasActiveCamera)
        assertFalse(pBefore.hasActiveScreen)
        assertTrue(snapshot.activeStreams.isEmpty())

        val cameraStream = Ts6StreamInfo(
            streamId = "stream-cam-1",
            clientId = 10,
            type = StreamType.CAMERA,
            width = 1280,
            height = 720,
            fps = 30,
        )
        store.putStream(cameraStream)

        snapshot = store.snapshot()
        val pWithCam = snapshot.participants.first { it.id == 10 }
        assertTrue(pWithCam.hasActiveCamera)
        assertFalse(pWithCam.hasActiveScreen)
        assertEquals(1, snapshot.activeStreams.size)
        assertEquals("stream-cam-1", snapshot.activeStreams.first().streamId)

        val screenStream = Ts6StreamInfo(
            streamId = "stream-scr-1",
            clientId = 10,
            type = StreamType.SCREEN,
            width = 1920,
            height = 1080,
            fps = 60,
        )
        store.putStream(screenStream)

        snapshot = store.snapshot()
        val pWithBoth = snapshot.participants.first { it.id == 10 }
        assertTrue(pWithBoth.hasActiveCamera)
        assertTrue(pWithBoth.hasActiveScreen)
        assertEquals(2, snapshot.activeStreams.size)

        // Test removing one stream
        store.removeStream("stream-cam-1")
        snapshot = store.snapshot()
        val pWithScreenOnly = snapshot.participants.first { it.id == 10 }
        assertFalse(pWithScreenOnly.hasActiveCamera)
        assertTrue(pWithScreenOnly.hasActiveScreen)
        assertEquals(1, snapshot.activeStreams.size)

        // Test removing client removes their streams
        store.removeParticipant(10)
        snapshot = store.snapshot()
        assertTrue(snapshot.participants.isEmpty())
        assertTrue(snapshot.activeStreams.isEmpty())
    }

    @Test
    fun ts6CommandGeneration() {
        val streamSignalingCmd = SingleCommand(
            "streamsignaling",
            ProtocolRole.CLIENT,
            listOf(
                CommandSingleParameter("clid", "42"),
                CommandSingleParameter("streamid", "abc-123"),
                CommandSingleParameter("msg", "{\"type\":\"offer\"}"),
            ),
        )
        val built = streamSignalingCmd.build()
        assertTrue(built.startsWith("streamsignaling"))
        assertTrue(built.contains("clid=42"))
        assertTrue(built.contains("streamid=abc-123"))

        val joinCmd = SingleCommand(
            "joinstreamrequest",
            ProtocolRole.CLIENT,
            listOf(
                CommandSingleParameter("clid", "99"),
                CommandSingleParameter("streamid", "test-uuid"),
            ),
        )
        val builtJoin = joinCmd.build()
        assertTrue(builtJoin.startsWith("joinstreamrequest"))
        assertTrue(builtJoin.contains("clid=99"))
        assertTrue(builtJoin.contains("streamid=test-uuid"))

        val sdpWithCrLf = "v=0\r\no=- 123 2 IN IP4 127.0.0.1\r\ns=-\r\nt=0 0\r\n"
        val cmdWithTs3Param = SingleCommand(
            "respondjoinstreamrequest",
            ProtocolRole.CLIENT,
            listOf(Ts3jSessionClient.Ts3Parameter("offer", sdpWithCrLf)),
        )
        val builtCommand = cmdWithTs3Param.build()
        assertTrue("SDP offer parameter must preserve escaped \\r\\n at the end", builtCommand.endsWith("\\r\\n"))

        val parsed = SingleCommand.parse(ProtocolRole.SERVER, builtCommand)
        val parsedOffer = parsed["offer"].value
        assertEquals("Parsed SDP offer must match original with trailing CRLF", sdpWithCrLf, parsedOffer)
        assertTrue(parsedOffer.endsWith("\r\n"))
    }
}
