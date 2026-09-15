package br.app.astrum.ts6.protocol

/** Result of one bounded wait on the native event queue. */
sealed interface NativePollResult {
    data class Event(val json: String) : NativePollResult
    data object Timeout : NativePollResult
    data object Closed : NativePollResult
    data class Error(val cause: Throwable) : NativePollResult
}

/** Result reported by the native disconnect handshake. */
enum class DisconnectResult {
    Confirmed,
    TimedOut,
    Failure,
}

/**
 * Native operations used by [AstrumCoreSessionClient].
 *
 * Keeping this boundary injectable makes the passive bridge testable without a
 * device (or a libastrum_core.so).  Methods which are not needed by a test
 * have conservative defaults; the JNI implementation overrides every one.
 */
interface AstrumCoreBindings {
    fun connectWithIdentity(host: String, port: Int, nickname: String, identityMaterial: String): Long =
        error("connectWithIdentity is not implemented")

    fun pollEvent(sessionId: Long, timeoutMs: Long): NativePollResult =
        NativePollResult.Closed

    fun sendVoice(sessionId: Long, codec: Int, data: ByteArray): Boolean = false

    fun joinChannel(sessionId: Long, channelId: Long, password: String): Boolean = false

    fun setupStream(
        sessionId: Long,
        name: String,
        streamType: Int,
        width: Int,
        height: Int,
        fps: Int,
        bitrateKbps: Int,
        hasAudio: Boolean,
    ): String? = null

    fun stopStream(sessionId: Long, streamId: String): Boolean = false
    fun leaveStream(sessionId: Long, targetClientId: Int, streamId: String): Boolean = false
    fun requestStreamInfo(sessionId: Long, targetClientId: Int): Boolean = false
    fun sendStreamSignaling(sessionId: Long, targetClientId: Int, streamId: String, json: String): Boolean = false
    fun requestJoinStream(sessionId: Long, targetClientId: Int, streamId: String): Boolean = false
    fun respondJoinStream(
        sessionId: Long,
        targetClientId: Int,
        streamId: String,
        allow: Boolean,
        offer: String?,
    ): Boolean = false

    fun disconnectWithReason(sessionId: Long, reason: String): Int = -1
}

/** Production adapter. Loading the native object remains lazy until this is used. */
internal object JniAstrumCoreBindings : AstrumCoreBindings {
    override fun connectWithIdentity(host: String, port: Int, nickname: String, identityMaterial: String): Long =
        AstrumCoreNative.connectWithIdentity(host, port, nickname, identityMaterial)

    override fun pollEvent(sessionId: Long, timeoutMs: Long): NativePollResult = try {
        AstrumCoreNative.pollEvent(sessionId, timeoutMs)?.let(NativePollResult::Event)
            ?: NativePollResult.Timeout
    } catch (error: Throwable) {
        NativePollResult.Error(error)
    }

    override fun sendVoice(sessionId: Long, codec: Int, data: ByteArray): Boolean =
        AstrumCoreNative.sendVoice(sessionId, codec, data)

    override fun joinChannel(sessionId: Long, channelId: Long, password: String): Boolean =
        AstrumCoreNative.joinChannel(sessionId, channelId, password)

    override fun setupStream(
        sessionId: Long,
        name: String,
        streamType: Int,
        width: Int,
        height: Int,
        fps: Int,
        bitrateKbps: Int,
        hasAudio: Boolean,
    ): String? = AstrumCoreNative.setupStream(
        sessionId,
        name,
        streamType,
        width,
        height,
        fps,
        bitrateKbps,
        hasAudio,
    )

    override fun stopStream(sessionId: Long, streamId: String): Boolean = AstrumCoreNative.stopStream(sessionId, streamId)
    override fun leaveStream(sessionId: Long, targetClientId: Int, streamId: String): Boolean =
        AstrumCoreNative.leaveStream(sessionId, targetClientId, streamId)
    override fun requestStreamInfo(sessionId: Long, targetClientId: Int): Boolean =
        AstrumCoreNative.requestStreamInfo(sessionId, targetClientId)
    override fun sendStreamSignaling(sessionId: Long, targetClientId: Int, streamId: String, json: String): Boolean =
        AstrumCoreNative.sendStreamSignaling(sessionId, targetClientId, streamId, json)
    override fun requestJoinStream(sessionId: Long, targetClientId: Int, streamId: String): Boolean =
        AstrumCoreNative.requestJoinStream(sessionId, targetClientId, streamId)
    override fun respondJoinStream(
        sessionId: Long,
        targetClientId: Int,
        streamId: String,
        allow: Boolean,
        offer: String?,
    ): Boolean = AstrumCoreNative.respondJoinStream(sessionId, targetClientId, streamId, allow, offer)

    override fun disconnectWithReason(sessionId: Long, reason: String): Int =
        AstrumCoreNative.disconnectWithReason(sessionId, reason)
}
