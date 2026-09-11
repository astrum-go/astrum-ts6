package br.app.astrum.ts6.app.service

import br.app.astrum.ts6.protocol.Ts3Participant
import org.junit.Assert.assertEquals
import org.junit.Test

class ParticipantAudioSettingsTest {
    @Test
    fun gainRespectsMuteAndVolumeBounds() {
        assertEquals(1.5f, ParticipantAudioSettings(volumePercent = 150).gain)
        assertEquals(0f, ParticipantAudioSettings(volumePercent = 150, muted = true).gain)
        assertEquals(2f, ParticipantAudioSettings(volumePercent = 500).gain)
        assertEquals(0f, ParticipantAudioSettings(volumePercent = -10).gain)
    }

    @Test
    fun controlKeyPrefersStableTeamSpeakIdentity() {
        val participant = Ts3Participant(7, 1, "Alice", false, false, false, "stable-id")

        assertEquals("stable-id", participant.audioControlKey())
        assertEquals("session:7", participant.copy(uniqueIdentifier = "").audioControlKey())
    }
}
