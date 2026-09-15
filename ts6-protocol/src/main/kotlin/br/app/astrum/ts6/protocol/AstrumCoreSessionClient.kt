package br.app.astrum.ts6.protocol

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

private fun JSONObject.firstNonBlankString(vararg keys: String): String? {
    for (key in keys) {
        optString(key).takeIf { it.isNotBlank() }?.let { return it }
    }
    return null
}

private fun JSONObject.firstInt(vararg keys: String): Int? {
    for (key in keys) {
        when (val value = opt(key)) {
            is Number -> return value.toInt()
            is String -> value.toIntOrNull()?.let { return it }
        }
    }
    return null
}

internal fun JSONObject.toTs6StreamInfo(): Ts6StreamInfo? {
    val streamId = firstNonBlankString("stream_id", "streamid", "id", "stream") ?: return null
    val clientId = firstInt("client_id", "client", "clid", "invoker", "invokerid") ?: return null
    val name = firstNonBlankString("name", "stream_name", "description").orEmpty()
    val streamType = firstNonBlankString("stream_type", "streamtype", "type")
    return Ts6StreamInfo(
        streamId = streamId,
        clientId = clientId,
        type = StreamType.fromValue(streamType, name),
        width = firstInt("width", "stream_width") ?: 1280,
        height = firstInt("height", "stream_height") ?: 720,
        fps = firstInt("fps", "framerate", "frame_rate") ?: 30,
        bitrate = firstInt("bitrate", "bitrate_kbps") ?: 2000,
        description = name.ifEmpty { "Stream" },
    )
}

internal class PendingStreamStart(
    val generation: Long,
    val type: StreamType,
) {
    val completed = CountDownLatch(1)

    @Volatile
    var streamId: String? = null

    @Volatile
    var nativeStreamId: String? = null

    @Volatile
    var failure: Throwable? = null
}

internal fun completePendingStreamStartById(
    pending: PendingStreamStart,
    streamId: String,
    generation: Long,
): Boolean {
    if (pending.generation != generation || streamId.isBlank()) return false
    pending.streamId = streamId
    pending.completed.countDown()
    return true
}

internal fun completePendingStreamStartForStreamInfo(
    pending: PendingStreamStart,
    streamInfo: Ts6StreamInfo,
    ownClientId: Int?,
    generation: Long,
): Boolean {
    if (streamInfo.clientId != ownClientId || pending.nativeStreamId != streamInfo.streamId) {
        return false
    }
    return completePendingStreamStartById(pending, streamInfo.streamId, generation)
}

private data class ShutdownRequest(
    val sessionId: Long,
    val hadNativeSession: Boolean,
    val generation: Long,
    val listener: Ts3SessionListener?,
    val connectAttempt: CountDownLatch?,
    val connectThread: Thread?,
    val completion: CountDownLatch,
)

private fun awaitUninterruptibly(latch: CountDownLatch) {
    var interrupted = false
    while (true) {
        try {
            latch.await()
            break
        } catch (_: InterruptedException) {
            interrupted = true
        }
    }
    if (interrupted) Thread.currentThread().interrupt()
}

class AstrumCoreSessionClient(
    private val bindings: AstrumCoreBindings = JniAstrumCoreBindings,
) : Ts3SessionClient {
    private companion object {
        const val STREAM_START_TIMEOUT_MS = 3000L
        const val STREAM_INFO_DISCOVERY_DELAY_MS = 300L
        const val POLL_TIMEOUT_MS = 50L
        const val POLL_TIMEOUT_BACKOFF_MS = 1L
    }

    @Volatile
    private var sessionId: Long = 0L
    private val snapshotStore = SessionSnapshotStore()
    private val running = AtomicBoolean(false)
    private val sessionReady = AtomicBoolean(false)
    private val connectionGeneration = AtomicLong(0L)
    private val terminalGeneration = AtomicLong(Long.MIN_VALUE)
    private val pendingStreamStarts = ConcurrentLinkedQueue<PendingStreamStart>()
    private val streamInfoDiscovery = StreamInfoDiscovery()
    private val discoveryExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor {
        Thread(it, "astrum-stream-discovery").apply { isDaemon = true }
    }
    private val lifecycleLock = Any()

    /** The connect/disconnect operations are synchronous to their callers. */
    @Volatile
    private var connectionCompletion: CountDownLatch? = null

    @Volatile
    private var connectionThread: Thread? = null

    @Volatile
    private var connectedLatchForAttempt: CountDownLatch? = null

    @Volatile
    private var shutdownCompletion: CountDownLatch? = null

    @Volatile
    private var shutdownOwnerThread: Thread? = null

    @Volatile
    private var listener: Ts3SessionListener? = null

    @Volatile
    private var voiceSource: EncodedVoiceSource? = null

    @Volatile
    private var ownClientId: Int? = null

    private var eventThread: Thread? = null
    private var voiceThread: Thread? = null

    override fun connect(
        config: ServerConfig,
        identityMaterial: String,
        listener: Ts3SessionListener,
    ) {
        val normalized = config.normalized()
        val validationError = normalized.validationError()
        require(validationError == null) { validationError ?: "Invalid server configuration" }

        disconnect("Reconnecting")
        val (generation, statusListener) = synchronized(lifecycleLock) {
            val nextGeneration = connectionGeneration.incrementAndGet()
            this.listener = listener
            ownClientId = null
            snapshotStore.clear()
            streamInfoDiscovery.clear()
            nextGeneration to listener
        }
        val connectedLatch = CountDownLatch(1)
        val connectedEvent = AtomicBoolean(false)
        val readyEvent = AtomicBoolean(false)
        val terminalFailure = AtomicReference<Throwable?>(null)
        val attemptCompletion = CountDownLatch(1)
        synchronized(lifecycleLock) {
            connectionCompletion = attemptCompletion
            connectionThread = Thread.currentThread()
            connectedLatchForAttempt = connectedLatch
        }
        sessionReady.set(false)
        statusListener.onStatusChanged(ConnectionStatus(ConnectionPhase.CONNECTING))

        try {
            System.err.println("ASTRUM_KOTLIN: Chamando AstrumCoreNative.connectWithIdentity(${normalized.host}, ${normalized.port}, ${normalized.nickname})...")
            val sid = bindings.connectWithIdentity(
                normalized.host,
                normalized.port,
                normalized.nickname,
                identityMaterial,
            )
            System.err.println("ASTRUM_KOTLIN: AstrumCoreNative.connectWithIdentity retornou sessionId=$sid")
            if (sid == 0L) {
                val errStatus = ConnectionStatus(
                    ConnectionPhase.ERROR,
                    "Falha ao conectar via AstrumCore usando a identidade TS3 persistida",
                    retryable = true,
                )
                if (isAttemptGenerationCurrent(generation)) listener.onStatusChanged(errStatus)
                throw IOException(
                    "Falha ao conectar via AstrumCore usando a identidade TS3 persistida; o material não foi regenerado",
                )
            }

            val accepted = synchronized(lifecycleLock) {
                if (connectionGeneration.get() != generation) {
                    false
                } else {
                    sessionId = sid
                    running.set(true)
                    true
                }
            }
            if (!accepted) {
                runCatching { bindings.disconnectWithReason(sid, "Stale connection") }
                return
            }
            System.err.println("ASTRUM_KOTLIN: Iniciando threads de evento e envio de voz para session=$sid...")

            // Loop de eventos recebidos do Rust via JNI
            val events = Thread({
                while (running.get() && connectionGeneration.get() == generation) {
                    if (!running.get()) break
                    when (val poll = try {
                        bindings.pollEvent(sid, POLL_TIMEOUT_MS)
                    } catch (error: Throwable) {
                        NativePollResult.Error(error)
                    }) {
                        NativePollResult.Timeout -> {
                            // A test double (and a misbehaving native implementation) may
                            // return immediately. Keep timeout distinct from close/error and
                            // prevent that from becoming a CPU-burning busy spin.
                            try {
                                Thread.sleep(POLL_TIMEOUT_BACKOFF_MS)
                            } catch (_: InterruptedException) {
                                break
                            }
                        }

                        is NativePollResult.Event -> try {
                            handleJsonEvent(poll.json, connectedLatch, connectedEvent, readyEvent, generation, terminalFailure)
                        } catch (e: JSONException) {
                            val failure = IOException("AstrumCore event JSON is invalid", e)
                            terminalFailure.compareAndSet(null, failure)
                            terminateFromNative(generation, ConnectionStatus(
                                ConnectionPhase.ERROR,
                                failure.message,
                                retryable = true,
                            ), connectedLatch)
                            break
                        }

                        NativePollResult.ReceiverClosed -> {
                            val failure = IOException("AstrumCore event receiver closed")
                            terminalFailure.compareAndSet(null, failure)
                            terminateFromNative(generation, ConnectionStatus(
                                ConnectionPhase.ERROR,
                                failure.message,
                                retryable = true,
                                disconnectResult = DisconnectResult.Failure,
                            ), connectedLatch)
                            break
                        }

                        NativePollResult.SessionClosed -> {
                            val failure = IOException("AstrumCore session is missing")
                            terminalFailure.compareAndSet(null, failure)
                            terminateFromNative(generation, ConnectionStatus(
                                ConnectionPhase.ERROR,
                                failure.message,
                                retryable = true,
                                disconnectResult = DisconnectResult.Failure,
                            ), connectedLatch)
                            break
                        }

                        is NativePollResult.Error -> {
                            val failure = IOException("AstrumCore pollEvent failed", poll.cause)
                            terminalFailure.compareAndSet(null, failure)
                            terminateFromNative(generation, ConnectionStatus(
                                ConnectionPhase.ERROR,
                                failure.message,
                                retryable = true,
                            ), connectedLatch)
                            break
                        }
                    }
                }
            }, "astrum-events").apply {
                isDaemon = true
                start()
            }
            eventThread = events

            // Loop de envio de áudio do microfone para o Rust via JNI
            val voiceTx = Thread({
                var sendFailureLogged = false
                while (running.get() && connectionGeneration.get() == generation) {
                    val source = voiceSource
                    if (sessionReady.get() && source != null && source.isReady()) {
                        val frame = source.pollEncodedFrame()
                        if (frame != null && frame.isNotEmpty()) {
                            val sent = try {
                                bindings.sendVoice(sid, 4 /* OPUS_VOICE */, frame)
                            } catch (error: Throwable) {
                                if (!sendFailureLogged) {
                                    System.err.println(
                                        "AstrumCoreSessionClient: sendVoice rejeitou frame: ${error.message}",
                                    )
                                    sendFailureLogged = true
                                }
                                false
                            }
                            if (!sent) {
                                if (!sendFailureLogged) {
                                    System.err.println(
                                        "AstrumCoreSessionClient: sendVoice retornou false; descartando frame",
                                    )
                                    sendFailureLogged = true
                                }
                                // sendVoice is a per-frame JNI operation. A rejection does
                                // not confirm that the session ended; the event loop remains
                                // authoritative for terminal connection state. Drop this
                                // frame and keep the voice lifecycle alive for a transient
                                // native/send failure.
                                try {
                                    Thread.sleep(15)
                                } catch (ie: InterruptedException) {
                                    break
                                }
                                continue
                            }
                            sendFailureLogged = false
                            try {
                                Thread.sleep(15)
                            } catch (ie: InterruptedException) {
                                break
                            }
                            continue
                        }
                    }
                    try {
                        Thread.sleep(5)
                    } catch (ie: InterruptedException) {
                        break
                    }
                }
            }, "astrum-voice-tx").apply {
                isDaemon = true
                start()
            }
            voiceThread = voiceTx

            // Public CONNECTED is emitted only after handleJsonEvent receives Ready.
            val connected = try {
                connectedLatch.await(3000, TimeUnit.MILLISECONDS)
            } catch (ignored: InterruptedException) {
                Thread.currentThread().interrupt()
                false
            }
            if (!connected || !connectedEvent.get() || !sessionReady.get() || !isGenerationCurrent(generation)) {
                val failure = terminalFailure.get() ?: IOException("Tempo limite aguardando evento Ready nativo")
                if (terminalFailure.get() == null && isAttemptGenerationCurrent(generation)) listener.onStatusChanged(
                    ConnectionStatus(ConnectionPhase.ERROR, failure.message, retryable = true),
                )
                if (isGenerationCurrent(generation) || sessionId != 0L || running.get()) {
                    disconnect("Connection confirmation timed out")
                }
                throw failure
            }
        } finally {
            synchronized(lifecycleLock) {
                if (connectedLatchForAttempt === connectedLatch) connectedLatchForAttempt = null
                if (connectionCompletion === attemptCompletion) connectionCompletion = null
                if (connectionThread === Thread.currentThread()) connectionThread = null
            }
            attemptCompletion.countDown()
        }
    }

    private fun handleJsonEvent(
        json: String,
        connectedLatch: CountDownLatch,
        connectedEvent: AtomicBoolean,
        readyEvent: AtomicBoolean,
        generation: Long,
        terminalFailure: AtomicReference<Throwable?>,
    ) {
        if (!isGenerationCurrent(generation)) return
            val obj = JSONObject(json)
            val type = obj.getString("type")
            System.err.println("ASTRUM_KOTLIN: <- Evento recebido do Rust: $type")

            when (type) {
            "Connected" -> {
                val data = obj.getJSONObject("data")
                val id = data.getInt("own_client_id")
                synchronized(lifecycleLock) {
                    if (!isGenerationCurrentLocked(generation)) return
                    ownClientId = id
                    connectedEvent.set(true)
                }
                // Connected only identifies the native transport. It is not public
                // connectivity: the server snapshot is usable only after Ready.
            }

            "Ready" -> {
                if (!connectedEvent.get() || !readyEvent.compareAndSet(false, true)) return
                sessionReady.set(true)
                connectedLatch.countDown()
                listenerForGeneration(generation)?.onStatusChanged(ConnectionStatus(ConnectionPhase.CONNECTED))
                publishSnapshot(generation)
                scheduleStreamInfoDiscovery(generation)
            }

            "ChannelListReceived" -> {
                val array = obj.getJSONArray("data")
                val channels = mutableListOf<Ts3Channel>()
                for (i in 0 until array.length()) {
                    val cObj = array.getJSONObject(i)
                    val cid = cObj.getInt("id")
                    val parentId = cObj.optInt("parent_id", cObj.optInt("cpid", 0))
                    val order = cObj.optInt("order", cObj.optInt("channel_order", 0))
                    val name = cObj.getString("name")
                    channels += Ts3Channel(
                        id = cid,
                        parentId = parentId,
                        orderAfterId = order,
                        name = name,
                        clientCount = 0,
                        hasPassword = cObj.optBoolean("flag_password", false),
                        isDefault = cObj.optBoolean("flag_default", false),
                        topic = cObj.optString("topic", ""),
                        codec = cObj.optInt("codec", 0),
                        codecQuality = cObj.optInt("codec_quality", 0),
                        codecLatencyFactor = cObj.optInt("codec_latency_factor", 0),
                        maxClients = cObj.optInt("max_clients", 0),
                        maxFamilyClients = cObj.optInt("max_family_clients", 0),
                        isPermanent = cObj.optBoolean("flag_permanent", false),
                        isSemiPermanent = cObj.optBoolean("flag_semi_permanent", false),
                        maxClientsUnlimited = cObj.optBoolean("flag_maxclients_unlimited", false),
                        maxFamilyClientsUnlimited = cObj.optBoolean("flag_maxfamilyclients_unlimited", false),
                        areSubscribed = cObj.optBoolean("flag_are_subscribed", false),
                        unsubscribable = cObj.optBoolean("flag_unsubscribable", false),
                        neededTalkPower = cObj.optInt("needed_talk_power", 0),
                        iconId = cObj.optLong("icon_id", 0L),
                        secondsEmpty = cObj.optLong("seconds_empty", 0L),
                        bannerGfxUrl = cObj.optString("banner_gfx_url", ""),
                        bannerMode = cObj.optInt("banner_mode", 0),
                    )
                }
                System.err.println(
                    "CHANNEL_TOPOLOGY count=${channels.size} channels=" +
                        channels.joinToString(prefix = "[", postfix = "]") { channel ->
                            val safeName = channel.name.replace('\r', ' ').replace('\n', ' ')
                            "{id=${channel.id},parentId=${channel.parentId},order=${channel.orderAfterId},name=$safeName}"
                        },
                )
                snapshotStore.replaceChannels(channels)
                publishSnapshot(generation)
            }

            "ParticipantListReceived" -> {
                val array = obj.getJSONArray("data")
                val participants = List(array.length()) { index ->
                    array.getJSONObject(index).toParticipant()
                }
                snapshotStore.replaceParticipants(participants)
                publishSnapshot(generation)
                scheduleStreamInfoDiscovery(generation)
            }

            "ParticipantJoined" -> {
                val pObj = obj.getJSONObject("data")
                val participant = pObj.toParticipant()
                snapshotStore.putParticipant(participant)
                publishSnapshot(generation)
                if (participant.id != ownClientId) {
                    scheduleStreamInfoDiscovery(generation, participant)
                }
            }

            "ParticipantUpdated" -> {
                val pObj = obj.getJSONObject("data")
                val participant = pObj.toParticipant()
                val isNew = snapshotStore.putParticipant(participant)
                publishSnapshot(generation)
                if (isNew && participant.id != ownClientId) {
                    scheduleStreamInfoDiscovery(generation, participant)
                }
            }

            "ParticipantMoved" -> {
                val mObj = obj.getJSONObject("data")
                val clid = mObj.getInt("client_id")
                val cid = mObj.getInt("channel_id")
                snapshotStore.updateOrInsertParticipant(clid) { existing ->
                    existing?.copy(channelId = cid)
                        ?: placeholderParticipant(clid, cid, isTalking = false)
                }
                publishSnapshot(generation)
                if (clid != ownClientId) {
                    snapshotStore.participant(clid)?.let {
                        scheduleStreamInfoDiscovery(generation, it)
                    }
                }
            }

            "ParticipantLeft" -> {
                val lObj = obj.getJSONObject("data")
                val clid = lObj.getInt("client_id")
                snapshotStore.removeParticipant(clid)
                streamInfoDiscovery.removeClient(clid)
                publishSnapshot(generation)
            }

            "ParticipantTalking" -> {
                val tObj = obj.getJSONObject("data")
                val clid = tObj.getInt("client_id")
                val isTalking = tObj.getBoolean("is_talking")
                snapshotStore.updateOrInsertParticipant(clid) { existing ->
                    existing?.copy(isTalking = isTalking)
                        ?: placeholderParticipant(clid, channelId = 0, isTalking = isTalking)
                }
                publishSnapshot(generation)
            }

            "VoiceReceived" -> {
                val vObj = obj.getJSONObject("data")
                val clid = vObj.getInt("client_id")
                val packetId = vObj.optInt("packet_id", 0)
                val codecInt = vObj.getInt("codec")
                val dataArr = vObj.getJSONArray("data")
                val bytes = ByteArray(dataArr.length()) { i -> dataArr.getInt(i).toByte() }
                val codec = if (codecInt == 5) VoiceCodec.OPUS_MUSIC else VoiceCodec.OPUS_VOICE
                if (!sessionReady.get()) return
                listenerForGeneration(generation)?.onVoiceFrame(
                    VoiceFrame(
                        clientId = clid,
                        packetId = packetId,
                        codec = codec,
                        encodedData = bytes,
                        isWhisper = false,
                    ),
                )
            }

            "StreamStarted", "StreamUpdated", "StreamInfoReceived" -> {
                val streamInfo = obj.optJSONObject("data")?.toTs6StreamInfo() ?: return
                completePendingStreamStart(streamInfo, generation)
                snapshotStore.putStream(streamInfo)
                publishSnapshot(generation)
                if (type == "StreamStarted") {
                    listenerForGeneration(generation)?.onStreamStarted(streamInfo)
                }
            }

            "StreamStopped" -> {
                val sObj = obj.getJSONObject("data")
                val streamId = sObj.getString("stream_id")
                val clid = sObj.getInt("client_id")
                snapshotStore.removeStream(streamId)
                publishSnapshot(generation)
                listenerForGeneration(generation)?.onStreamStopped(streamId, clid)
            }

            "StreamSignalingReceived" -> {
                val sObj = obj.getJSONObject("data")
                val streamId = sObj.getString("stream_id")
                val senderId = sObj.getInt("sender_id")
                val payload = sObj.getString("payload")
                listenerForGeneration(generation)?.onStreamSignaling(Ts6StreamSignaling(streamId, senderId, payload))
            }

            "StreamJoinRequested" -> {
                val sObj = obj.getJSONObject("data")
                val streamId = sObj.getString("stream_id")
                val requesterId = sObj.getInt("requester_id")
                listenerForGeneration(generation)?.onStreamJoinRequested(streamId, requesterId)
            }

            "StreamJoinResponseReceived" -> {
                val sObj = obj.getJSONObject("data")
                val streamId = sObj.getString("stream_id")
                val responderId = sObj.getInt("responder_id")
                val allow = sObj.getBoolean("allow")
                if (allow) {
                    val offer = extractSdp(sObj, "offer", "sdp")
                        ?: sObj.optJSONObject("args")?.let { extractSdp(it, "offer", "sdp") }
                    listenerForGeneration(generation)?.onStreamClientJoined(streamId, responderId, offer)
                } else {
                    listenerForGeneration(generation)?.onStreamClientLeft(streamId, responderId)
                }
            }

            "Disconnected" -> {
                val dObj = obj.getJSONObject("data")
                val reason = dObj.optString("reason", "Disconnected")
                val terminalListener = synchronized(lifecycleLock) {
                    if (!isGenerationCurrentLocked(generation)) return
                    running.set(false)
                    sessionReady.set(false)
                    sessionId = 0L
                    ownClientId = null
                    snapshotStore.clear()
                    streamInfoDiscovery.clear()
                    listener
                }
                failPendingStreamStarts(IllegalStateException(reason))
                connectedLatch.countDown()
                terminalFailure.compareAndSet(null, IOException(reason))
                emitTerminalOnce(
                    generation,
                    reason,
                    terminalListener,
                    ConnectionStatus(
                        ConnectionPhase.DISCONNECTED,
                        reason,
                        disconnectResult = DisconnectResult.Confirmed,
                    ),
                )
            }
            }
    }

    private fun terminateFromNative(
        generation: Long,
        terminalStatus: ConnectionStatus,
        connectedLatch: CountDownLatch,
    ) {
        val terminalListener = synchronized(lifecycleLock) {
            if (!isGenerationCurrentLocked(generation)) return
            running.set(false)
            sessionReady.set(false)
            sessionId = 0L
            ownClientId = null
            snapshotStore.clear()
            streamInfoDiscovery.clear()
            listener
        }
        connectedLatch.countDown()
        failPendingStreamStarts(IllegalStateException(terminalStatus.detail ?: "Native session ended"))
        emitTerminalOnce(generation, terminalStatus.detail ?: "Native session ended", terminalListener, terminalStatus)
    }

    private fun publishSnapshot(generation: Long? = null) {
        val (callback, snap) = synchronized(lifecycleLock) {
            if (generation != null && !isGenerationCurrentLocked(generation)) return
            if (!sessionReady.get()) return
            val snapshot = snapshotStore.snapshot().copy(ownClientId = ownClientId)
            listener to snapshot
        }
        callback?.onSnapshotChanged(snap)
    }

    private fun scheduleStreamInfoDiscovery(
        generation: Long,
        participant: Ts3Participant? = null,
    ) {
        val targets = participant?.let(::listOf)
            ?: snapshotStore.snapshot().participants
        val ownId = ownClientId
        if (ownId == null || !sessionReady.get()) return
        for (target in targets) {
            if (target.id == ownId) continue
            val clientId = target.id
            val channelId = target.channelId
            if (!streamInfoDiscovery.shouldRequest(generation, clientId, channelId)) continue
            discoveryExecutor.schedule({
                if (!isGenerationCurrent(generation)) return@schedule
                val current = snapshotStore.participant(clientId)
                if (current == null || current.channelId != channelId || current.id == ownClientId) return@schedule
                val sid = sessionId
                if (sid == 0L || !sessionReady.get()) return@schedule
                runCatching {
                    bindings.requestStreamInfo(sid, clientId)
                }.onFailure { error ->
                    System.err.println(
                        "AstrumCoreSessionClient: discovery falhou para client $clientId: ${error.message}",
                    )
                }
            }, STREAM_INFO_DISCOVERY_DELAY_MS, TimeUnit.MILLISECONDS)
        }
    }

    override fun setVoiceSource(source: EncodedVoiceSource?) {
        this.voiceSource = source
    }

    override fun joinChannel(channelId: Int, password: String) {
        val sid = sessionId
        if (sid != 0L && sessionReady.get()) {
            val joined = bindings.joinChannel(sid, channelId.toLong(), password)
            check(joined) {
                "AstrumCoreNative.joinChannel failed for channel $channelId"
            }
            scheduleStreamInfoDiscovery(connectionGeneration.get())
        }
    }

    override fun disconnect(reason: String) {
        disconnectInternal(reason, null)
    }

    private fun disconnectInternal(reason: String, terminalStatusOverride: ConnectionStatus?) {
        val shutdown = synchronized(lifecycleLock) {
            shutdownCompletion?.let {
                if (Thread.currentThread() === shutdownOwnerThread) return@synchronized null to null
                return@synchronized null to it
            }

            val connectAttempt = connectionCompletion
            val currentSessionId = sessionId
            val hadNativeSession = currentSessionId != 0L || running.get()
            if (!hadNativeSession && connectAttempt == null) return@synchronized null to null

            val nextGeneration = connectionGeneration.incrementAndGet()
            running.set(false)
            sessionReady.set(false)
            // Invalidate these before invoking native code so no new operation can use this session.
            sessionId = 0L
            connectedLatchForAttempt?.countDown()
            val completion = CountDownLatch(1)
            shutdownCompletion = completion
            shutdownOwnerThread = Thread.currentThread()
            ShutdownRequest(
                sessionId = currentSessionId,
                hadNativeSession = hadNativeSession,
                generation = nextGeneration,
                listener = listener,
                connectAttempt = connectAttempt,
                connectThread = connectionThread,
                completion = completion,
            ) to null
        }.let { (request, existing) ->
            if (existing != null) {
                awaitUninterruptibly(existing)
                return
            }
            request ?: return
        }

        eventThread?.interrupt()
        voiceThread?.interrupt()
        eventThread = null
        voiceThread = null
        if (shutdown.hadNativeSession) {
            shutdown.listener?.onStatusChanged(
                ConnectionStatus(ConnectionPhase.DISCONNECTING, detail = reason),
            )
        }

        var disconnectResult: DisconnectResult? = null
        try {
            // A connect in progress owns the session ID until connectWithIdentity returns.
            // Waiting here makes close() a real lifecycle barrier instead of a fire-and-forget.
            disconnectResult = if (shutdown.sessionId != 0L) {
                val result = runCatching {
                    bindings.disconnectWithReason(shutdown.sessionId, reason)
                }.getOrElse { error ->
                    System.err.println("AstrumCoreSessionClient: disconnect JNI falhou: ${error.message}")
                    -1
                }
                when (result) {
                    1 -> System.err.println("AstrumCoreSessionClient: disconnect confirmado pelo servidor")
                    0 -> System.err.println("AstrumCoreSessionClient: timeout aguardando disconnect do servidor")
                    else -> System.err.println("AstrumCoreSessionClient: disconnect falhou")
                }
                when (result) {
                    1 -> DisconnectResult.Confirmed
                    0 -> DisconnectResult.TimedOut
                    else -> DisconnectResult.Failure
                }
            } else if (shutdown.hadNativeSession) {
                DisconnectResult.Failure
            } else null
            if (shutdown.connectThread !== Thread.currentThread()) {
                shutdown.connectAttempt?.let(::awaitUninterruptibly)
            }
        } finally {
            synchronized(lifecycleLock) {
                if (connectionGeneration.get() == shutdown.generation) {
                    ownClientId = null
                    snapshotStore.clear()
                    streamInfoDiscovery.clear()
                }
                if (shutdownCompletion === shutdown.completion) {
                    shutdownCompletion = null
                    shutdownOwnerThread = null
                }
            }
            failPendingStreamStarts(IllegalStateException(reason))
            // Publish completion before the terminal callback: callbacks may reconnect/reenter.
            shutdown.completion.countDown()
            if (shutdown.hadNativeSession) {
                val status = terminalStatusOverride?.let { override ->
                    if (override.disconnectResult == null && disconnectResult != null) {
                        override.copy(disconnectResult = disconnectResult)
                    } else {
                        override
                    }
                } ?: disconnectResult?.let { result ->
                    ConnectionStatus(
                        phase = if (result == DisconnectResult.Confirmed) {
                            ConnectionPhase.DISCONNECTED
                        } else {
                            ConnectionPhase.ERROR
                        },
                        detail = reason,
                        retryable = result != DisconnectResult.Confirmed,
                        disconnectResult = result,
                    )
                }
                emitTerminalOnce(shutdown.generation, reason, shutdown.listener, status)
            }
        }
    }

    override fun startStream(
        type: StreamType,
        width: Int,
        height: Int,
        fps: Int,
        bitrateKbps: Int,
        hasAudio: Boolean,
    ): String {
        val (sid, pending) = synchronized(lifecycleLock) {
            val currentSessionId = sessionId
            check(currentSessionId != 0L && running.get() && sessionReady.get()) {
                "Cannot start a stream without an active session"
            }
            val request = PendingStreamStart(connectionGeneration.get(), type)
            pendingStreamStarts.add(request)
            currentSessionId to request
        }
        val typeInt = when (type) {
            StreamType.CAMERA -> 1
            StreamType.SCREEN -> 2
            StreamType.WINDOW -> 3
            else -> 2
        }
        try {
            val nativeStreamId = bindings.setupStream(
                sid,
                when (type) {
                    StreamType.CAMERA -> "Camera"
                    StreamType.SCREEN -> "Screen"
                    StreamType.WINDOW -> "Window"
                    StreamType.UNKNOWN -> "Screen"
                },
                typeInt,
                width,
                height,
                fps,
                bitrateKbps,
                hasAudio,
            )
            check(!nativeStreamId.isNullOrBlank()) {
                "AstrumCoreNative.setupStream failed without a stream ID"
            }
            pending.nativeStreamId = nativeStreamId
            completePendingStreamStart(pending, nativeStreamId, pending.generation)
        } catch (error: Throwable) {
            pendingStreamStarts.remove(pending)
            throw error
        }
        val completed = try {
            pending.completed.await(STREAM_START_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (interrupted: InterruptedException) {
            pendingStreamStarts.remove(pending)
            Thread.currentThread().interrupt()
            throw interrupted
        }
        if (!completed) {
            pendingStreamStarts.remove(pending)
            throw TimeoutException("Timed out waiting for confirmed stream start")
        }
        pending.failure?.let { throw it }
        return pending.streamId
            ?.takeIf { it.isNotBlank() }
            ?: throw TimeoutException("Stream start completed without a confirmed stream ID")
    }

    private fun completePendingStreamStart(
        pending: PendingStreamStart,
        streamId: String,
        generation: Long,
    ) {
        if (pending.generation != generation || streamId.isBlank()) return
        if (pendingStreamStarts.remove(pending)) {
            completePendingStreamStartById(pending, streamId, generation)
        }
    }

    private fun completePendingStreamStart(streamInfo: Ts6StreamInfo, generation: Long) {
        if (streamInfo.clientId != ownClientId) return
        val streamId = streamInfo.streamId
        if (streamId.isBlank()) return
        val pending = pendingStreamStarts.firstOrNull {
            it.generation == generation &&
                it.nativeStreamId == streamId
        } ?: return
        if (pendingStreamStarts.remove(pending)) {
            completePendingStreamStartForStreamInfo(pending, streamInfo, ownClientId, generation)
        }
    }

    private fun failPendingStreamStarts(failure: Throwable) {
        while (true) {
            val pending = pendingStreamStarts.poll() ?: break
            pending.failure = failure
            pending.completed.countDown()
        }
    }

    override fun stopStream(streamId: String) {
        val sid = sessionId
        if (sid != 0L && sessionReady.get()) {
            check(bindings.stopStream(sid, streamId)) {
                "AstrumCoreNative.stopStream failed for stream $streamId"
            }
        }
    }

    override fun requestJoinStream(targetClientId: Int, streamId: String) {
        val sid = sessionId
        if (sid != 0L && sessionReady.get()) {
            check(bindings.requestJoinStream(sid, targetClientId, streamId)) {
                "AstrumCoreNative.requestJoinStream failed for stream $streamId"
            }
        }
    }

    override fun leaveStream(targetClientId: Int, streamId: String) {
        val sid = sessionId
        if (sid != 0L && sessionReady.get()) {
            check(bindings.leaveStream(sid, targetClientId, streamId)) {
                "AstrumCoreNative.leaveStream failed for stream $streamId"
            }
        }
    }

    override fun respondJoinStreamRequest(
        targetClientId: Int,
        streamId: String,
        allow: Boolean,
        offer: String?,
    ) {
        val sid = sessionId
        if (sid != 0L && sessionReady.get()) {
            check(bindings.respondJoinStream(sid, targetClientId, streamId, allow, offer)) {
                "AstrumCoreNative.respondJoinStream failed for stream $streamId"
            }
        }
    }

    override fun sendStreamSignaling(targetClientId: Int, streamId: String, payload: String) {
        val sid = sessionId
        if (sid != 0L && sessionReady.get()) {
            check(bindings.sendStreamSignaling(sid, targetClientId, streamId, payload)) {
                "AstrumCoreNative.sendStreamSignaling failed for stream $streamId"
            }
        }
    }

    override fun requestStreamInfo(targetClientId: Int) {
        val sid = sessionId
        if (sid != 0L && sessionReady.get()) {
            check(bindings.requestStreamInfo(sid, targetClientId)) {
                "AstrumCoreNative.requestStreamInfo failed for client $targetClientId"
            }
        }
    }

    private fun extractSdp(obj: JSONObject, vararg keys: String): String? {
        for (key in keys) {
            when (val value = obj.opt(key)) {
                is String -> value.takeIf { it.isNotBlank() }?.let { return it }
                is JSONObject -> {
                    extractSdp(value, "offer", "sdp")?.let { return it }
                }
            }
        }
        return null
    }

    private fun isGenerationCurrent(generation: Long): Boolean = synchronized(lifecycleLock) {
        isGenerationCurrentLocked(generation)
    }

    private fun isAttemptGenerationCurrent(generation: Long): Boolean = synchronized(lifecycleLock) {
        connectionGeneration.get() == generation
    }

    private fun listenerForGeneration(generation: Long): Ts3SessionListener? = synchronized(lifecycleLock) {
        listener?.takeIf { isGenerationCurrentLocked(generation) }
    }

    private fun isGenerationCurrentLocked(generation: Long): Boolean =
        connectionGeneration.get() == generation && running.get()

    private fun emitTerminalOnce(
        generation: Long,
        reason: String,
        terminalListener: Ts3SessionListener? = listener,
        terminalStatus: ConnectionStatus? = null,
    ) {
        val callback = synchronized(lifecycleLock) {
            if (connectionGeneration.get() != generation) return
            if (terminalGeneration.getAndSet(generation) == generation) return
            terminalListener
        }
        callback?.onStatusChanged(terminalStatus ?: ConnectionStatus(ConnectionPhase.DISCONNECTED, detail = reason))
        callback?.onSnapshotChanged(SessionSnapshot.Empty)
    }

    private fun JSONObject.toParticipant(): Ts3Participant = Ts3Participant(
        id = getInt("id"),
        channelId = optInt("channel_id", 0),
        nickname = optString("nickname", ""),
        isTalking = optBoolean("is_talking", false),
        isInputMuted = optBoolean("is_input_muted", false),
        isOutputMuted = optBoolean("is_output_muted", false),
        uniqueIdentifier = optString("unique_identifier", ""),
        clientType = optInt("client_type", 0),
        isAway = optBoolean("is_away", false),
        databaseId = optLong("database_id", 0L),
        platform = optString("platform", ""),
        version = optString("version", ""),
        description = optString("description", ""),
        isRecording = optBoolean("is_recording", false),
    )

    private fun placeholderParticipant(id: Int, channelId: Int, isTalking: Boolean) =
        Ts3Participant(
            id = id,
            channelId = channelId,
            nickname = "Cliente $id",
            isTalking = isTalking,
            isInputMuted = false,
            isOutputMuted = false,
            uniqueIdentifier = "astrum_$id",
        )

    override fun close() {
        disconnect("Closing AstrumCoreSessionClient")
        discoveryExecutor.shutdownNow()
    }
}
