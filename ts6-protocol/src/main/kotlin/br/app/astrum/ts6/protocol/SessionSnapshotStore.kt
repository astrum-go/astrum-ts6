package br.app.astrum.ts6.protocol

import java.util.concurrent.TimeUnit

internal class StreamInfoDiscovery(
    private val nowNanos: () -> Long = System::nanoTime,
    private val cooldownNanos: Long = TimeUnit.SECONDS.toNanos(1),
) {
    private data class RequestKey(val generation: Long, val clientId: Int, val channelId: Int)

    private val requestedAt = mutableMapOf<RequestKey, Long>()

    @Synchronized
    fun shouldRequest(generation: Long, clientId: Int, channelId: Int): Boolean {
        val now = nowNanos()
        requestedAt.entries.removeIf { now - it.value >= cooldownNanos }
        val key = RequestKey(generation, clientId, channelId)
        if (requestedAt.containsKey(key)) return false
        requestedAt[key] = now
        return true
    }

    @Synchronized
    fun clear() {
        requestedAt.clear()
    }

    @Synchronized
    fun removeClient(clientId: Int) {
        requestedAt.keys.removeIf { it.clientId == clientId }
    }
}

internal class SessionSnapshotStore {
    private val lock = Any()
    private val channels = linkedMapOf<Int, Ts3Channel>()
    private val participants = linkedMapOf<Int, Ts3Participant>()
    private val streams = linkedMapOf<String, Ts6StreamInfo>()

    fun clear() = synchronized(lock) {
        channels.clear()
        participants.clear()
        streams.clear()
    }

    fun putChannel(channel: Ts3Channel) = synchronized(lock) {
        channels[channel.id] = channel
    }

    fun replaceChannels(snapshot: Collection<Ts3Channel>) = synchronized(lock) {
        channels.clear()
        snapshot.forEach { channels[it.id] = it }
    }

    fun updateChannel(id: Int, transform: (Ts3Channel) -> Ts3Channel) = synchronized(lock) {
        channels[id]?.let { channels[id] = transform(it) }
    }

    fun removeChannel(id: Int) = synchronized(lock) {
        channels.remove(id)
    }

    fun putParticipant(participant: Ts3Participant): Boolean = synchronized(lock) {
        val isNew = !participants.containsKey(participant.id)
        participants[participant.id] = participant
        isNew
    }

    fun replaceParticipants(snapshot: Collection<Ts3Participant>) = synchronized(lock) {
        participants.clear()
        snapshot.forEach { participants[it.id] = it }
    }

    fun updateParticipant(id: Int, transform: (Ts3Participant) -> Ts3Participant) = synchronized(lock) {
        participants[id]?.let { participants[id] = transform(it) }
    }

    fun updateOrInsertParticipant(
        id: Int,
        transform: (Ts3Participant?) -> Ts3Participant,
    ) = synchronized(lock) {
        participants[id] = transform(participants[id])
    }

    fun removeParticipant(id: Int) = synchronized(lock) {
        participants.remove(id)
        streams.values.filter { it.clientId == id }.forEach { streams.remove(it.streamId) }
    }

    fun participant(id: Int): Ts3Participant? = synchronized(lock) {
        participants[id]
    }

    fun putStream(stream: Ts6StreamInfo) = synchronized(lock) {
        streams[stream.streamId] = stream
    }

    fun removeStream(streamId: String) = synchronized(lock) {
        streams.remove(streamId)
    }

    fun removeStreamsByClient(clientId: Int) = synchronized(lock) {
        streams.values.filter { it.clientId == clientId }.forEach { streams.remove(it.streamId) }
    }

    fun getStream(streamId: String): Ts6StreamInfo? = synchronized(lock) {
        streams[streamId]
    }

    fun snapshot(): SessionSnapshot = synchronized(lock) {
        val countsByChannel = participants.values.groupingBy { it.channelId }.eachCount()
        val streamsByClient = streams.values.groupBy { it.clientId }
        SessionSnapshot(
            channels = channels.values.map { channel ->
                channel.copy(clientCount = countsByChannel[channel.id] ?: 0)
            },
            participants = participants.values.map { participant ->
                val clientStreams = streamsByClient[participant.id].orEmpty()
                participant.copy(
                    hasActiveCamera = clientStreams.any { it.type == StreamType.CAMERA },
                    hasActiveScreen = clientStreams.any { it.type == StreamType.SCREEN || it.type == StreamType.WINDOW },
                    hasActiveStream = clientStreams.isNotEmpty(),
                )
            }.sortedBy { it.nickname.lowercase() },
            activeStreams = streams.values.toList(),
        )
    }
}
