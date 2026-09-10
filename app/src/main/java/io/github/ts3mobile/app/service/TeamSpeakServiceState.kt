package io.github.ts3mobile.app.service

import io.github.ts3mobile.audio.opus.AudioRoutingState
import io.github.ts3mobile.audio.opus.SuppressionMode
import io.github.ts3mobile.protocol.ConnectionStatus
import io.github.ts3mobile.protocol.SessionSnapshot
import io.github.ts3mobile.protocol.Ts3Participant

enum class MicrophoneMode {
    OFF,
    PUSH_TO_TALK,
    CONTINUOUS,
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
)
