package br.app.astrum.ts6.audio.opus

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioRoutingTest {
    private val routes = listOf(
        AudioRoutingState.SystemRoute,
        AudioRouteOption(7, AudioRouteKind.SPEAKER, "扬声器"),
        AudioRouteOption(12, AudioRouteKind.BLUETOOTH, "蓝牙设备"),
    )

    @Test
    fun keepsAnAvailableSelection() {
        assertEquals(12, resolveSelectedRouteId(12, routes))
    }

    @Test
    fun fallsBackToSystemWhenADeviceDisappears() {
        assertEquals(SYSTEM_AUDIO_ROUTE_ID, resolveSelectedRouteId(99, routes))
    }
}
