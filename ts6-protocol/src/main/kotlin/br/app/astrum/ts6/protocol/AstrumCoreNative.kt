package br.app.astrum.ts6.protocol

object AstrumCoreNative {
    init {
        try {
            System.loadLibrary("astrum_core")
        } catch (e: UnsatisfiedLinkError) {
            throw IllegalStateException("Failed to load libastrum_core", e)
        }
    }

    external fun connectWithIdentity(host: String, port: Int, nickname: String, identityMaterial: String): Long
    external fun generateIdentity(securityLevel: Int = 10): String?
    external fun pollEvent(sessionId: Long, timeoutMs: Long): String?
    external fun sendVoice(sessionId: Long, codec: Int, data: ByteArray): Boolean
    external fun joinChannel(sessionId: Long, channelId: Long, password: String): Boolean
    external fun setupStream(
        sessionId: Long,
        name: String,
        streamType: Int,
        width: Int,
        height: Int,
        fps: Int,
        bitrateKbps: Int,
        hasAudio: Boolean,
    ): String?
    external fun stopStream(sessionId: Long, streamId: String): Boolean
    external fun leaveStream(sessionId: Long, targetClientId: Int, streamId: String): Boolean
    external fun requestStreamInfo(sessionId: Long, targetClientId: Int): Boolean
    external fun sendStreamSignaling(sessionId: Long, targetClientId: Int, streamId: String, json: String): Boolean
    external fun requestJoinStream(sessionId: Long, targetClientId: Int, streamId: String): Boolean
    external fun respondJoinStream(sessionId: Long, targetClientId: Int, streamId: String, allow: Boolean, offer: String?): Boolean
    external fun disconnect(sessionId: Long): Boolean
    /** Returns 1 for confirmed, 0 for bounded timeout, and -1 for failure. */
    external fun disconnectWithReason(sessionId: Long, reason: String): Int
}
