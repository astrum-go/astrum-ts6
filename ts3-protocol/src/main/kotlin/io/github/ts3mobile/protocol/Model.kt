package io.github.ts3mobile.protocol

data class ServerConfig(
    val host: String,
    val port: Int = 9987,
    val nickname: String,
    val password: String = "",
) {
    fun normalized(): ServerConfig = copy(
        host = host.trim(),
        nickname = nickname.trim(),
    )

    fun validationError(): String? = when {
        host.trim().isEmpty() -> "Server address is required"
        port !in 1..65535 -> "Port must be between 1 and 65535"
        nickname.trim().length !in 3..30 -> "Nickname must contain 3 to 30 characters"
        else -> null
    }
}

enum class ConnectionPhase {
    DISCONNECTED,
    CONNECTING,
    RECONNECTING,
    CONNECTED,
    DISCONNECTING,
    ERROR,
}

data class ConnectionStatus(
    val phase: ConnectionPhase = ConnectionPhase.DISCONNECTED,
    val detail: String? = null,
    val retryable: Boolean = false,
)

data class Ts3Channel(
    val id: Int,
    val parentId: Int,
    val orderAfterId: Int,
    val name: String,
    val clientCount: Int,
    val hasPassword: Boolean,
    val isDefault: Boolean,
)

enum class StreamType(val value: Int) {
    UNKNOWN(0),
    CAMERA(1),
    SCREEN(2),
    WINDOW(3);

    companion object {
        fun fromValue(value: String?, name: String? = null): StreamType {
            val n = name?.lowercase()?.trim().orEmpty()
            if (n.contains("screen") ||
                n.contains("tela") ||
                n.contains("display") ||
                n.contains("monitor") ||
                n.contains("desktop")
            ) {
                return SCREEN
            }
            if (n.contains("window") ||
                n.contains("janela") ||
                n.contains("devtools") ||
                n.contains("chrome") ||
                n.contains("edge") ||
                n.contains("firefox") ||
                n.contains("discord") ||
                n.contains("app")
            ) {
                return WINDOW
            }
            if (n.contains("cam") ||
                n.contains("camera") ||
                n.contains("câmera") ||
                n.contains("webcam") ||
                n.contains("droidcam") ||
                n.contains("iriun") ||
                n.contains("obs")
            ) {
                return CAMERA
            }

            return when (value?.lowercase()?.trim()) {
                "3", "window", "janela", "app" -> WINDOW
                "2", "screen", "screens", "desktop" -> SCREEN
                "1", "camera", "cameras", "cam", "webcam" -> CAMERA
                else -> if (n.isNotEmpty()) WINDOW else CAMERA
            }
        }
    }
}

enum class StreamPreset(
    val title: String,
    val description: String,
    val maxDimension: Int,
    val fps: Int,
    val bitrateKbps: Int,
) {
    BALANCED_720P_30("720p @ 30fps", "Equilibrado (Recomendado)", 720, 30, 2000),
    HIGH_1080P_30("1080p @ 30fps", "Alta Nitidez (Texto/Telas)", 1080, 30, 3500),
    GAMING_720P_60("720p @ 60fps", "Alta Fluidez (Jogos)", 720, 60, 3500),
    LOW_480P_30("480p @ 30fps", "Econômico (Poupa bateria/dados)", 480, 30, 800);

    val bitrateBps: Int get() = bitrateKbps * 1000
}

data class Ts6StreamInfo(
    val streamId: String,
    val clientId: Int,
    val type: StreamType,
    val width: Int = 1280,
    val height: Int = 720,
    val fps: Int = 30,
    val bitrate: Int = 0,
    val description: String = "",
) {
    val isScreenOrWindow: Boolean get() = type == StreamType.SCREEN || type == StreamType.WINDOW
    val isCamera: Boolean get() = type == StreamType.CAMERA

    fun displayTitle(): String = when {
        description.isNotBlank() -> description
        type == StreamType.SCREEN -> "Tela"
        type == StreamType.WINDOW -> "Janela"
        type == StreamType.CAMERA -> "Câmera"
        else -> "Transmissão"
    }

    fun displayFullLabel(): String = "Transmissão: ${displayTitle()}"
}

data class Ts6StreamSignaling(
    val streamId: String,
    val senderClientId: Int,
    val payload: String,
)

data class Ts3Participant(
    val id: Int,
    val channelId: Int,
    val nickname: String,
    val isTalking: Boolean,
    val isInputMuted: Boolean,
    val isOutputMuted: Boolean,
    val uniqueIdentifier: String = "",
    val hasActiveCamera: Boolean = false,
    val hasActiveScreen: Boolean = false,
    val hasActiveStream: Boolean = false,
)

data class SessionSnapshot(
    val channels: List<Ts3Channel> = emptyList(),
    val participants: List<Ts3Participant> = emptyList(),
    val ownClientId: Int? = null,
    val activeStreams: List<Ts6StreamInfo> = emptyList(),
) {
    val currentChannelId: Int?
        get() = participants.firstOrNull { it.id == ownClientId }?.channelId

    companion object {
        val Empty = SessionSnapshot()
    }
}

data class ChannelRow(
    val channel: Ts3Channel,
    val depth: Int,
)
