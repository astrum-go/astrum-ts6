package io.github.ts3mobile.app.video

import android.content.Context
import android.content.Intent
import android.graphics.Matrix
import android.media.projection.MediaProjection
import android.util.Log
import io.github.ts3mobile.protocol.StreamType
import org.webrtc.ScreenCapturerAndroid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.CapturerObserver
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.JavaI420Buffer
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RendererCommon
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoFrame
import org.webrtc.VideoProcessor
import org.webrtc.VideoSink
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.YuvHelper
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

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val peerConnectionFactory: PeerConnectionFactory

    var onBroadcastStoppedCallback: (() -> Unit)? = null

    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoCapturer: VideoCapturer? = null
    private var cameraVideoCapturer: CameraVideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrackInternal: VideoTrack? = null

    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

    private val _isBroadcasting = MutableStateFlow(false)
    val isBroadcasting: StateFlow<Boolean> = _isBroadcasting.asStateFlow()

    private val _isBroadcastingScreen = MutableStateFlow(false)
    val isBroadcastingScreen: StateFlow<Boolean> = _isBroadcastingScreen.asStateFlow()

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

            val rotationProcessor = RotationVideoProcessor()
            videoSource?.setVideoProcessor(rotationProcessor)

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
            cameraVideoCapturer = capturer
            capturer.initialize(surfaceTextureHelper, context, videoSource?.capturerObserver)

            val sensorWidth = maxOf(width, height)
            val sensorHeight = minOf(width, height)
            capturer.startCapture(sensorWidth, sensorHeight, fps)

            val track = peerConnectionFactory.createVideoTrack("ARDAMSv0", videoSource)
            track.setEnabled(true)
            localVideoTrackInternal = track
            _localVideoTrack.value = track
            _isBroadcastingScreen.value = false
            _isBroadcasting.value = true
            Log.i(TAG, "Camera broadcast started: streamId=$streamId (${width}x${height}@${fps}fps, sensor=${sensorWidth}x${sensorHeight})")
        } catch (error: Throwable) {
            Log.e(TAG, "Failed to start camera broadcast", error)
            stopBroadcast()
        }
    }

    /**
     * Starts broadcasting the device screen via MediaProjection to TeamSpeak 6 channel.
     */
    fun startScreenBroadcast(streamId: String, resultData: Intent, width: Int, height: Int, fps: Int = 30) {
        if (_isBroadcasting.value) return
        activeBroadcastStreamId = streamId

        try {
            surfaceTextureHelper = SurfaceTextureHelper.create("Ts6ScreenThread", eglBase.eglBaseContext)
            videoSource = peerConnectionFactory.createVideoSource(/* isScreencast = */ true)

            val capturer = ScreenCapturerAndroid(resultData, object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.i(TAG, "MediaProjection stopped by system")
                    onBroadcastStoppedCallback?.invoke()
                    stopBroadcast()
                }
            })

            videoCapturer = capturer
            cameraVideoCapturer = null
            capturer.initialize(surfaceTextureHelper, context, videoSource?.capturerObserver)
            capturer.startCapture(width, height, fps)

            val track = peerConnectionFactory.createVideoTrack("ARDAMSv0", videoSource)
            track.setEnabled(true)
            localVideoTrackInternal = track
            _localVideoTrack.value = track
            _isBroadcastingScreen.value = true
            _isBroadcasting.value = true
            Log.i(TAG, "Screen broadcast started: streamId=$streamId (${width}x${height}@${fps}fps)")
        } catch (error: Throwable) {
            Log.e(TAG, "Failed to start screen broadcast", error)
            stopBroadcast()
        }
    }

    /**
     * Toggles between front and back camera.
     */
    fun switchCamera() {
        val capturer = cameraVideoCapturer ?: return
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
        stopBroadcast()
    }

    fun stopScreenBroadcast() {
        stopBroadcast()
    }

    fun stopBroadcast() {
        _isBroadcasting.value = false
        _isBroadcastingScreen.value = false
        val stoppingStreamId = activeBroadcastStreamId
        activeBroadcastStreamId = null

        // Fechar todas as peer connections de viewers associadas a este stream.
        // Sem isso, ao reiniciar a câmera as PCs zumbis bloqueavam novos joins.
        if (stoppingStreamId != null) {
            val viewerKeys = peerConnections.keys().toList().filter { it.endsWith(":$stoppingStreamId") }
            for (key in viewerKeys) {
                peerConnections.remove(key)?.let { pc ->
                    Log.i(TAG, "stopBroadcast: closing viewer PeerConnection for $key")
                    runCatching { pc.close() }
                    runCatching { pc.dispose() }
                }
                pendingIceCandidates.remove(key)
            }
        }

        try {
            videoCapturer?.stopCapture()
        } catch (ignored: Throwable) {}
        videoCapturer?.dispose()
        videoCapturer = null
        cameraVideoCapturer = null

        localVideoTrackInternal?.dispose()
        localVideoTrackInternal = null
        _localVideoTrack.value = null

        videoSource?.dispose()
        videoSource = null

        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null

        Log.i(TAG, "Broadcast stopped")
    }

    private var maxBroadcastBitrateBps: Int = 2_000_000

    fun setBroadcastBitrate(bitrateBps: Int) {
        maxBroadcastBitrateBps = bitrateBps
        peerConnections.values.forEach { pc ->
            applyBitrateLimit(pc, bitrateBps)
        }
    }

    fun applyBitrateLimit(peerConnection: PeerConnection, maxBitrateBps: Int = maxBroadcastBitrateBps) {
        for (sender in peerConnection.senders) {
            if (sender.track()?.kind() == MediaStreamTrack.VIDEO_TRACK_KIND) {
                val params = sender.parameters ?: continue
                for (encoding in params.encodings) {
                    encoding.maxBitrateBps = maxBitrateBps
                    encoding.minBitrateBps = maxBitrateBps / 4
                }
                sender.parameters = params
                Log.d(TAG, "Applied bitrate limit: ${maxBitrateBps / 1000} kbps")
            }
        }
    }

    fun changeScreenCaptureFormat(width: Int, height: Int, fps: Int) {
        if (!_isBroadcastingScreen.value) return
        try {
            videoCapturer?.changeCaptureFormat(width, height, fps)
            videoSource?.adaptOutputFormat(width, height, fps)
            Log.i(TAG, "changeScreenCaptureFormat: updated to ${width}x${height}@${fps}fps")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to changeScreenCaptureFormat", e)
        }
    }

    fun changeCameraCaptureFormat(width: Int, height: Int, fps: Int) {
        val capturer = cameraVideoCapturer ?: return
        try {
            val sensorWidth = maxOf(width, height)
            val sensorHeight = minOf(width, height)
            capturer.changeCaptureFormat(sensorWidth, sensorHeight, fps)
            Log.i(TAG, "changeCameraCaptureFormat: updated to ${sensorWidth}x${sensorHeight}@${fps}fps")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to changeCameraCaptureFormat", e)
        }
    }

    fun getScreenCapturerMediaProjection(): MediaProjection? {
        val capturer = videoCapturer as? ScreenCapturerAndroid ?: return null
        return try {
            val field = ScreenCapturerAndroid::class.java.getDeclaredField("mediaProjection")
            field.isAccessible = true
            field.get(capturer) as? MediaProjection
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to obtain MediaProjection via reflection", e)
            null
        }
    }

    /**
     * Joins and begins watching a remote stream (camera or screen).
     */
    fun watchStream(remoteClientId: Int, streamId: String) {
        if (streamId == activeBroadcastStreamId) {
            Log.w(TAG, "watchStream: cannot watch own broadcast stream $streamId")
            return
        }
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
     * Closes the active peer connection for a viewer who disconnected or left the stream.
     */
    fun stopViewer(remoteClientId: Int, streamId: String) {
        val key = peerKey(remoteClientId, streamId)
        peerConnections.remove(key)?.let { pc ->
            Log.i(TAG, "stopViewer: closing PeerConnection for $key")
            runCatching { pc.close() }
            runCatching { pc.dispose() }
        }
        pendingIceCandidates.remove(key)
    }

    /**
     * Handles an incoming join request from a remote client who wants to watch our stream.
     *
     * The captured TS6 broadcaster sends a video SEND_ONLY transceiver followed by
     * an INACTIVE audio transceiver, even with audio capture disabled. The initial
     * offer is carried by respondjoinstreamrequest; the viewer supplies the answer.
     */
    fun handleJoinRequest(requesterClientId: Int, streamId: String) {
        if (activeBroadcastStreamId != streamId) {
            Log.w(TAG, "handleJoinRequest: ignoring request for inactive stream $streamId")
            return
        }
        val key = peerKey(requesterClientId, streamId)
        // Se já existe uma PC para este viewer (reconexão ou retry), fechar antes de recriar.
        peerConnections.remove(key)?.let { stale ->
            Log.w(TAG, "handleJoinRequest: closing stale PeerConnection for $key before recreating")
            runCatching { stale.close() }
            runCatching { stale.dispose() }
            pendingIceCandidates.remove(key)
        }

        val peerConnection = createPeerConnection(requesterClientId, streamId) ?: return
        peerConnections[key] = peerConnection

        managerScope.launch {
            val track = withTimeoutOrNull(10_000) { _localVideoTrack.filterNotNull().first() }
            if (track == null) {
                Log.e(TAG, "handleJoinRequest: no local video track within 10s for $key, aborting")
                if (peerConnections.remove(key, peerConnection)) {
                    pendingIceCandidates.remove(key)
                    runCatching { peerConnection.close() }
                    runCatching { peerConnection.dispose() }
                }
                return@launch
            }
            // A retry or stop may have replaced/disposed this peer while capture started.
            if (peerConnections[key] !== peerConnection || activeBroadcastStreamId != streamId) return@launch
            peerConnection.addTransceiver(
                track,
                RtpTransceiver.RtpTransceiverInit(
                    RtpTransceiver.RtpTransceiverDirection.SEND_ONLY,
                    listOf("outgoing_video"),
                ),
            )
            peerConnection.addTransceiver(
                MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
                RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.INACTIVE),
            )
            applyBitrateLimit(peerConnection)
            Log.i(TAG, "handleJoinRequest: local track ready, creating offer for $key")

            val constraints = MediaConstraints()
            peerConnection.createOffer(object : SimpleSdpObserver() {
                override fun onCreateSuccess(desc: SessionDescription?) {
                    desc ?: return
                    if (peerConnections[key] !== peerConnection || activeBroadcastStreamId != streamId) return
                    peerConnection.setLocalDescription(object : SimpleSdpObserver() {
                        override fun onSetSuccess() {
                            if (peerConnections[key] !== peerConnection || activeBroadcastStreamId != streamId) return
                            // Preserve the exact codec/payload mapping accepted locally. RTP
                            // payload IDs are negotiated, not fixed identifiers for codecs.
                            val offerSdp = if (desc.description.endsWith("\r\n")) desc.description else desc.description.trimEnd() + "\r\n"
                            Log.i(TAG, "Local broadcast offer ready for $key: length=${offerSdp.length}, video=sendonly, audio=inactive")
                            respondJoinStreamCallback?.invoke(requesterClientId, streamId, offerSdp)
                        }

                        override fun onSetFailure(error: String?) {
                            Log.e(TAG, "Broadcast setLocalDescription failed for $key: $error")
                        }
                    }, desc)
                }
                override fun onCreateFailure(error: String?) {
                    Log.e(TAG, "handleJoinRequest: createOffer failed for $key: $error")
                }
            }, constraints)
        }
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
                } else {
                    Log.e(TAG, "Remote client returned an empty SDP answer for $key; cleaning up PeerConnection")
                    peerConnections.remove(key)?.apply {
                        runCatching { close() }
                        runCatching { dispose() }
                    }
                    pendingIceCandidates.remove(key)
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
                when (newState) {
                    PeerConnection.PeerConnectionState.FAILED -> {
                        // Limpar PC falha para que o próximo join request possa criar uma nova sem bloqueio
                        Log.e(TAG, "PeerConnection FAILED for $streamId from $targetClientId — removing stale connection")
                        val key = peerKey(targetClientId, streamId)
                        peerConnections.remove(key)?.let { pc ->
                            runCatching { pc.close() }
                            runCatching { pc.dispose() }
                        }
                        pendingIceCandidates.remove(key)
                    }
                    PeerConnection.PeerConnectionState.DISCONNECTED ->
                        Log.w(TAG, "PeerConnection DISCONNECTED for $streamId from $targetClientId — will auto-retry on next join request")
                    PeerConnection.PeerConnectionState.CONNECTED ->
                        Log.i(TAG, "PeerConnection CONNECTED for $streamId from $targetClientId ✓")
                    else -> Unit
                }
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
        managerScope.cancel()
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

    /**
     * Fecha todas as peer connections remotas (viewers e streams assistidos) sem tocar na câmera local.
     * Chamado ao iniciar uma nova sessão de servidor para evitar conexões zumbi da sessão anterior.
     */
    fun resetViewerState() {
        val keys = peerConnections.keys().toList()
        for (key in keys) {
            peerConnections.remove(key)?.let { pc ->
                runCatching { pc.close() }
                runCatching { pc.dispose() }
            }
        }
        pendingIceCandidates.clear()
        _remoteVideoTracks.value = emptyMap()
        Log.i(TAG, "resetViewerState: all remote peer connections closed (${keys.size} total)")
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

    /**
     * Ensures camera frames sent to WebRTC have rotation = 0 and are physically upright.
     * TeamSpeak 6 desktop's video renderer does not respect CVO (urn:3gpp:video-orientation),
     * causing camera feeds to appear sideways. By performing physical rotation via libyuv
     * NEON SIMD assembly (YuvHelper.I420Rotate), the desktop displays portrait and landscape
     * correctly and upright, matching screen share behavior.
     */
    private class RotationVideoProcessor : VideoProcessor {
        private var sink: VideoSink? = null

        override fun setSink(sink: VideoSink?) {
            this.sink = sink
        }

        override fun onCapturerStarted(success: Boolean) {}
        override fun onCapturerStopped() {}

        override fun onFrameCaptured(frame: VideoFrame) {
            processFrame(frame)
        }

        override fun onFrameCaptured(frame: VideoFrame, parameters: VideoProcessor.FrameAdaptationParameters) {
            val adaptedFrame = VideoProcessor.applyFrameAdaptationParameters(frame, parameters) ?: return
            try {
                processFrame(adaptedFrame)
            } finally {
                adaptedFrame.release()
            }
        }

        private fun processFrame(frame: VideoFrame) {
            val currentSink = sink ?: return
            if (frame.rotation == 0) {
                currentSink.onFrame(frame)
                return
            }

            val i420 = frame.buffer.toI420()
            if (i420 == null) {
                currentSink.onFrame(frame)
                return
            }

            try {
                val isSwapped = frame.rotation % 180 != 0
                val dstWidth = if (isSwapped) i420.height else i420.width
                val dstHeight = if (isSwapped) i420.width else i420.height
                val dstBuffer = JavaI420Buffer.allocate(dstWidth, dstHeight)

                YuvHelper.I420Rotate(
                    i420.dataY, i420.strideY,
                    i420.dataU, i420.strideU,
                    i420.dataV, i420.strideV,
                    dstBuffer.dataY, dstBuffer.strideY,
                    dstBuffer.dataU, dstBuffer.strideU,
                    dstBuffer.dataV, dstBuffer.strideV,
                    i420.width, i420.height,
                    frame.rotation,
                )

                val rotatedFrame = VideoFrame(dstBuffer, 0, frame.timestampNs)
                currentSink.onFrame(rotatedFrame)
                rotatedFrame.release()
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to rotate camera frame, falling back to unrotated", e)
                currentSink.onFrame(frame)
            } finally {
                i420.release()
            }
        }
    }

    companion object {
        private const val TAG = "Ts6WebRtc"
    }
}
