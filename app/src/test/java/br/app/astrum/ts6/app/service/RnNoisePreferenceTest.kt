package br.app.astrum.ts6.app.service

import br.app.astrum.ts6.audio.opus.SuppressionMode
import org.junit.Assert.assertEquals
import org.junit.Test

class RnNoisePreferenceTest {
    @Test
    fun newInstallDefaultsToEnabled() {
        assertEquals(SuppressionMode.ASTRUM_CLARITY, AudioPreferences.DEFAULT_SUPPRESSION_MODE)
        assertEquals(SuppressionMode.ASTRUM_CLARITY, TeamSpeakServiceState().suppressionMode)
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

    @Test
    fun restoresPersistedAstrumClarityValue() {
        val preferenceState = SuppressionModePreferenceState()

        assertEquals(SuppressionMode.ASTRUM_CLARITY, preferenceState.restore(persistedValue = SuppressionMode.ASTRUM_CLARITY))
        assertEquals(SuppressionMode.ASTRUM_CLARITY, preferenceState.current())
    }
}

