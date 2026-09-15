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

    @Test
    fun outOfOrderParticipantUpdatesMaterializeWithoutLosingExistingData() {
        val store = SessionSnapshotStore()

        store.updateOrInsertParticipant(42) { existing ->
            existing?.copy(isTalking = true)
                ?: Ts3Participant(42, 7, "Alice", true, true, false, "uid-42")
        }
        store.updateOrInsertParticipant(42) { existing ->
            existing!!.copy(channelId = 8)
        }

        val participant = store.snapshot().participants.single()
        assertEquals(42, participant.id)
        assertEquals(8, participant.channelId)
        assertEquals("Alice", participant.nickname)
        assertEquals(true, participant.isTalking)
        assertEquals(true, participant.isInputMuted)
        assertEquals("uid-42", participant.uniqueIdentifier)
    }

    @Test
    fun authoritativeChannelAndParticipantListsReconcileStaleEntries() {
        val store = SessionSnapshotStore()
        store.putChannel(firstChannel)
        store.putParticipant(participant)

        store.replaceChannels(
            listOf(
                firstChannel.copy(parentId = 4, orderAfterId = 3, topic = "Updated"),
                secondChannel,
            ),
        )
        store.replaceParticipants(
            listOf(
                participant.copy(
                    uniqueIdentifier = "uid-alice",
                    clientType = 1,
                    isAway = true,
                    isInputMuted = true,
                ),
            ),
        )

        val snapshot = store.snapshot()
        assertEquals(listOf(1, 2), snapshot.channels.map { it.id })
        assertEquals(4, snapshot.channels.first().parentId)
        assertEquals("Updated", snapshot.channels.first().topic)
        assertEquals(listOf(7), snapshot.participants.map { it.id })
        assertEquals("uid-alice", snapshot.participants.single().uniqueIdentifier)
        assertEquals(1, snapshot.participants.single().clientType)
        assertEquals(true, snapshot.participants.single().isAway)
        assertEquals(true, snapshot.participants.single().isInputMuted)
    }

    @Test
    fun participantMetadataUpdateAfterMoveConvergesOnOneEntry() {
        val store = SessionSnapshotStore()

        store.updateOrInsertParticipant(42) { existing ->
            existing?.copy(channelId = 8)
                ?: Ts3Participant(42, 8, "", false, false, false)
        }
        store.updateOrInsertParticipant(42) { existing ->
            existing!!.copy(
                nickname = "Alice",
                uniqueIdentifier = "uid-42",
                clientType = 0,
                isAway = true,
                isOutputMuted = true,
            )
        }

        val result = store.snapshot().participants.single()
        assertEquals(42, result.id)
        assertEquals(8, result.channelId)
        assertEquals("Alice", result.nickname)
        assertEquals("uid-42", result.uniqueIdentifier)
        assertEquals(true, result.isAway)
        assertEquals(true, result.isOutputMuted)
    }

    @Test
    fun explicitZeroChannelMembershipRemainsAuthoritative() {
        val store = SessionSnapshotStore()
        store.putChannel(firstChannel)
        store.putParticipant(participant.copy(channelId = 0))

        val snapshot = store.snapshot()
        assertEquals(0, snapshot.participants.single().channelId)
        assertEquals(0, snapshot.channels.single().clientCount)
    }

    @Test
    fun authoritativeParticipantReplacementDiscardsStaleMembership() {
        val store = SessionSnapshotStore()
        store.putParticipant(participant.copy(channelId = secondChannel.id))

        store.replaceParticipants(listOf(participant.copy(channelId = 0)))

        val snapshot = store.snapshot()
        assertEquals(listOf(participant.id), snapshot.participants.map { it.id })
        assertEquals(0, snapshot.participants.single().channelId)
    }
}
