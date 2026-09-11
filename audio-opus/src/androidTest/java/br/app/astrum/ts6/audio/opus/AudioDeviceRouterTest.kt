package br.app.astrum.ts6.audio.opus

import android.content.Context
import android.media.AudioManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AudioDeviceRouterTest {
    @Test
    fun selectsAnAvailableRouteAndRestoresAudioMode() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val audioManager = context.getSystemService(AudioManager::class.java)
        val initialMode = audioManager.mode
        val router = AudioDeviceRouter(context) {}

        try {
            router.start()
            val initialState = router.state
            assertEquals(SYSTEM_AUDIO_ROUTE_ID, initialState.selectedRouteId)
            assertTrue(initialState.routes.any { it.id == SYSTEM_AUDIO_ROUTE_ID })

            val concreteRoute = initialState.routes.firstOrNull {
                it.kind == AudioRouteKind.SPEAKER
            } ?: initialState.routes.first { it.id != SYSTEM_AUDIO_ROUTE_ID }

            router.selectRoute(concreteRoute.id)
            assertEquals(concreteRoute.id, router.state.selectedRouteId)
            assertEquals(null, router.state.error)

            router.selectRoute(SYSTEM_AUDIO_ROUTE_ID)
            assertEquals(SYSTEM_AUDIO_ROUTE_ID, router.state.selectedRouteId)
            assertEquals(null, router.state.error)
        } finally {
            router.close()
        }

        assertEquals(initialMode, audioManager.mode)
    }
}
