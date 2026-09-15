package br.app.astrum.ts6.protocol

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONObject
import uniffi.astrum_core.MobileSession

/**
 * Experimental Ts3SessionClient backed by the exported UniFFI mobile API.
 *
 * This deliberately implements only lifecycle and Opus voice. The mobile API
 * does not export channels or streams yet; those methods throw instead of
 * pretending that an operation succeeded.
 */
class AstrumCoreUniFfiSessionClient : Ts3SessionClient {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lifecycleLock = Any()

    @Volatile
    private var mobileSession: AstrumCoreMobileSession? = null

    @Volatile
    private var listener: Ts3SessionListener? = null

    @Volatile
    private var voiceSource: EncodedVoiceSource? = null

    @Volatile
    private var eventJob: Job? = null

    @Volatile
    private var voiceJob: Job? = null

    private var generation = 0L

    override fun connect(
        config: ServerConfig,
        identityMaterial: String,
        listener: Ts3SessionListener,
    ) {
        val normalized = config.normalized()
        val validationError = normalized.validationError()
        require(validationError == null) { validationError ?: "Invalid server configuration" }
        check(normalized.password.isBlank()) {
            "UniFFI backend does not support password-protected servers; use ts3j"
        }

        disconnect("Replacing UniFFI connection")
        val token = synchronized(lifecycleLock) {
            generation += 1
            this.listener = listener
            generation
        }
        listener.onStatusChanged(ConnectionStatus(ConnectionPhase.CONNECTING))

        val session = AstrumCoreUniFfiMobileSession.create(
            AstrumCoreMobileSession.Config(
                host = normalized.host,
                port = normalized.port,
                nickname = normalized.nickname,
                identity = identityMaterial.takeIf(String::isNotBlank),
            ),
        )
        synchronized(lifecycleLock) {
            if (generation != token) {
                runBlocking { session.close("Stale UniFFI connection") }
                return
            }
            mobileSession = session
        }

        val ready = CompletableDeferred<Unit>()
        readySignals[token] = ready
        eventJob = scope.launch { eventLoop(session, token, ready) }
        try {
            runBlocking { session.connect() }
            runBlocking { withTimeout(CONNECT_TIMEOUT_MS) { ready.await() } }
        } catch (error: Throwable) {
            synchronized(lifecycleLock) {
                if (mobileSession === session) mobileSession = null
            }
            eventJob?.cancel()
            readySignals.remove(token)
            runCatching { runBlocking { session.close("UniFFI connection failed") } }
            if (isCurrent(token)) listener.onStatusChanged(
                ConnectionStatus(
                    ConnectionPhase.ERROR,
                    error.message ?: "UniFFI connection failed",
                    retryable = true,
                ),
            )
            throw error
        }

        if (!isCurrent(token)) {
            readySignals.remove(token)
            runBlocking { session.close("Stale UniFFI connection") }
            return
        }
        readySignals.remove(token)
        voiceJob = scope.launch { voiceLoop(session, token) }
    }

    override fun setVoiceSource(source: EncodedVoiceSource?) {
        voiceSource = source
    }

    override fun joinChannel(channelId: Int, password: String) {
        unsupported("channel operations")
    }

    override fun startStream(
        type: StreamType,
        width: Int,
        height: Int,
        fps: Int,
        bitrateKbps: Int,
        hasAudio: Boolean,
    ): String = unsupported("stream operations")

    override fun stopStream(streamId: String) = unsupported("stream operations")

    override fun requestJoinStream(targetClientId: Int, streamId: String) = unsupported("stream operations")

    override fun leaveStream(targetClientId: Int, streamId: String) = unsupported("stream operations")

    override fun respondJoinStreamRequest(
        targetClientId: Int,
        streamId: String,
        allow: Boolean,
        offer: String?,
    ) = unsupported("stream operations")

    override fun sendStreamSignaling(targetClientId: Int, streamId: String, payload: String) =
        unsupported("stream operations")

    override fun requestStreamInfo(targetClientId: Int) = unsupported("stream operations")

    override fun disconnect(reason: String) {
        val (session, events, voice, callback, _) = synchronized(lifecycleLock) {
            val current = mobileSession
            val currentEvents = eventJob
            val currentVoice = voiceJob
            val currentListener = listener
            generation += 1
            mobileSession = null
            eventJob = null
            voiceJob = null
            listener = null
            Tuple(current, currentEvents, currentVoice, currentListener, generation)
        }
        readySignals.entries.removeIf { it.key != generation }
        if (session == null && events == null && voice == null) return

        callback?.onStatusChanged(ConnectionStatus(ConnectionPhase.DISCONNECTING, detail = reason))
        events?.cancel()
        voice?.cancel()
        runBlocking {
            runCatching { session?.close(reason) }
            events?.cancelAndJoin()
            voice?.cancelAndJoin()
        }
        callback?.onSnapshotChanged(SessionSnapshot.Empty)
        callback?.onStatusChanged(ConnectionStatus(ConnectionPhase.DISCONNECTED, detail = reason))
    }

    override fun close() {
        disconnect("Closing UniFFI session")
        scope.cancel()
    }

    private suspend fun eventLoop(
        session: AstrumCoreMobileSession,
        token: Long,
        ready: CompletableDeferred<Unit>,
    ) {
        try {
            while (currentCoroutineContext().isActive && isCurrent(token)) {
                val event = session.nextEvent(EVENT_POLL_TIMEOUT_MS)
                if (event == null) {
                    if (session.state() == AstrumCoreMobileSession.State.CLOSED) break
                    continue
                }
                handleEvent(event, token)
                if (session.state() == AstrumCoreMobileSession.State.CLOSED) break
            }
        } catch (error: Throwable) {
            ready.completeExceptionally(error)
            if (isCurrent(token)) {
                listener?.onStatusChanged(
                    ConnectionStatus(ConnectionPhase.ERROR, error.message ?: "UniFFI event loop failed", retryable = true),
                )
            }
        }
    }

    private suspend fun voiceLoop(session: AstrumCoreMobileSession, token: Long) {
        while (currentCoroutineContext().isActive && isCurrent(token)) {
            val source = voiceSource
            val frame = if (source?.isReady() == true) source.pollEncodedFrame() else null
            if (frame != null && frame.isNotEmpty()) {
                runCatching { session.sendVoiceFrame(4, frame) }
                    .onFailure { error ->
                        if (session.state() != AstrumCoreMobileSession.State.CONNECTED) return
                        System.err.println("AstrumCoreUniFFI: voice frame rejected: ${error.message}")
                    }
                delay(15)
            } else {
                delay(5)
            }
        }
    }

    private fun handleEvent(json: String, token: Long) {
        val objectValue = JSONObject(json)
        when (objectValue.optString("type")) {
            "Connected" -> {
                val data = objectValue.optJSONObject("data")
                val ownClientId = data?.optInt("own_client_id", 0) ?: 0
                listenerFor(token)?.onSnapshotChanged(
                    SessionSnapshot(ownClientId = ownClientId.takeIf { it != 0 }),
                )
            }

            "Ready" -> {
                readyFor(token)?.complete(Unit)
                listenerFor(token)?.let { currentListener ->
                    currentListener.onStatusChanged(ConnectionStatus(ConnectionPhase.CONNECTED))
                    currentListener.onSnapshotChanged(SessionSnapshot.Empty)
                }
            }

            "VoiceReceived" -> {
                val data = objectValue.optJSONObject("data") ?: return
                val bytes = data.optJSONArray("data") ?: return
                val codec = when (data.optInt("codec", 4)) {
                    5 -> VoiceCodec.OPUS_MUSIC
                    4 -> VoiceCodec.OPUS_VOICE
                    else -> return
                }
                listenerFor(token)?.onVoiceFrame(
                    VoiceFrame(
                        clientId = data.optInt("client_id", 0),
                        packetId = data.optInt("packet_id", 0),
                        codec = codec,
                        encodedData = ByteArray(bytes.length()) { index -> bytes.optInt(index).toByte() },
                        isWhisper = false,
                    ),
                )
            }

            "Disconnected" -> {
                val reason = objectValue.optJSONObject("data")?.optString("reason", "Disconnected")
                    ?: "Disconnected"
                listenerFor(token)?.let {
                    it.onSnapshotChanged(SessionSnapshot.Empty)
                    it.onStatusChanged(ConnectionStatus(ConnectionPhase.DISCONNECTED, detail = reason))
                }
            }
        }
    }

    private fun listenerFor(token: Long): Ts3SessionListener? = synchronized(lifecycleLock) {
        listener?.takeIf { generation == token && mobileSession != null }
    }

    private fun readyFor(token: Long): CompletableDeferred<Unit>? =
        readySignals[token]

    private fun isCurrent(token: Long): Boolean = synchronized(lifecycleLock) {
        generation == token && mobileSession != null
    }

    private fun unsupported(capability: String): Nothing = throw UnsupportedOperationException(
        "UniFFI backend does not support $capability; AstrumCoreMobileSession has no such API yet",
    )

    private data class Tuple(
        val session: AstrumCoreMobileSession?,
        val events: Job?,
        val voice: Job?,
        val listener: Ts3SessionListener?,
        val token: Long,
    )

    private companion object {
        const val EVENT_POLL_TIMEOUT_MS = 100L
        const val CONNECT_TIMEOUT_MS = 3_000L
    }

    private val readySignals = java.util.concurrent.ConcurrentHashMap<Long, CompletableDeferred<Unit>>()
}
