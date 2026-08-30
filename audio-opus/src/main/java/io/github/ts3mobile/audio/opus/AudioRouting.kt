package io.github.ts3mobile.audio.opus

const val SYSTEM_AUDIO_ROUTE_ID = -1

enum class AudioRouteKind {
    SYSTEM,
    EARPIECE,
    SPEAKER,
    WIRED,
    BLUETOOTH,
    USB,
    OTHER,
}

data class AudioRouteOption(
    val id: Int,
    val kind: AudioRouteKind,
    val label: String,
)

data class AudioRoutingState(
    val routes: List<AudioRouteOption> = listOf(SystemRoute),
    val selectedRouteId: Int = SYSTEM_AUDIO_ROUTE_ID,
    val error: String? = null,
) {
    val selectedRoute: AudioRouteOption
        get() = routes.firstOrNull { it.id == selectedRouteId } ?: SystemRoute

    companion object {
        val SystemRoute = AudioRouteOption(
            id = SYSTEM_AUDIO_ROUTE_ID,
            kind = AudioRouteKind.SYSTEM,
            label = "Sistema (automático)",
        )

        val Default = AudioRoutingState()
    }
}

internal fun resolveSelectedRouteId(
    requestedRouteId: Int,
    routes: List<AudioRouteOption>,
): Int = requestedRouteId.takeIf { requested -> routes.any { it.id == requested } }
    ?: SYSTEM_AUDIO_ROUTE_ID
