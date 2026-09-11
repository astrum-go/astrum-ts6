package br.app.astrum.ts6.app.service

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import br.app.astrum.ts6.audio.opus.SuppressionMode
import kotlinx.coroutines.launch
import java.io.IOException

private val Context.audioPreferencesDataStore by preferencesDataStore(name = "ts3_audio")
private val suppressionModeKey = stringPreferencesKey("suppression_mode")
private val rnNoiseEnabledKey = booleanPreferencesKey("rnnoise_enabled")

class AudioPreferences(private val context: Context) {
    val suppressionMode: Flow<SuppressionMode> = context.audioPreferencesDataStore.data
        .catch { error ->
            if (error is IOException) {
                System.err.println("TS3_AUDIO: failed to read audio preferences: ${error.message}")
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { preferences ->
            val stored = preferences[suppressionModeKey]
            if (stored != null) {
                try {
                    SuppressionMode.valueOf(stored)
                } catch (_: IllegalArgumentException) {
                    DEFAULT_SUPPRESSION_MODE
                }
            } else {
                // Migration from old boolean rnnoise_enabled key
                val oldEnabled = preferences[rnNoiseEnabledKey]
                if (oldEnabled != null) {
                    if (oldEnabled) SuppressionMode.RNNOISE else SuppressionMode.OFF
                } else {
                    DEFAULT_SUPPRESSION_MODE
                }
            }
        }

    suspend fun readSuppressionMode(): SuppressionMode = AudioPreferencesCoordinator.read {
        suppressionMode.first()
    }

    fun setSuppressionMode(mode: SuppressionMode) {
        AudioPreferencesCoordinator.request(context, mode)
    }

    companion object {
        val DEFAULT_SUPPRESSION_MODE = SuppressionMode.ASTRUM_CLARITY
    }
}

internal class SuppressionModePreferenceState {
    private var currentValue = AudioPreferences.DEFAULT_SUPPRESSION_MODE
    private var loaded = false
    private var pendingValue: SuppressionMode? = null

    @Synchronized
    fun request(mode: SuppressionMode): SuppressionMode {
        currentValue = mode
        if (!loaded) pendingValue = mode
        return currentValue
    }

    @Synchronized
    fun restore(persistedValue: SuppressionMode): SuppressionMode {
        if (!loaded) {
            currentValue = pendingValue ?: persistedValue
            pendingValue = null
            loaded = true
        }
        return currentValue
    }

    @Synchronized
    fun current(): SuppressionMode = currentValue
}

private object AudioPreferencesCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requestSignals = Channel<Unit>(Channel.CONFLATED)
    private val preferenceSnapshot = SuppressionModePreferenceSnapshot()
    private val lock = Any()
    private var context: Context? = null

    init {
        scope.launch {
            for (ignored in requestSignals) persistLatestRequest()
        }
    }

    fun request(requestContext: Context, mode: SuppressionMode) {
        preferenceSnapshot.request(mode)
        synchronized(lock) {
            if (context == null) context = requestContext.applicationContext
        }
        requestSignals.trySend(Unit)
    }

    suspend fun read(persistedReader: suspend () -> SuppressionMode): SuppressionMode {
        preferenceSnapshot.knownValue()?.let { return it }
        val persisted = persistedReader()
        return preferenceSnapshot.resolve(persisted)
    }

    private suspend fun persistLatestRequest() {
        val request = preferenceSnapshot.pendingRequest() ?: return
        val requestContext = synchronized(lock) { context } ?: return
        if (preferenceSnapshot.pendingRequest()?.version != request.version) return
        val persisted = try {
            requestContext.audioPreferencesDataStore.edit { preferences ->
                preferences[suppressionModeKey] = request.mode.name
            }
            true
        } catch (error: IOException) {
            System.err.println("TS3_AUDIO: failed to write audio preferences: ${error.message}")
            false
        }
        if (persisted) {
            preferenceSnapshot.commit(request)
        }
    }
}

internal class SuppressionModePreferenceSnapshot {
    private var nextVersion = 0L
    private var latest: Request? = null
    private var committedValue: SuppressionMode? = null

    @Synchronized
    fun request(mode: SuppressionMode) {
        val version = ++nextVersion
        latest = Request(version, mode)
    }

    @Synchronized
    fun knownValue(): SuppressionMode? = latest?.mode ?: committedValue

    @Synchronized
    fun pendingRequest(): Request? = latest

    @Synchronized
    fun resolve(persisted: SuppressionMode): SuppressionMode {
        latest?.mode?.let { return it }
        if (committedValue == null) committedValue = persisted
        return committedValue ?: persisted
    }

    @Synchronized
    fun commit(request: Request) {
        committedValue = request.mode
        if (latest?.version == request.version) latest = null
    }

    data class Request(
        val version: Long,
        val mode: SuppressionMode,
    )
}
