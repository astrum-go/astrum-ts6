package br.app.astrum.ts6.audio.opus

import org.junit.Assert.assertEquals
import org.junit.Test

class OpusAudioPlayerTest {
    @Test
    fun signedPacketDistanceHandlesSixteenBitWraparound() {
        assertEquals(1, OpusAudioPlayer.signedPacketDistance(65_535, 0))
        assertEquals(3, OpusAudioPlayer.signedPacketDistance(65_534, 1))
    }

    @Test
    fun signedPacketDistanceIdentifiesDuplicateAndOlderPacket() {
        assertEquals(0, OpusAudioPlayer.signedPacketDistance(12, 12))
        assertEquals(-1, OpusAudioPlayer.signedPacketDistance(12, 11))
        assertEquals(-1, OpusAudioPlayer.signedPacketDistance(0, 65_535))
    }
}
