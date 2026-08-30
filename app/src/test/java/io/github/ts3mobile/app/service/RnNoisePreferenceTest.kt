package io.github.ts3mobile.app.service

import io.github.ts3mobile.audio.opus.SuppressionMode
import org.junit.Assert.assertEquals
import org.junit.Test

class RnNoisePreferenceTest {
    @Test
    fun newInstallDefaultsToEnabled() {
        assertEquals(SuppressionMode.RNNOISE, AudioPreferences.DEFAULT_SUPPRESSION_MODE)
        assertEquals(SuppressionMode.RNNOISE, TeamSpeakServiceState().suppressionMode)
    }

    @Test
    fun restoresPersistedDisabledValue() {
        val preferenceState = SuppressionModePreferenceState()

        assertEquals(SuppressionMode.OFF, preferenceState.restore(persistedValue = SuppressionMode.OFF))
        assertEquals(SuppressionMode.OFF, preferenceState.current())
    }

    @Test
    fun latestToggleWinsWhenItArrivesBeforeInitialRestore() {
        val preferenceState = SuppressionModePreferenceState()

        preferenceState.request(SuppressionMode.OFF)
        preferenceState.request(SuppressionMode.RNNOISE)

        assertEquals(SuppressionMode.RNNOISE, preferenceState.restore(persistedValue = SuppressionMode.OFF))
    }

    @Test
    fun pendingRequestIsVisibleToASecondPreferenceReader() {
        val preferenceState = SuppressionModePreferenceState()

        preferenceState.request(SuppressionMode.OFF)

        assertEquals(SuppressionMode.OFF, preferenceState.restore(persistedValue = SuppressionMode.RNNOISE))
    }

    @Test
    fun completedWriteDoesNotMakeAStaleReadWinOverTheLatestRequest() {
        val snapshot = SuppressionModePreferenceSnapshot()
        snapshot.request(SuppressionMode.OFF)

        // Simulate DataStore returning the old value before the pending write commits.
        val stalePersistedValue = SuppressionMode.RNNOISE
        snapshot.commit(snapshot.pendingRequest()!!)

        assertEquals(SuppressionMode.OFF, snapshot.resolve(persisted = stalePersistedValue))
    }

    @Test
    fun restoresPersistedDeepFilterValue() {
        val preferenceState = SuppressionModePreferenceState()

        assertEquals(SuppressionMode.DEEPFILTER, preferenceState.restore(persistedValue = SuppressionMode.DEEPFILTER))
        assertEquals(SuppressionMode.DEEPFILTER, preferenceState.current())
    }
}

