package io.github.ts3mobile.app.video

import android.content.Context
import android.util.Log
import io.github.ts3mobile.protocol.StreamType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.util.concurrent.ConcurrentHashMap

/**
 * WebRTC Video Engine for TeamSpeak 6 compatibility.
 * Handles hardware-accelerated H.264/VP8 video encoding/decoding,
 * camera capture via Camera2, peer connection lifecycle, and JSEP signaling.
 */
class WebRtcManager(
    private val context: Context,
    private val sendSignalingCallback: (targetClientId: Int, streamId: String, payload: String) -> Unit,
    private val respondJoinStreamCallback: ((targetClientId: Int, streamId: String, offer: String) -> Unit)? = null,
) {
    val eglBase: EglBase = EglBase.create()

    private val peerConnectionFactory: PeerConnectionFactory

    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrackInternal: VideoTrack? = null

    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

    private val _isBroadcasting = MutableStateFlow(false)
    val isBroadcasting: StateFlow<Boolean> = _isBroadcasting.asStateFlow()

    private val _isFrontCamera = MutableStateFlow(true)
    val isFrontCamera: StateFlow<Boolean> = _isFrontCamera.asStateFlow()

    private var activeBroadcastStreamId: String? = null

    // Remote peers: key is composite "clientId:streamId"
    private val peerConnections = ConcurrentHashMap<String, PeerConnection>()
    private val pendingIceCandidates = ConcurrentHashMap<String, MutableList<IceCandidate>>()

    private val _remoteVideoTracks = MutableStateFlow<Map<String, VideoTrack>>(emptyMap())
    val remoteVideoTracks: StateFlow<Map<String, VideoTrack>> = _remoteVideoTracks.asStateFlow()

    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:turn.teamspeak.com:3478").createIceServer(),
        PeerConnection.IceServer.builder("stun:turn2.teamspeak.com:3478").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
    )

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions(),
        )

        val encoderFactory = DefaultVideoEncoderFactory(
            eglBase.eglBaseContext,
            /* enableIntelVp8Encoder = */ true,
            /* enableH264HighProfile = */ true,
        )
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }

    /**
     * Starts broadcasting camera to TeamSpeak 6 channel.
     */
    fun startCameraBroadcast(streamId: String, width: Int = 1280, height: Int = 720, fps: Int = 30) {
        if (_isBroadcasting.value) return
        activeBroadcastStreamId = streamId

        try {
            val enumerator = Camera2Enumerator(context)
            val frontDevice = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
            val backDevice = enumerator.deviceNames.firstOrNull { enumerator.isBackFacing(it) }
            val chosenDevice = frontDevice ?: backDevice ?: enumerator.deviceNames.firstOrNull()
                ?: error("No camera available on device")

            _isFrontCamera.value = enumerator.isFrontFacing(chosenDevice)

            surfaceTextureHelper = SurfaceTextureHelper.create("Ts6CameraThread", eglBase.eglBaseContext)
            videoSource = peerConnectionFactory.createVideoSource(false)

            val capturer = enumerator.createCapturer(chosenDevice, object : CameraVideoCapturer.CameraEventsHandler {
                override fun onCameraError(errorDescription: String?) {
                    Log.e(TAG, "Camera error: $errorDescription")
                }
                override fun onCameraDisconnected() {
                    Log.w(TAG, "Camera disconnected")
                }
                override fun onCameraFreezed(errorDescription: String?) {
                    Log.w(TAG, "Camera freezed: $errorDescription")
                }
                override fun onCameraOpening(cameraName: String?) {
                    Log.d(TAG, "Camera opening: $cameraName")
                }
                override fun onFirstFrameAvailable() {
                    Log.d(TAG, "First camera frame available")
                }
                override fun onCameraClosed() {
                    Log.d(TAG, "Camera closed")
                }
            })

            videoCapturer = capturer
            capturer.initialize(surfaceTextureHelper, context, videoSource?.capturerObserver)
            capturer.startCapture(width, height, fps)

            val track = peerConnectionFactory.createVideoTrack("ARDAMSv0", videoSource)
            track.setEnabled(true)
            localVideoTrackInternal = track
            _localVideoTrack.value = track
            _isBroadcasting.value = true
            Log.i(TAG, "Camera broadcast started: streamId=$streamId")
        } catch (error: Throwable) {
            Log.e(TAG, "Failed to start camera broadcast", error)
            stopCameraBroadcast()
        }
    }

    /**
     * Toggles between front and back camera.
     */
    fun switchCamera() {
        val capturer = videoCapturer ?: return
        capturer.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFront: Boolean) {
                _isFrontCamera.value = isFront
                Log.d(TAG, "Camera switched, isFront=$isFront")
            }

            override fun onCameraSwitchError(error: String?) {
                Log.e(TAG, "Failed to switch camera: $error")
            }
        })
    }

    /**
     * Updates the active broadcast stream ID when confirmed by the server.
     */
    fun updateBroadcastStreamId(streamId: String) {
        Log.i(TAG, "updateBroadcastStreamId: updating from $activeBroadcastStreamId to $streamId")
        activeBroadcastStreamId = streamId
    }

    /**
     * Stops broadcasting camera and closes active outbound peer connections.
     */
    fun stopCameraBroadcast() {
        _isBroadcasting.value = false
        activeBroadcastStreamId = null

        try {
            videoCapturer?.stopCapture()
        } catch (ignored: Throwable) {}
        videoCapturer?.dispose()
        videoCapturer = null

        localVideoTrackInternal?.dispose()
        localVideoTrackInternal = null
        _localVideoTrack.value = null

        videoSource?.dispose()
        videoSource = null

        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null

        Log.i(TAG, "Camera broadcast stopped")
    }

    /**
     * Joins and begins watching a remote stream (camera or screen).
     */
    fun watchStream(remoteClientId: Int, streamId: String) {
        val key = peerKey(remoteClientId, streamId)
        if (peerConnections.containsKey(key)) return

        Log.i(TAG, "watchStream: preparing PeerConnection to receive stream $streamId from $remoteClientId")
        val peerConnection = createPeerConnection(remoteClientId, streamId) ?: return
        peerConnections[key] = peerConnection

        try {
            peerConnection.addTransceiver(
                MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY),
            )
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to add RECV_ONLY video transceiver: ${e.message}")
        }
    }

    /**
     * Stops watching a remote stream and frees its peer connection.
     */
    fun stopWatchingStream(streamId: String) {
        val matchingKeys = peerConnections.keys().toList().filter { it.endsWith(":$streamId") }
        for (key in matchingKeys) {
            peerConnections.remove(key)?.apply {
                close()
                dispose()
            }
            pendingIceCandidates.remove(key)
        }
        val currentTracks = _remoteVideoTracks.value.toMutableMap()
        currentTracks.remove(streamId)
        _remoteVideoTracks.value = currentTracks
    }

    /**
     * Handles an incoming join request from a remote client who wants to watch our stream.
     */
    fun handleJoinRequest(requesterClientId: Int, streamId: String) {
        if (!_isBroadcasting.value) return
        if (activeBroadcastStreamId == null || activeBroadcastStreamId != streamId) {
            Log.i(TAG, "handleJoinRequest: updating activeBroadcastStreamId from $activeBroadcastStreamId to $streamId")
            activeBroadcastStreamId = streamId
        }
        val key = peerKey(requesterClientId, streamId)
        if (peerConnections.containsKey(key)) return

        val peerConnection = createPeerConnection(requesterClientId, streamId) ?: return
        peerConnections[key] = peerConnection

        val track = localVideoTrackInternal
        if (track != null) {
            peerConnection.addTrack(track, listOf("ARDAMS"))
        }

        val constraints = MediaConstraints()
        peerConnection.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc ?: return
                peerConnection.setLocalDescription(SimpleSdpObserver(), desc)

                val minifiedOffer = cleanOfferSdp(desc.description)

                // 1. Respond to joinstreamrequest via TS3 protocol command (includes minified offer):
                respondJoinStreamCallback?.invoke(requesterClientId, streamId, minifiedOffer)

                // 2. Send streamsignaling offer in standard TS6 format:
                val offerJson = JSONObject().apply {
                    put("cmd", "offer")
                    put("args", JSONObject().apply {
                        put("offer", minifiedOffer)
                    })
                }.toString()
                sendSignalingCallback(requesterClientId, streamId, offerJson)
            }
        }, constraints)
    }

    /**
     * Processes an incoming signaling message (offer, answer, candidate) from TS3 streamsignaling.
     */
    fun handleRemoteSignaling(senderClientId: Int, streamId: String, payload: String) {
        try {
            Log.i(TAG, "handleRemoteSignaling: sender=$senderClientId stream=$streamId payload=$payload")
            val key = peerKey(senderClientId, streamId)
            val trimmed = payload.trim()

            if (trimmed.startsWith("v=0")) {
                // Raw SDP offer
                processRemoteOffer(senderClientId, streamId, key, trimmed)
                return
            }

            val json = JSONObject(trimmed)
            val cmd = json.optString("cmd")
            val type = json.optString("type").ifEmpty { cmd }.lowercase()
            val args = json.optJSONObject("args")
            val hasCandidate = type == "candidate" || cmd == "iceCandidate" ||
                json.has("candidate") || json.has("iceCandidate") ||
                (args != null && (args.has("mLine") || args.has("mid") || args.has("candidate") || args.has("sdp")))

            val isOffer = cmd in listOf("joinResponse", "offer", "reconnectOffer") || type == "offer" ||
                (!hasCandidate && (json.has("offer") || (args != null && args.has("offer")) || (!peerConnections.containsKey(key) && (json.has("sdp") || (args != null && args.has("sdp"))))))

            val isAnswer = cmd == "answer" || type == "answer" ||
                (!isOffer && !hasCandidate && (json.has("answer") || (args != null && args.has("answer")) || (peerConnections.containsKey(key) && (json.has("sdp") || (args != null && args.has("sdp"))))))

            if (isOffer) {
                val sdp = args?.optString("offer")?.ifEmpty { null }
                    ?: args?.optString("sdp")?.ifEmpty { null }
                    ?: json.optString("offer").ifEmpty { null }
                    ?: json.optString("sdp")
                if (!sdp.isNullOrBlank()) {
                    processRemoteOffer(senderClientId, streamId, key, sdp)
                }
            } else if (isAnswer) {
                val sdp = args?.optString("answer")?.ifEmpty { null }
                    ?: args?.optString("sdp")?.ifEmpty { null }
                    ?: json.optString("answer").ifEmpty { null }
                    ?: json.optString("sdp")
                if (!sdp.isNullOrBlank()) {
                    val peerConnection = peerConnections[key] ?: return
                    val remoteDesc = SessionDescription(SessionDescription.Type.ANSWER, normalizeSdp(sdp))
                    peerConnection.setRemoteDescription(object : SimpleSdpObserver() {
                        override fun onSetSuccess() {
                            Log.i(TAG, "setRemoteDescription (ANSWER) succeeded for $key")
                            drainPendingIceCandidates(key, peerConnection)
                        }
                        override fun onSetFailure(error: String?) {
                            Log.e(TAG, "setRemoteDescription (ANSWER) failed for $key: $error")
                        }
                    }, remoteDesc)
                }
            } else if (hasCandidate) {
                val candObj = args ?: json.optJSONObject("candidate") ?: json.optJSONObject("iceCandidate")
                val candidateSdp = candObj?.optString("sdp")?.ifEmpty { null }
                    ?: candObj?.optString("candidate")?.ifEmpty { null }
                    ?: json.optString("candidate").ifEmpty { null }
                    ?: json.optString("sdp")
                val sdpMid = candObj?.optString("mid")?.ifEmpty { null }
                    ?: candObj?.optString("sdpMid")?.ifEmpty { null }
                    ?: json.optString("sdpMid", "0")
                val sdpMLineIndex = when {
                    candObj != null && candObj.has("mLine") -> candObj.optInt("mLine", 0)
                    candObj != null && candObj.has("sdpMLineIndex") -> candObj.optInt("sdpMLineIndex", 0)
                    json.has("sdpMLineIndex") -> json.optInt("sdpMLineIndex", 0)
                    else -> 0
                }
                if (!candidateSdp.isNullOrBlank()) {
                    val candidate = IceCandidate(sdpMid, sdpMLineIndex, candidateSdp)
                    val peerConnection = peerConnections[key]
                    if (peerConnection != null && peerConnection.remoteDescription != null) {
                        peerConnection.addIceCandidate(candidate)
                    } else {
                        pendingIceCandidates.getOrPut(key) { mutableListOf() }.add(candidate)
                    }
                }
            }
        } catch (error: Throwable) {
            Log.e(TAG, "Error handling remote signaling from client $senderClientId", error)
        }
    }

    private fun normalizeSdp(sdp: String): String {
        return sdp
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\r\n") + "\r\n"
    }

    fun cleanOfferSdp(sdp: String): String {
        val lines = sdp.replace("\r\n", "\n").replace("\r", "\n").lines()
        val keptPayloads = setOf("96", "97", "104", "105") // VP8 (96/97) and H264 baseline (104/105)
        val result = mutableListOf<String>()

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("m=video")) {
                val parts = line.split(" ")
                if (parts.size > 3) {
                    val payloads = parts.subList(3, parts.size).filter { it in keptPayloads }
                    result.add(parts.subList(0, 3).joinToString(" ") + " " + payloads.joinToString(" "))
                    continue
                }
            } else if (line.startsWith("a=rtpmap:") || line.startsWith("a=rtcp-fb:") || line.startsWith("a=fmtp:")) {
                val colonIdx = line.indexOf(':')
                val spaceIdx = line.indexOf(' ', colonIdx)
                val pt = if (spaceIdx != -1) line.substring(colonIdx + 1, spaceIdx) else line.substring(colonIdx + 1)
                if (pt !in keptPayloads) continue
            } else if (line.startsWith("a=candidate:")) {
                continue
            }
            result.add(line)
        }
        return result.joinToString("\r\n") + "\r\n"
    }

    fun cleanAnswerSdp(sdp: String): String {
        val lines = sdp.replace("\r\n", "\n").replace("\r", "\n").lines()
        val result = mutableListOf<String>()

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            // Strip inline candidates; candidates are sent via trickle ICE
            if (line.startsWith("a=candidate:")) continue
            result.add(line)
        }
        return result.joinToString("\r\n") + "\r\n"
    }

    private fun processRemoteOffer(senderClientId: Int, streamId: String, key: String, sdp: String) {
        var peerConnection = peerConnections[key]
        if (peerConnection == null) {
            peerConnection = createPeerConnection(senderClientId, streamId)
            if (peerConnection != null) {
                peerConnections[key] = peerConnection
                localVideoTrackInternal?.let { track ->
                    peerConnection.addTrack(track, listOf("ARDAMS"))
                }
            }
        }
        val remoteDesc = SessionDescription(SessionDescription.Type.OFFER, normalizeSdp(sdp))
        peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                Log.i(TAG, "setRemoteDescription (OFFER) succeeded for $key, creating answer...")
                drainPendingIceCandidates(key, peerConnection)
                val sdpConstraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
                }
                peerConnection.createAnswer(object : SimpleSdpObserver() {
                    override fun onCreateSuccess(desc: SessionDescription?) {
                        desc ?: return
                        Log.i(TAG, "createAnswer succeeded for $key, setting local description and sending...")
                        peerConnection.setLocalDescription(SimpleSdpObserver(), desc)
                        val minifiedAnswer = cleanAnswerSdp(desc.description)
                        Log.i(TAG, "cleanAnswerSdp: length=${minifiedAnswer.length} for $key")
                        val answerJson = JSONObject().apply {
                            put("cmd", "answer")
                            put("args", JSONObject().apply {
                                put("answer", minifiedAnswer)
                            })
                        }.toString()
                        sendSignalingCallback(senderClientId, streamId, answerJson)
                    }
                    override fun onCreateFailure(error: String?) {
                        Log.e(TAG, "createAnswer failed for $key: $error")
                    }
                }, sdpConstraints)
            }
            override fun onSetFailure(error: String?) {
                Log.e(TAG, "setRemoteDescription (OFFER) failed for $key: $error")
            }
        }, remoteDesc)
    }

    private fun drainPendingIceCandidates(key: String, peerConnection: PeerConnection) {
        val candidates = pendingIceCandidates.remove(key) ?: return
        Log.i(TAG, "Draining ${candidates.size} pending ICE candidates for $key")
        for (c in candidates) {
            peerConnection.addIceCandidate(c)
        }
    }

    private fun createPeerConnection(targetClientId: Int, streamId: String): PeerConnection? {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            bundlePolicy = PeerConnection.BundlePolicy.BALANCED
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
        }

        return peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                Log.i(TAG, "SignalingState for $streamId from $targetClientId: $state")
            }
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.i(TAG, "ICE Connection State for $streamId from $targetClientId: $state")
            }
            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                Log.i(TAG, "PeerConnection State for $streamId from $targetClientId: $newState")
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                Log.i(TAG, "IceGatheringState for $streamId: $state")
            }

            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate ?: return
                val json = JSONObject().apply {
                    put("cmd", "iceCandidate")
                    put("args", JSONObject().apply {
                        put("mLine", candidate.sdpMLineIndex)
                        put("mid", candidate.sdpMid)
                        put("sdp", candidate.sdp)
                    })
                }.toString()
                sendSignalingCallback(targetClientId, streamId, json)
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

            override fun onTrack(transceiver: RtpTransceiver?) {
                val track = transceiver?.receiver?.track()
                if (track is VideoTrack) {
                    track.setEnabled(true)
                    Log.i(TAG, "Received remote VideoTrack via onTrack for $streamId (enabled=${track.enabled()})")
                    attachRemoteTrack(streamId, track)
                }
            }

            override fun onAddStream(mediaStream: MediaStream?) {
                val track = mediaStream?.videoTracks?.firstOrNull()
                if (track != null) {
                    track.setEnabled(true)
                    Log.i(TAG, "Received remote VideoTrack via onAddStream for $streamId (enabled=${track.enabled()})")
                    attachRemoteTrack(streamId, track)
                }
            }

            override fun onRemoveStream(mediaStream: MediaStream?) {
                detachRemoteTrack(streamId)
            }

            override fun onDataChannel(channel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
        })
    }

    private fun attachRemoteTrack(streamId: String, track: VideoTrack) {
        val current = _remoteVideoTracks.value.toMutableMap()
        current[streamId] = track
        _remoteVideoTracks.value = current
    }

    private fun detachRemoteTrack(streamId: String) {
        val current = _remoteVideoTracks.value.toMutableMap()
        current.remove(streamId)
        _remoteVideoTracks.value = current
    }

    private fun peerKey(clientId: Int, streamId: String) = "$clientId:$streamId"

    fun close() {
        stopCameraBroadcast()
        for ((_, pc) in peerConnections) {
            runCatching { pc.close() }
            runCatching { pc.dispose() }
        }
        peerConnections.clear()
        pendingIceCandidates.clear()
        _remoteVideoTracks.value = emptyMap()
        runCatching { peerConnectionFactory.dispose() }
        runCatching { eglBase.release() }
    }

    private open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) {
            Log.e(TAG, "SDP create failure: $error")
        }
        override fun onSetFailure(error: String?) {
            Log.e(TAG, "SDP set failure: $error")
        }
    }

    companion object {
        private const val TAG = "Ts6WebRtc"
    }
}
