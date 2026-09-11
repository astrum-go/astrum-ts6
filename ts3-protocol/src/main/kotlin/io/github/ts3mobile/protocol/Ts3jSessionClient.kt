package io.github.ts3mobile.protocol

import com.github.manevolent.ts3j.command.CommandException
import com.github.manevolent.ts3j.command.SingleCommand
import com.github.manevolent.ts3j.command.parameter.CommandSingleParameter
import com.github.manevolent.ts3j.audio.Microphone
import com.github.manevolent.ts3j.event.ChannelCreateEvent
import com.github.manevolent.ts3j.event.ChannelDeletedEvent
import com.github.manevolent.ts3j.event.ChannelEditedEvent
import com.github.manevolent.ts3j.event.ChannelListEvent
import com.github.manevolent.ts3j.event.ChannelMovedEvent
import com.github.manevolent.ts3j.event.ClientJoinEvent
import com.github.manevolent.ts3j.event.ClientLeaveEvent
import com.github.manevolent.ts3j.event.ClientMovedEvent
import com.github.manevolent.ts3j.event.ClientUpdatedEvent
import com.github.manevolent.ts3j.event.DisconnectedEvent
import com.github.manevolent.ts3j.event.TS3Listener
import com.github.manevolent.ts3j.event.UnknownTeamspeakEvent
import com.github.manevolent.ts3j.enums.CodecType
import com.github.manevolent.ts3j.protocol.ProtocolRole
import com.github.manevolent.ts3j.protocol.packet.PacketBody0Voice
import com.github.manevolent.ts3j.protocol.packet.PacketBody1VoiceWhisper
import com.github.manevolent.ts3j.protocol.packet.PacketBody2Command
import com.github.manevolent.ts3j.protocol.PacketKind
import com.github.manevolent.ts3j.protocol.socket.client.LocalTeamspeakClientSocket
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class Ts3jSessionClient : Ts3SessionClient {
    private val generation = AtomicLong(0L)
    private val snapshotStore = SessionSnapshotStore()

    @Volatile
    private var socket: LocalTeamspeakClientSocket? = null

    @Volatile
    private var listener: Ts3SessionListener? = null

    @Volatile
    private var voiceSource: EncodedVoiceSource? = null

    private val commandExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ts3-cmd-ack").apply { isDaemon = true }
    }

    private val pendingStreamStartedLatch = AtomicReference<CountDownLatch?>(null)
    private val pendingStreamStartedId = AtomicReference<String?>(null)

    override fun connect(
        config: ServerConfig,
        identityMaterial: String,
        listener: Ts3SessionListener,
    ) {
        val normalized = config.normalized()
        val validationError = normalized.validationError()
        require(validationError == null) { validationError ?: "Invalid server configuration" }

        disconnectQuietly()
        val token = generation.incrementAndGet()
        this.listener = listener
        snapshotStore.clear()
        emitStatus(token, ConnectionStatus(ConnectionPhase.CONNECTING))

        val identity = Ts3IdentityCodec.decode(identityMaterial)
        val client = LocalTeamspeakClientSocket()
        val asynchronousFailure = AtomicReference<Throwable?>(null)
        socket = client

        client.setIdentity(identity)
        client.setNickname(normalized.nickname)
        client.setHWID(identity.uid.toBase64())
        client.setMicrophone(voiceSource?.toMicrophone())
        client.setExceptionHandler { error ->
            if (token == generation.get()) {
                asynchronousFailure.compareAndSet(null, error)
                logFailure("background protocol failure", error)
                emitStatus(
                    token,
                    ConnectionStatus(
                        ConnectionPhase.ERROR,
                        error.conciseMessage(),
                        retryable = error.isRetryableConnectionFailure(),
                    ),
                )
            }
        }
        client.setVoiceHandler { packet ->
            try {
                forwardVoicePacket(packet, token)
            } catch (error: RuntimeException) {
                logFailure("voice callback rejected", error)
            }
        }
        client.setWhisperHandler { packet ->
            try {
                forwardWhisperPacket(packet, token)
            } catch (error: RuntimeException) {
                logFailure("whisper callback rejected", error)
            }
        }
        client.addListener(createListener(client, token))

        try {
            val address = InetSocketAddress(
                InetAddress.getByName(normalized.host),
                normalized.port,
            )
            logDiagnostic("connecting to ${address.address.hostAddress}:${address.port}")
            client.connect(address, normalized.password.takeIf(String::isNotBlank), CONNECT_TIMEOUT_MS)
            if (token != generation.get()) {
                runCatching { client.close() }
                if (socket === client) socket = null
                return
            }
            try {
                client.subscribeAll()
            } catch (error: CommandException) {
                logDiagnostic("event subscriptions unavailable: ${error.conciseMessage()}")
            }
            if (token != generation.get()) {
                runCatching { client.close() }
                if (socket === client) socket = null
                return
            }
            publishSnapshot(token)
            emitStatus(token, ConnectionStatus(ConnectionPhase.CONNECTED))
            queryActiveStreams(client)
        } catch (error: Throwable) {
            val reportedError = asynchronousFailure.get() ?: error.withNetworkDiagnostics(client)
            logFailure("connection failed", reportedError)
            if (token == generation.get()) {
                emitStatus(
                    token,
                    ConnectionStatus(
                        ConnectionPhase.ERROR,
                        reportedError.conciseMessage(),
                        retryable = reportedError.isRetryableConnectionFailure(),
                    ),
                )
            }
            runCatching { client.close() }
            if (socket === client) socket = null
            throw reportedError
        }
    }

    override fun disconnect(reason: String) {
        val current = socket ?: run {
            listener?.onStatusChanged(ConnectionStatus())
            return
        }
        generation.incrementAndGet()
        listener?.onStatusChanged(ConnectionStatus(ConnectionPhase.DISCONNECTING))
        try {
            runCatching { current.disconnect(reason) }
        } finally {
            runCatching { current.close() }
            if (socket === current) socket = null
            listener?.onSnapshotChanged(SessionSnapshot.Empty)
            listener?.onStatusChanged(ConnectionStatus())
        }
    }

    override fun setVoiceSource(source: EncodedVoiceSource?) {
        voiceSource = source
        socket?.setMicrophone(source?.toMicrophone())
    }

    override fun joinChannel(channelId: Int, password: String) {
        require(channelId > 0) { "Invalid channel ID" }
        val current = socket?.takeIf { it.isConnected }
            ?: error("Not connected to a TeamSpeak server")
        current.joinChannel(channelId, password)
        snapshotStore.updateParticipant(current.clientId) { participant ->
            participant.copy(channelId = channelId)
        }
        publishSnapshot(generation.get())
        queryActiveStreams(current)
    }

    override fun startStream(type: StreamType, width: Int, height: Int, fps: Int): String {
        val latch = CountDownLatch(1)
        pendingStreamStartedLatch.set(latch)
        pendingStreamStartedId.set(null)
        val serverIdRef = AtomicReference<String?>(null)
        val ownClientId = socket?.takeIf { it.isConnected }?.clientId ?: 0
        sendRawCommand(
            "setupstream",
            "name" to if (type == StreamType.SCREEN) "Screen" else "Camera",
            "type" to type.value.toString(),
            "bitrate" to "4608",
            "accessibility" to "1",
            "mode" to "1",
            "viewer_limit" to "0",
            "audio" to "0",
        ) { resp ->
            val serverStreamId = resp?.get("id")?.value
                ?: resp?.get("streamid")?.value
            if (!serverStreamId.isNullOrBlank()) {
                serverIdRef.set(serverStreamId)
                val streamInfo = Ts6StreamInfo(
                    streamId = serverStreamId,
                    clientId = ownClientId,
                    type = type,
                    width = width,
                    height = height,
                    fps = fps,
                    bitrate = 4608,
                    description = if (type == StreamType.SCREEN) "Screen" else "Camera",
                )
                snapshotStore.putStream(streamInfo)
                publishSnapshot(generation.get())
                listener?.onStreamStarted(streamInfo)
                latch.countDown()
            }
        }
        val completed = latch.await(5000, TimeUnit.MILLISECONDS)
        if (!completed) {
            logDiagnostic("startStream: TIMEOUT aguardando ID do servidor — usando fallback UUID")
        }
        pendingStreamStartedLatch.set(null)
        val finalId = serverIdRef.get() ?: pendingStreamStartedId.get() ?: UUID.randomUUID().toString()
        logDiagnostic("startStream resolved id=$finalId")
        return finalId
    }

    override fun stopStream(streamId: String) {
        sendRawCommand(
            "stopstream",
            "id" to streamId,
            "reason" to "1",
        )
        snapshotStore.removeStream(streamId)
        publishSnapshot(generation.get())
    }

    override fun requestJoinStream(targetClientId: Int, streamId: String) {
        sendRawCommand(
            "joinstreamrequest",
            "id" to streamId,
            "clid" to targetClientId.toString(),
            "msg" to "",
            "is_remove" to "0",
        )
    }

    override fun leaveStream(targetClientId: Int, streamId: String) {
        sendRawCommand(
            "joinstreamrequest",
            "id" to streamId,
            "clid" to targetClientId.toString(),
            "msg" to "",
            "is_remove" to "1",
        )
    }

    override fun respondJoinStreamRequest(targetClientId: Int, streamId: String, allow: Boolean, offer: String?) {
        val params = mutableListOf(
            "id" to streamId,
            "clid" to targetClientId.toString(),
            "msg" to "",
        )
        if (!offer.isNullOrBlank()) {
            params.add("offer" to offer)
        }
        params.add("decision" to if (allow) "1" else "0")
        sendRawCommand("respondjoinstreamrequest", *params.toTypedArray())
    }

    override fun sendStreamSignaling(targetClientId: Int, streamId: String, payload: String) {
        sendRawCommand(
            "streamsignaling",
            "id" to streamId,
            "clid" to targetClientId.toString(),
            "json" to payload,
        )
    }

    override fun requestStreamInfo(targetClientId: Int) {
        sendRawCommand(
            "requeststreaminfo",
            "clid" to targetClientId.toString(),
        )
    }

    private fun sendRawCommand(
        name: String,
        vararg params: Pair<String, String>,
        onSuccess: ((SingleCommand?) -> Unit)? = null,
    ) {
        val current = socket?.takeIf { it.isConnected }
            ?: error("Not connected to a TeamSpeak server")
        try {
            val cmd = SingleCommand(
                name,
                ProtocolRole.CLIENT,
                params.map { (k, v) -> CommandSingleParameter(k, v) },
            )
            val paramStr = params.joinToString(" ") { "${it.first}=${it.second}" }
            logDiagnostic("-> SEND [$name]: $paramStr")
            val response = current.executeCommand(cmd)
            commandExecutor.execute {
                try {
                    val result = response.get(5000L)
                    logDiagnostic("<- ACK [$name]: success ($result)")
                    val firstCmd = result.firstOrNull()
                    onSuccess?.invoke(firstCmd)
                } catch (cmdErr: Throwable) {
                    logDiagnostic("<- ERR [$name]: ${cmdErr.message}")
                }
            }
        } catch (error: Throwable) {
            logFailure("failed to send command $name", error)
        }
    }

    private fun queryActiveStreams(client: LocalTeamspeakClientSocket) {
        val ownId = client.clientId
        commandExecutor.execute {
            try {
                Thread.sleep(300)
            } catch (ignored: InterruptedException) {}
            val otherParticipants = snapshotStore.snapshot().participants.filter { it.id != ownId }
            for (p in otherParticipants) {
                requestStreamInfo(p.id)
            }
        }
    }

    override fun close() {
        generation.incrementAndGet()
        val current = socket
        socket = null
        runCatching { current?.close() }
        snapshotStore.clear()
        voiceSource = null
        listener = null
    }

    private fun createListener(
        client: LocalTeamspeakClientSocket,
        token: Long,
    ): TS3Listener = object : TS3Listener {
        override fun onDisconnected(event: DisconnectedEvent) {
            if (token != generation.get()) return
            socket = null
            runCatching { client.close() }
            snapshotStore.clear()
            listener?.onSnapshotChanged(SessionSnapshot.Empty)
            emitStatus(
                token,
                ConnectionStatus(
                    ConnectionPhase.DISCONNECTED,
                    "Server closed the connection (${event.reasonId})",
                    retryable = event.reasonId !in TERMINAL_DISCONNECT_REASONS,
                ),
            )
        }

        override fun onChannelList(event: ChannelListEvent) {
            snapshotStore.putChannel(event.map.toChannel(event.channelId))
            publishSnapshotWhenConnected(client, token)
        }

        override fun onClientJoin(event: ClientJoinEvent) {
            if (event.clientType == REGULAR_CLIENT_TYPE) {
                snapshotStore.putParticipant(event.toParticipant())
                publishSnapshotWhenConnected(client, token)
                if (client.isConnected && event.clientId != client.clientId) {
                    commandExecutor.execute {
                        requestStreamInfo(event.clientId)
                    }
                }
            }
        }

        override fun onClientLeave(event: ClientLeaveEvent) {
            snapshotStore.removeParticipant(event.clientId)
            snapshotStore.removeStreamsByClient(event.clientId)
            publishSnapshotWhenConnected(client, token)
        }

        override fun onClientMoved(event: ClientMovedEvent) {
            snapshotStore.updateParticipant(event.clientId) { it.copy(channelId = event.targetChannelId) }
            publishSnapshotWhenConnected(client, token)
        }

        override fun onClientChanged(event: ClientUpdatedEvent) {
            snapshotStore.updateParticipant(event.clientId) { it.withUpdates(event.map) }
            publishSnapshotWhenConnected(client, token)
        }

        override fun onChannelCreate(event: ChannelCreateEvent) {
            snapshotStore.putChannel(event.map.toChannel(event.channelId))
            publishSnapshotWhenConnected(client, token)
        }

        override fun onChannelDeleted(event: ChannelDeletedEvent) {
            snapshotStore.removeChannel(event.channelId)
            publishSnapshotWhenConnected(client, token)
        }

        override fun onChannelEdit(event: ChannelEditedEvent) {
            snapshotStore.updateChannel(event.channelId) { it.withUpdates(event.map) }
            publishSnapshotWhenConnected(client, token)
        }

        override fun onChannelMoved(event: ChannelMovedEvent) {
            snapshotStore.updateChannel(event.channelId) {
                it.copy(parentId = event.channelParentId, orderAfterId = event.channelOrder)
            }
            publishSnapshotWhenConnected(client, token)
        }

        override fun onUnknownEvent(event: UnknownTeamspeakEvent) {
            if (token != generation.get()) return
            handleUnknownTeamspeakEvent(event, client, token)
        }
    }

    private fun handleUnknownTeamspeakEvent(
        event: UnknownTeamspeakEvent,
        client: LocalTeamspeakClientSocket,
        token: Long,
    ) {
        val map = event.map ?: emptyMap()
        logDiagnostic("unknown event: ${event.command} params=$map")
        when (event.command) {
            "notifystreamstarted", "notifystreaminfo" -> {
                val streamId = map["id"] ?: map["streamid"].orEmpty()
                val clid = map["clid"]?.toIntOrNull() ?: event.invokerId
                if (streamId.isNotBlank() && clid > 0) {
                    val typeStr = map["type"] ?: "1"
                    val name = map["name"].orEmpty()
                    val stream = Ts6StreamInfo(
                        streamId = streamId,
                        clientId = clid,
                        type = StreamType.fromValue(typeStr, name),
                        width = map["width"]?.toIntOrNull() ?: 1280,
                        height = map["height"]?.toIntOrNull() ?: 720,
                        fps = map["fps"]?.toIntOrNull() ?: 30,
                        bitrate = map["bitrate"]?.toIntOrNull() ?: 4608,
                        description = name,
                    )
                    logDiagnostic("Stream info/started: id=$streamId by clid=$clid type=${stream.type} (${stream.description})")
                    snapshotStore.putStream(stream)
                    publishSnapshotWhenConnected(client, token)
                    listener?.onStreamStarted(stream)

                    val ownId = socket?.takeIf { it.isConnected }?.clientId
                    if (ownId != null && clid == ownId) {
                        pendingStreamStartedId.set(streamId)
                        pendingStreamStartedLatch.get()?.countDown()
                    }
                }
            }
            "notifystreamstopped" -> {
                val streamId = map["id"] ?: map["streamid"].orEmpty()
                val clid = map["clid"]?.toIntOrNull() ?: event.invokerId
                logDiagnostic("Stream stopped: id=$streamId by clid=$clid")
                snapshotStore.removeStream(streamId)
                publishSnapshotWhenConnected(client, token)
                listener?.onStreamStopped(streamId, clid)
            }
            "notifystreamupdated" -> {
                val streamId = map["id"] ?: map["streamid"].orEmpty()
                val existing = snapshotStore.getStream(streamId)
                if (existing != null) {
                    val updated = existing.copy(
                        width = map["width"]?.toIntOrNull() ?: existing.width,
                        height = map["height"]?.toIntOrNull() ?: existing.height,
                        fps = map["fps"]?.toIntOrNull() ?: existing.fps,
                        bitrate = map["bitrate"]?.toIntOrNull() ?: existing.bitrate,
                    )
                    snapshotStore.putStream(updated)
                    publishSnapshotWhenConnected(client, token)
                }
            }
            "notifystreamsignaling" -> {
                val streamId = map["id"] ?: map["streamid"].orEmpty()
                val clid = map["clid"]?.toIntOrNull() ?: event.invokerId
                val payload = map["json"] ?: map["msg"] ?: map["signaling"] ?: map["data"].orEmpty()
                logDiagnostic("<- SIGNALING received from clid=$clid stream=$streamId payloadLength=${payload.length}")
                listener?.onStreamSignaling(Ts6StreamSignaling(streamId, clid, payload))
            }
            "notifyjoinstreamrequest" -> {
                val streamId = map["id"] ?: map["streamid"].orEmpty()
                val clid = map["clid"]?.toIntOrNull() ?: event.invokerId
                logDiagnostic("<- JOIN REQUEST from clid=$clid for stream=$streamId")
                listener?.onStreamJoinRequested(streamId, clid)
            }
            "notifyrespondjoinstreamrequest" -> {
                val streamId = map["id"] ?: map["streamid"].orEmpty()
                val clid = map["clid"]?.toIntOrNull() ?: event.invokerId
                val decision = map["decision"] ?: map["status"]
                val offer = map["offer"]
                logDiagnostic("<- JOIN RESPONSE from clid=$clid for stream=$streamId decision=$decision offerLength=${offer?.length}")
                if (!offer.isNullOrBlank()) {
                    listener?.onStreamSignaling(Ts6StreamSignaling(streamId, clid, offer))
                }
            }
            "notifystreamclientjoined" -> {
                val streamId = map["id"] ?: map["streamid"].orEmpty()
                val clid = map["clid"]?.toIntOrNull() ?: event.invokerId
                logDiagnostic("<- CLIENT JOINED: clid=$clid stream=$streamId")
                listener?.onStreamClientJoined(streamId, clid)
            }
            "notifystreamclientleft" -> {
                val streamId = map["id"] ?: map["streamid"].orEmpty()
                val clid = map["clid"]?.toIntOrNull() ?: event.invokerId
                logDiagnostic("<- CLIENT LEFT: clid=$clid stream=$streamId")
                listener?.onStreamClientLeft(streamId, clid)
            }
        }
    }

    private fun publishSnapshotWhenConnected(client: LocalTeamspeakClientSocket, token: Long) {
        if (client.isConnected) publishSnapshot(token)
    }

    private fun publishSnapshot(token: Long) {
        if (token != generation.get()) return
        val ownClientId = socket?.takeIf { it.isConnected }?.clientId
        listener?.onSnapshotChanged(snapshotStore.snapshot().copy(ownClientId = ownClientId))
    }

    private fun emitStatus(token: Long, status: ConnectionStatus) {
        if (token == generation.get()) listener?.onStatusChanged(status)
    }

    private fun forwardVoicePacket(packet: PacketBody0Voice, token: Long) {
        forwardVoiceFrame(
            clientId = packet.clientId,
            packetId = packet.packetId,
            codecType = packet.codecType,
            codecData = packet.codecData,
            isWhisper = false,
            token = token,
        )
    }

    private fun forwardWhisperPacket(packet: PacketBody1VoiceWhisper, token: Long) {
        forwardVoiceFrame(
            clientId = packet.clientId,
            packetId = packet.packetId,
            codecType = packet.codecType,
            codecData = packet.codecData,
            isWhisper = true,
            token = token,
        )
    }

    private fun forwardVoiceFrame(
        clientId: Int,
        packetId: Int,
        codecType: CodecType,
        codecData: ByteArray,
        isWhisper: Boolean,
        token: Long,
    ) {
        if (token != generation.get()) return
        val codec = when (codecType) {
            CodecType.OPUS_VOICE -> VoiceCodec.OPUS_VOICE
            CodecType.OPUS_MUSIC -> VoiceCodec.OPUS_MUSIC
            else -> return
        }
        listener?.onVoiceFrame(
            VoiceFrame(
                clientId = clientId,
                packetId = packetId,
                codec = codec,
                encodedData = codecData.copyOf(),
                isWhisper = isWhisper,
            ),
        )
    }

    private fun disconnectQuietly() {
        val current = socket ?: return
        runCatching { current.disconnect("Replacing connection") }
        runCatching { current.close() }
        if (socket === current) socket = null
    }

    private fun EncodedVoiceSource.toMicrophone(): Microphone = object : Microphone {
        override fun isReady(): Boolean = runCatching { this@toMicrophone.isReady() }
            .getOrDefault(false)

        override fun getCodec(): CodecType = CodecType.OPUS_VOICE

        override fun provide(): ByteArray = runCatching { this@toMicrophone.pollEncodedFrame() }
            .getOrNull()
            ?: EMPTY_AUDIO_FRAME
    }

    private fun Map<String, String>.toChannel(id: Int) = Ts3Channel(
        id = id,
        parentId = intValue("pid") ?: intValue("cpid") ?: 0,
        orderAfterId = intValue("channel_order") ?: 0,
        name = get("channel_name").orEmpty(),
        clientCount = 0,
        hasPassword = booleanValue("channel_flag_password") ?: false,
        isDefault = booleanValue("channel_flag_default") ?: false,
    )

    private fun ClientJoinEvent.toParticipant() = Ts3Participant(
        id = clientId,
        channelId = clientTargetId,
        nickname = clientNickname,
        isTalking = isClientTalking,
        isInputMuted = isClientInputMuted,
        isOutputMuted = isClientOutputMuted,
        uniqueIdentifier = uniqueClientIdentifier,
    )

    private fun Ts3Channel.withUpdates(values: Map<String, String>) = copy(
        parentId = values.intValue("pid") ?: values.intValue("cpid") ?: parentId,
        orderAfterId = values.intValue("channel_order") ?: orderAfterId,
        name = values["channel_name"] ?: name,
        hasPassword = values.booleanValue("channel_flag_password") ?: hasPassword,
        isDefault = values.booleanValue("channel_flag_default") ?: isDefault,
    )

    private fun Ts3Participant.withUpdates(values: Map<String, String>) = copy(
        nickname = values["client_nickname"] ?: nickname,
        uniqueIdentifier = values["client_unique_identifier"] ?: uniqueIdentifier,
        isTalking = values.booleanValue("client_flag_talking")
            ?: values.booleanValue("status")
            ?: isTalking,
        isInputMuted = values.booleanValue("client_input_muted") ?: isInputMuted,
        isOutputMuted = values.booleanValue("client_output_muted") ?: isOutputMuted,
    )

    private fun Map<String, String>.intValue(key: String): Int? = get(key)?.toIntOrNull()

    private fun Map<String, String>.booleanValue(key: String): Boolean? = get(key)?.let { it == "1" }

    private fun Throwable.conciseMessage(): String {
        var cursor: Throwable? = this
        while (cursor != null) {
            cursor.message?.takeIf(String::isNotBlank)?.let { return it.take(180) }
            cursor = cursor.cause
        }
        return this::class.java.simpleName
    }

    private fun Throwable.withNetworkDiagnostics(client: LocalTeamspeakClientSocket): Throwable {
        if (this !is TimeoutException) return this

        val sentPackets = PacketKind.entries.sumOf { client.getStatistics(it).sentPackets }
        val receivedPackets = PacketKind.entries.sumOf { client.getStatistics(it).receivedPackets }
        val detail = if (receivedPackets == 0) {
            "the server sent no TeamSpeak response"
        } else {
            "sent $sentPackets and received $receivedPackets UDP packets before the handshake stalled"
        }
        return IOException("TeamSpeak handshake timed out: $detail", this)
    }

    private fun logDiagnostic(message: String) {
        System.err.println("TS3_DIAG: $message")
    }

    private fun logFailure(stage: String, error: Throwable) {
        System.err.println("TS3_DIAG: $stage: ${error.conciseMessage()}")
        error.printStackTrace(System.err)
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000L
        const val REGULAR_CLIENT_TYPE = 0
        val TERMINAL_DISCONNECT_REASONS = setOf(4, 5)
        val EMPTY_AUDIO_FRAME = ByteArray(0)
    }
}
