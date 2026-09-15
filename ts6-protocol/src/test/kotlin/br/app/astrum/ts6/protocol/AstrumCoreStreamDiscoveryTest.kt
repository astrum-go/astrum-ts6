package br.app.astrum.ts6.protocol

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AstrumCoreStreamDiscoveryTest {
    @Test
    fun streamInfoAliasesPreserveServerIdAndNormalizeMetadata() {
        val info = JSONObject(
            """
            {
              "streamid":"server-stream-7",
              "clid":"42",
              "streamtype":"2",
              "stream_name":"Desktop",
              "stream_width":"1920",
              "stream_height":1080,
              "framerate":60,
              "bitrate_kbps":4000
            }
            """,
        ).toTs6StreamInfo()

        requireNotNull(info)
        assertEquals("server-stream-7", info.streamId)
        assertEquals(42, info.clientId)
        assertEquals(StreamType.SCREEN, info.type)
        assertEquals(1920, info.width)
        assertEquals(1080, info.height)
        assertEquals(60, info.fps)
        assertEquals(4000, info.bitrate)
        assertEquals("Desktop", info.description)
    }

    @Test
    fun streamInfoWithoutServerIdIsIgnoredWithoutFallback() {
        assertNull(
            JSONObject("{\"client_id\":42,\"stream_type\":2}").toTs6StreamInfo(),
        )
    }

    @Test
    fun nativeStreamIdCompletesTheExactPendingStart() {
        val pending = PendingStreamStart(7L, StreamType.SCREEN)
        pending.nativeStreamId = "native-server-id"

        assertTrue(completePendingStreamStartById(pending, "native-server-id", 7L))
        assertEquals("native-server-id", pending.streamId)
        assertTrue(pending.completed.count == 0L)
    }

    @Test
    fun matchingStreamInfoCompletesOwnPendingStartButNotRemoteOne() {
        val pending = PendingStreamStart(7L, StreamType.CAMERA)
        pending.nativeStreamId = "server-camera"
        val info = Ts6StreamInfo("server-camera", 42, StreamType.CAMERA)

        assertTrue(!completePendingStreamStartForStreamInfo(pending, info, 99, 7L))
        assertTrue(pending.completed.count == 1L)
        assertTrue(completePendingStreamStartForStreamInfo(pending, info, 42, 7L))
        assertEquals("server-camera", pending.streamId)
    }

    @Test
    fun streamDiscoveryDeduplicatesByGenerationClientAndChannelWithCooldown() {
        var now = 0L
        val discovery = StreamInfoDiscovery({ now }, cooldownNanos = 1_000L)

        assertTrue(discovery.shouldRequest(1L, 42, 7))
        assertTrue(!discovery.shouldRequest(1L, 42, 7))
        assertTrue(discovery.shouldRequest(1L, 42, 8))
        assertTrue(discovery.shouldRequest(2L, 42, 7))
        now = 1_001L
        assertTrue(discovery.shouldRequest(1L, 42, 7))
    }
}
