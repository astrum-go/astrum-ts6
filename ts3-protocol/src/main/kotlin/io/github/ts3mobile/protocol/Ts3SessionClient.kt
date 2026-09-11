package io.github.ts3mobile.protocol

interface Ts3SessionListener {
    fun onStatusChanged(status: ConnectionStatus)
    fun onSnapshotChanged(snapshot: SessionSnapshot)
    fun onVoiceFrame(frame: VoiceFrame)
    fun onStreamStarted(stream: Ts6StreamInfo) {}
    fun onStreamStopped(streamId: String, clientId: Int) {}
    fun onStreamSignaling(signaling: Ts6StreamSignaling) {}
    fun onStreamJoinRequested(streamId: String, remoteClientId: Int) {}
    fun onStreamClientJoined(streamId: String, clientId: Int) {}
    fun onStreamClientLeft(streamId: String, clientId: Int) {}
}

interface Ts3SessionClient : AutoCloseable {
    fun connect(config: ServerConfig, identityMaterial: String, listener: Ts3SessionListener)
    fun setVoiceSource(source: EncodedVoiceSource?)
    fun joinChannel(channelId: Int, password: String = "")
    fun disconnect(reason: String = "Client disconnected")
    fun startStream(type: StreamType = StreamType.CAMERA, width: Int = 1280, height: Int = 720, fps: Int = 30): String
    fun stopStream(streamId: String)
    fun requestJoinStream(targetClientId: Int, streamId: String)
    fun leaveStream(targetClientId: Int, streamId: String)
    fun respondJoinStreamRequest(targetClientId: Int, streamId: String, allow: Boolean = true, offer: String? = null)
    fun sendStreamSignaling(targetClientId: Int, streamId: String, payload: String)
    fun requestStreamInfo(targetClientId: Int)
    override fun close()
}
