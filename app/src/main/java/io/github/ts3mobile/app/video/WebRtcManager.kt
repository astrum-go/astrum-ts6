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

        val peerConnection = createPeerConnection(remoteClientId, streamId) ?: return
        peerConnections[key] = peerConnection

        // Viewer creates offer or waits for broadcaster's offer
        // In TS6, either side can initiate SDP; we create an offer to start negotiation immediately
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
        }

        peerConnection.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc ?: return
                peerConnection.setLocalDescription(SimpleSdpObserver(), desc)
                val json = JSONObject().apply {
                    put("type", "offer")
                    put("sdp", desc.description)
                }.toString()
                sendSignalingCallback(remoteClientId, streamId, json)
            }
        }, constraints)
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
        if (!_isBroadcasting.value || activeBroadcastStreamId != streamId) return
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
                val json = JSONObject().apply {
                    put("type", "offer")
                    put("sdp", desc.description)
                }.toString()
                sendSignalingCallback(requesterClientId, streamId, json)
            }
        }, constraints)
    }

    /**
     * Processes an incoming signaling message (offer, answer, candidate) from TS3 streamsignaling.
     */
    fun handleRemoteSignaling(senderClientId: Int, streamId: String, payload: String) {
        try {
            val json = JSONObject(payload)
            val type = json.optString("type")
            val key = peerKey(senderClientId, streamId)

            when (type) {
                "offer" -> {
                    val sdp = json.getString("sdp")
                    var peerConnection = peerConnections[key]
                    if (peerConnection == null) {
                        peerConnection = createPeerConnection(senderClientId, streamId)
                        if (peerConnection != null) {
                            peerConnections[key] = peerConnection
                            // If we have a local video track to send, add it
                            localVideoTrackInternal?.let { track ->
                                peerConnection.addTrack(track, listOf("ARDAMS"))
                            }
                        }
                    }
                    val remoteDesc = SessionDescription(SessionDescription.Type.OFFER, sdp)
                    peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
                        override fun onSetSuccess() {
                            drainPendingIceCandidates(key, peerConnection)
                            // Create answer
                            peerConnection.createAnswer(object : SimpleSdpObserver() {
                                override fun onCreateSuccess(desc: SessionDescription?) {
                                    desc ?: return
                                    peerConnection.setLocalDescription(SimpleSdpObserver(), desc)
                                    val answerJson = JSONObject().apply {
                                        put("type", "answer")
                                        put("sdp", desc.description)
                                    }.toString()
                                    sendSignalingCallback(senderClientId, streamId, answerJson)
                                }
                            }, MediaConstraints())
                        }
                    }, remoteDesc)
                }
                "answer" -> {
                    val sdp = json.getString("sdp")
                    val peerConnection = peerConnections[key] ?: return
                    val remoteDesc = SessionDescription(SessionDescription.Type.ANSWER, sdp)
                    peerConnection.setRemoteDescription(object : SimpleSdpObserver() {
                        override fun onSetSuccess() {
                            drainPendingIceCandidates(key, peerConnection)
                        }
                    }, remoteDesc)
                }
                "candidate" -> {
                    val candidateSdp = json.getString("candidate")
                    val sdpMid = json.optString("sdpMid", "video")
                    val sdpMLineIndex = json.optInt("sdpMLineIndex", 0)
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

    private fun drainPendingIceCandidates(key: String, peerConnection: PeerConnection) {
        val candidates = pendingIceCandidates.remove(key) ?: return
        for (c in candidates) {
            peerConnection.addIceCandidate(c)
        }
    }

    private fun createPeerConnection(targetClientId: Int, streamId: String): PeerConnection? {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        return peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "ICE Connection State for $streamId: $state")
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}

            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate ?: return
                val json = JSONObject().apply {
                    put("type", "candidate")
                    put("candidate", candidate.sdp)
                    put("sdpMid", candidate.sdpMid)
                    put("sdpMLineIndex", candidate.sdpMLineIndex)
                }.toString()
                sendSignalingCallback(targetClientId, streamId, json)
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

            override fun onTrack(transceiver: RtpTransceiver?) {
                val track = transceiver?.receiver?.track()
                if (track is VideoTrack) {
                    Log.i(TAG, "Received remote VideoTrack via onTrack for $streamId")
                    attachRemoteTrack(streamId, track)
                }
            }

            override fun onAddStream(mediaStream: MediaStream?) {
                val track = mediaStream?.videoTracks?.firstOrNull()
                if (track != null) {
                    Log.i(TAG, "Received remote VideoTrack via onAddStream for $streamId")
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
