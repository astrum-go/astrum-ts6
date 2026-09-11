package io.github.ts3mobile.app.service

import io.github.ts3mobile.audio.opus.AudioRoutingState
import io.github.ts3mobile.audio.opus.SuppressionMode
import io.github.ts3mobile.protocol.ConnectionStatus
import io.github.ts3mobile.protocol.SessionSnapshot
import io.github.ts3mobile.protocol.Ts3Participant

import io.github.ts3mobile.protocol.StreamType

enum class MicrophoneMode {
    OFF,
    PUSH_TO_TALK,
    CONTINUOUS,
}

data class WatchedStream(
    val streamId: String,
    val clientId: Int,
    val nickname: String = "",
    val type: StreamType = StreamType.CAMERA,
    val name: String = "",
) {
    val isScreenOrWindow: Boolean get() = type == StreamType.SCREEN || type == StreamType.WINDOW

    fun displayTitle(): String = when {
        name.isNotBlank() -> name
        type == StreamType.SCREEN -> "Tela"
        type == StreamType.WINDOW -> "Janela"
        type == StreamType.CAMERA -> "Câmera"
        else -> "Transmissão"
    }

    fun displayFullLabel(): String = "Transmissão (${displayTitle()})"
}

data class ParticipantAudioSettings(
    val volumePercent: Int = 100,
    val muted: Boolean = false,
) {
    val gain: Float
        get() = if (muted) 0f else volumePercent.coerceIn(0, 200) / 100f
}

internal fun Ts3Participant.audioControlKey(): String =
    uniqueIdentifier.ifBlank { "session:$id" }

data class StreamViewer(
    val clientId: Int,
    val nickname: String,
    val streamId: String,
)

data class TeamSpeakServiceState(
    val status: ConnectionStatus = ConnectionStatus(),
    val snapshot: SessionSnapshot = SessionSnapshot.Empty,
    val serverLabel: String? = null,
    val identityReady: Boolean = false,
    val playbackMuted: Boolean = false,
    val participantAudioSettings: Map<String, ParticipantAudioSettings> = emptyMap(),
    val microphoneMode: MicrophoneMode = MicrophoneMode.PUSH_TO_TALK,
    val suppressionMode: SuppressionMode = AudioPreferences.DEFAULT_SUPPRESSION_MODE,
    val isTransmitting: Boolean = false,
    val microphoneError: String? = null,
    val switchingChannelId: Int? = null,
    val channelError: String? = null,
    val audioRouting: AudioRoutingState = AudioRoutingState.Default,
    val isBroadcastingCamera: Boolean = false,
    val isFrontCamera: Boolean = true,
    val activeBroadcastStreamId: String? = null,
    val watchingStreamId: String? = null,
    val watchingStreamClientId: Int? = null,
    val watchingStreams: List<WatchedStream> = emptyList(),
    val autoAcceptStreamViewers: Boolean = true,
    val pendingViewerRequests: List<StreamViewer> = emptyList(),
    val activeViewers: List<StreamViewer> = emptyList(),
)
