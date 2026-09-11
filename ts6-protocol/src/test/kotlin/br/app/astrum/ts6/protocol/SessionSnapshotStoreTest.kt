package br.app.astrum.ts6.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionSnapshotStoreTest {
    private val firstChannel = Ts3Channel(1, 0, 0, "Lobby", 0, false, true)
    private val secondChannel = Ts3Channel(2, 0, 1, "Games", 0, false, false)
    private val participant = Ts3Participant(7, 1, "Alice", false, false, false)

    @Test
    fun snapshotTracksMovesAndChannelCounts() {
        val store = SessionSnapshotStore()
        store.putChannel(firstChannel)
        store.putChannel(secondChannel)
        store.putParticipant(participant)

        assertEquals(listOf(1, 0), store.snapshot().channels.map { it.clientCount })

        store.updateParticipant(participant.id) { it.copy(channelId = secondChannel.id) }

        assertEquals(listOf(0, 1), store.snapshot().channels.map { it.clientCount })
    }

    @Test
    fun snapshotTracksEditsAndRemovals() {
        val store = SessionSnapshotStore()
        store.putChannel(firstChannel)
        store.putParticipant(participant)

        store.updateChannel(firstChannel.id) { it.copy(name = "Welcome") }
        store.removeParticipant(participant.id)

        val snapshot = store.snapshot()
        assertEquals("Welcome", snapshot.channels.single().name)
        assertEquals(0, snapshot.channels.single().clientCount)
        assertEquals(emptyList<Ts3Participant>(), snapshot.participants)
    }

    @Test
    fun snapshotDerivesOwnCurrentChannel() {
        val snapshot = SessionSnapshot(
            channels = listOf(firstChannel, secondChannel),
            participants = listOf(participant),
            ownClientId = participant.id,
        )

        assertEquals(firstChannel.id, snapshot.currentChannelId)
        assertEquals(null, snapshot.copy(ownClientId = 999).currentChannelId)
    }
}
