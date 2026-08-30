package io.github.ts3mobile.app.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.ts3mobile.app.MainActivity
import io.github.ts3mobile.app.R
import io.github.ts3mobile.app.identity.IdentityVault
import io.github.ts3mobile.audio.opus.AudioDeviceRouter
import io.github.ts3mobile.audio.opus.OpusAudioPlayer
import io.github.ts3mobile.audio.opus.OpusMicrophoneCapture
import io.github.ts3mobile.audio.opus.AudioRoutingState
import io.github.ts3mobile.audio.opus.SuppressionMode
import io.github.ts3mobile.protocol.ConnectionPhase
import io.github.ts3mobile.protocol.ConnectionStatus
import io.github.ts3mobile.protocol.ServerConfig
import io.github.ts3mobile.protocol.SessionSnapshot
import io.github.ts3mobile.protocol.Ts3SessionClient
import io.github.ts3mobile.protocol.Ts3SessionListener
import io.github.ts3mobile.protocol.Ts3jSessionClient
import io.github.ts3mobile.protocol.VoiceFrame
import io.github.ts3mobile.protocol.isRetryableConnectionFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

class TeamSpeakService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionMutex = Mutex()
    private val transmitMutex = Mutex()
    private val reconnectPolicy = ReconnectPolicy()
    private val pushToTalkPressed = AtomicBoolean(false)
    private val mutableState = MutableStateFlow(TeamSpeakServiceState())
    private val networkAvailable = MutableStateFlow(false)
    private val suppressionModePreferenceReady = CompletableDeferred<Unit>()
    private val suppressionModePreferenceState = SuppressionModePreferenceState()
    private val state = mutableState.asStateFlow()
    private val binder = SessionBinder()

    private lateinit var identityVault: IdentityVault
    private lateinit var audioPreferences: AudioPreferences
    private lateinit var audioPlayer: OpusAudioPlayer
    private lateinit var microphone: OpusMicrophoneCapture
    private lateinit var audioRouter: AudioDeviceRouter
    private lateinit var connectivityManager: ConnectivityManager

    @Volatile
    private var session: Ts3SessionClient? = null

    @Volatile
    private var activeListener: SessionListener? = null

    @Volatile
    private var connectionEpoch = 0L

    @Volatile
    private var connectedOnce = false

    @Volatile
    private var userDisconnectRequested = true

    @Volatile
    private var restorePending = false

    private var desiredConfig: ServerConfig? = null
    private var identityMaterial: String? = null
    private var lastChannel: ChannelTarget? = null
    private var reconnectAttempt = 0
    private var connectionJob: Job? = null
    private var reconnectJob: Job? = null
    private var stableConnectionJob: Job? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateNetworkAvailability(true)
        }

        override fun onLost(network: Network) {
            updateNetworkAvailability(hasUsableNetwork())
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            updateNetworkAvailability(
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        identityVault = IdentityVault(applicationContext)
        audioPreferences = AudioPreferences(applicationContext)
        audioPlayer = OpusAudioPlayer(applicationContext)
        microphone = OpusMicrophoneCapture(applicationContext, ::onMicrophoneFailure)
        audioRouter = AudioDeviceRouter(applicationContext, ::onAudioRoutingChanged)
        serviceScope.launch {
            val persistedValue = runCatching { audioPreferences.readSuppressionMode() }
                .getOrElse { error ->
                    System.err.println(
                        "TS3_AUDIO: failed to resolve suppression mode; " +
                            "using default: ${error.message}",
                    )
                    AudioPreferences.DEFAULT_SUPPRESSION_MODE
                }
            val mode = suppressionModePreferenceState.restore(persistedValue)
            microphone.setSuppressionMode(mode)
            mutableState.update { it.copy(suppressionMode = mode) }
            suppressionModePreferenceReady.complete(Unit)
        }
        connectivityManager = getSystemService(ConnectivityManager::class.java)
        networkAvailable.value = hasUsableNetwork()
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val config = intent.toServerConfig() ?: return START_NOT_STICKY
                startForegroundWithTypes(
                    buildNotification(
                        host = config.host,
                        status = ConnectionStatus(ConnectionPhase.CONNECTING),
                    ),
                    includeMicrophone = false,
                )
                beginConnection(config)
            }

            ACTION_DISCONNECT -> requestDisconnect()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        connectionEpoch++
        userDisconnectRequested = true
        activeListener = null
        connectionJob?.cancel()
        reconnectJob?.cancel()
        stableConnectionJob?.cancel()
        session?.close()
        session = null
        pushToTalkPressed.set(false)
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
        microphone.close()
        audioPlayer.close()
        audioRouter.close()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun beginConnection(config: ServerConfig) {
        connectionEpoch++
        val epoch = connectionEpoch
        userDisconnectRequested = false
        connectedOnce = false
        reconnectAttempt = 0
        desiredConfig = config
        identityMaterial = null
        lastChannel = null
        restorePending = false
        activeListener = null
        stableConnectionJob?.cancel()
        reconnectJob?.cancel()
        connectionJob?.cancel()

        val selectedMicrophoneMode = mutableState.value.microphoneMode
        val selectedPlaybackMuted = mutableState.value.playbackMuted
        audioPlayer.replaceParticipantGains(emptyMap())
        pushToTalkPressed.set(false)
        mutableState.update { current ->
            TeamSpeakServiceState(
                status = ConnectionStatus(ConnectionPhase.CONNECTING),
                serverLabel = "${config.host}:${config.port}",
                microphoneMode = selectedMicrophoneMode,
                suppressionMode = suppressionModePreferenceState.current(),
                playbackMuted = selectedPlaybackMuted,
                audioRouting = current.audioRouting,
            )
        }
        if (session != null) session?.close()

        connectionJob = serviceScope.launch {
            suppressionModePreferenceReady.await()
            transmitMutex.withLock { stopMicrophoneLocked() }
            when (val result = performConnectionAttempt(config, epoch, reconnecting = false)) {
                AttemptResult.Success,
                AttemptResult.Stale,
                -> Unit

                is AttemptResult.Failed -> {
                    if (connectedOnce && result.retryable) {
                        launchReconnect(result.status, epoch)
                    } else {
                        finishTerminalFailure(result.status, epoch)
                    }
                }
            }
        }
    }

    private suspend fun performConnectionAttempt(
        config: ServerConfig,
        epoch: Long,
        reconnecting: Boolean,
    ): AttemptResult = sessionMutex.withLock {
        if (!isEpochActive(epoch)) return@withLock AttemptResult.Stale

        activeListener = null
        session?.close()
        session = null

        try {
            audioRouter.start()
            val identity = identityMaterial ?: identityVault.getOrCreate().also {
                identityMaterial = it
            }
            mutableState.update { it.copy(identityReady = true) }

            audioPlayer.setMuted(mutableState.value.playbackMuted)
            audioPlayer.replaceParticipantGains(emptyMap())
            audioPlayer.start()

            val newSession = Ts3jSessionClient()
            val listener = SessionListener(epoch, reconnecting)
            activeListener = listener
            session = newSession
            newSession.setVoiceSource(microphone)
            newSession.connect(config, identity, listener)
            if (!isListenerActive(listener)) {
                newSession.close()
                if (session === newSession) session = null
                return@withLock if (isEpochActive(epoch)) {
                    val status = mutableState.value.status
                    AttemptResult.Failed(status, status.retryable)
                } else {
                    AttemptResult.Stale
                }
            }
            if (mutableState.value.status.phase == ConnectionPhase.CONNECTED) {
                AttemptResult.Success
            } else {
                val status = mutableState.value.status
                AttemptResult.Failed(status, status.retryable && reconnecting)
            }
        } catch (error: Throwable) {
            val failedSession = session
            activeListener = null
            failedSession?.close()
            if (session === failedSession) session = null
            if (!isEpochActive(epoch)) return@withLock AttemptResult.Stale

            val status = ConnectionStatus(
                ConnectionPhase.ERROR,
                "Falha na conexão: ${error.conciseMessage()}",
                retryable = error.isRetryableConnectionFailure(),
            )
            mutableState.update { current ->
                current.copy(
                    status = if (reconnecting) {
                        ConnectionStatus(
                            ConnectionPhase.RECONNECTING,
                            "Falha na reconexão: ${status.detail.orEmpty()}".trimEnd(':'),
                            retryable = status.retryable,
                        )
                    } else {
                        status
                    },
                    snapshot = SessionSnapshot.Empty,
                )
            }
            AttemptResult.Failed(status, status.retryable && reconnecting)
        }
    }

    private fun requestDisconnect() {
        userDisconnectRequested = true
        connectionEpoch++
        activeListener = null
        connectionJob?.cancel()
        reconnectJob?.cancel()
        stableConnectionJob?.cancel()
        if (mutableState.value.status.phase in interruptibleConnectionPhases) {
            session?.close()
        }
        serviceScope.launch { disconnect(userInitiated = true) }
    }

    private suspend fun disconnect(userInitiated: Boolean) = sessionMutex.withLock {
        pushToTalkPressed.set(false)
        transmitMutex.withLock { stopMicrophoneLocked() }
        try {
            session?.disconnect(if (userInitiated) "Disconnected by user" else "Service stopped")
        } finally {
            session?.close()
            session = null
            activeListener = null
            audioPlayer.stop()
            audioPlayer.setMuted(false)
            audioRouter.stop()
            mutableState.update { current ->
                TeamSpeakServiceState(
                suppressionMode = suppressionModePreferenceState.current(),
                )
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            if (userInitiated) stopSelf()
        }
    }

    private inner class SessionListener(
        val epoch: Long,
        private val reconnecting: Boolean,
    ) : Ts3SessionListener {
        @Volatile
        var connected = false

        override fun onStatusChanged(status: ConnectionStatus) {
            if (!isListenerActive(this)) return
            when (status.phase) {
                ConnectionPhase.CONNECTING -> {
                    if (!reconnecting) mutableState.update { it.copy(status = status) }
                }

                ConnectionPhase.CONNECTED -> {
                    connected = true
                    onSessionConnected(this)
                }

                ConnectionPhase.DISCONNECTED,
                ConnectionPhase.ERROR,
                -> {
                    if (connected) {
                        onEstablishedSessionEnded(this, status)
                    } else {
                        mutableState.update { current ->
                            current.copy(
                                status = status,
                                snapshot = SessionSnapshot.Empty,
                                isTransmitting = false,
                            )
                        }
                    }
                }

                ConnectionPhase.DISCONNECTING -> {
                    mutableState.update { it.copy(status = status) }
                    updateNotification()
                }

                ConnectionPhase.RECONNECTING -> Unit
            }
        }

        override fun onSnapshotChanged(snapshot: SessionSnapshot) {
            if (!isListenerActive(this)) return
            mutableState.update { it.copy(snapshot = snapshot) }
            applyParticipantAudioSettings(snapshot)
            if (connected && !restorePending) {
                snapshot.currentChannelId?.let { channelId ->
                    if (lastChannel?.id != channelId) lastChannel = ChannelTarget(channelId, "")
                }
            }
            updateNotification()
        }

        override fun onVoiceFrame(frame: VoiceFrame) {
            if (isListenerActive(this) && connected) audioPlayer.submit(frame)
        }
    }

    private fun onSessionConnected(listener: SessionListener) {
        if (!isListenerActive(listener)) return
        connectedOnce = true
        mutableState.update { current ->
            current.copy(
                status = ConnectionStatus(ConnectionPhase.CONNECTED),
                switchingChannelId = null,
                channelError = null,
            )
        }
        if (lastChannel == null) {
            mutableState.value.snapshot.currentChannelId?.let {
                lastChannel = ChannelTarget(it, "")
            }
        }
        scheduleStableConnectionReset(listener)
        updateNotification()
        reconcileMicrophone()
        restoreLastChannelIfNeeded(listener)
    }

    private fun onEstablishedSessionEnded(
        listener: SessionListener,
        status: ConnectionStatus,
    ) {
        if (!isListenerActive(listener)) return
        activeListener = null
        stableConnectionJob?.cancel()
        pushToTalkPressed.set(false)
        microphone.stop()
        audioPlayer.stop()

        if (status.retryable && connectedOnce && !userDisconnectRequested) {
            launchReconnect(status, listener.epoch)
        } else {
            serviceScope.launch { finishTerminalFailure(status, listener.epoch) }
        }
    }

    private fun launchReconnect(cause: ConnectionStatus, epoch: Long) {
        if (!isEpochActive(epoch) || userDisconnectRequested) return
        val detail = if (networkAvailable.value) {
            "Conexão interrompida; preparando reconexão automática: ${cause.detail.orEmpty()}".trimEnd(':')
        } else {
            WAITING_FOR_NETWORK_DETAIL
        }
        mutableState.update { current ->
            current.copy(
                status = ConnectionStatus(
                    ConnectionPhase.RECONNECTING,
                    detail,
                    retryable = true,
                ),
                snapshot = SessionSnapshot.Empty,
                isTransmitting = false,
                switchingChannelId = null,
            )
        }
        updateNotification()
        if (reconnectJob?.isActive == true) return

        val job = serviceScope.launch {
            suspendAudioForReconnect()
            reconnectLoop(epoch)
        }
        reconnectJob = job
        job.invokeOnCompletion {
            if (reconnectJob === job) reconnectJob = null
        }
    }

    private suspend fun reconnectLoop(epoch: Long) {
        val config = desiredConfig ?: return
        while (isEpochActive(epoch) && !userDisconnectRequested) {
            if (!networkAvailable.value) {
                mutableState.update { current ->
                    current.copy(
                        status = ConnectionStatus(
                            ConnectionPhase.RECONNECTING,
                            WAITING_FOR_NETWORK_DETAIL,
                            retryable = true,
                        ),
                    )
                }
                updateNotification()
                networkAvailable.first { it }
            }
            if (!isEpochActive(epoch) || userDisconnectRequested) return

            val attempt = reconnectAttempt + 1
            val delayMs = reconnectPolicy.delayForAttempt(attempt)
            mutableState.update { current ->
                current.copy(
                    status = ConnectionStatus(
                        ConnectionPhase.RECONNECTING,
                        "Nova tentativa de conexão em ${delayMs / 1_000} s (tentativa $attempt)",
                        retryable = true,
                    ),
                )
            }
            updateNotification()
            delay(delayMs)
            if (!networkAvailable.value) continue
            if (!isEpochActive(epoch) || userDisconnectRequested) return

            reconnectAttempt = attempt
            mutableState.update { current ->
                current.copy(
                    status = ConnectionStatus(
                        ConnectionPhase.RECONNECTING,
                        "Realizando a tentativa de reconexão $attempt",
                        retryable = true,
                    ),
                )
            }
            updateNotification()

            when (val result = performConnectionAttempt(config, epoch, reconnecting = true)) {
                AttemptResult.Success -> return
                AttemptResult.Stale -> return
                is AttemptResult.Failed -> {
                    if (!result.retryable) {
                        finishTerminalFailure(result.status, epoch)
                        return
                    }
                    suspendAudioForReconnect()
                }
            }
        }
    }

    private suspend fun suspendAudioForReconnect() {
        pushToTalkPressed.set(false)
        transmitMutex.withLock { stopMicrophoneLocked() }
        audioPlayer.stop()
    }

    private suspend fun finishTerminalFailure(status: ConnectionStatus, epoch: Long) {
        if (!isEpochActive(epoch)) return
        activeListener = null
        stableConnectionJob?.cancel()
        sessionMutex.withLock {
            if (!isEpochActive(epoch)) return@withLock
            session?.close()
            session = null
        }
        pushToTalkPressed.set(false)
        transmitMutex.withLock { microphone.stop() }
        audioPlayer.stop()
        audioRouter.stop()
        mutableState.update { current ->
            current.copy(
                status = status.copy(retryable = false),
                snapshot = SessionSnapshot.Empty,
                isTransmitting = false,
                switchingChannelId = null,
            )
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun scheduleStableConnectionReset(listener: SessionListener) {
        stableConnectionJob?.cancel()
        stableConnectionJob = serviceScope.launch {
            delay(STABLE_CONNECTION_MS)
            if (isListenerActive(listener) && listener.connected) reconnectAttempt = 0
        }
    }

    private fun restoreLastChannelIfNeeded(listener: SessionListener) {
        val target = lastChannel ?: return
        if (mutableState.value.snapshot.currentChannelId == target.id) return
        restorePending = true
        mutableState.update { it.copy(switchingChannelId = target.id) }
        serviceScope.launch {
            try {
                sessionMutex.withLock {
                    check(isListenerActive(listener) && listener.connected) { "A conexão não está mais ativa" }
                    session?.joinChannel(target.id, target.password) ?: error("A conexão não está mais ativa")
                }
                mutableState.update {
                    it.copy(switchingChannelId = null, channelError = null)
                }
            } catch (error: Throwable) {
                if (isEpochActive(listener.epoch)) {
                    mutableState.update {
                        it.copy(
                            switchingChannelId = null,
                            channelError = "Falha ao restaurar o canal: ${error.conciseMessage()}",
                        )
                    }
                }
            } finally {
                restorePending = false
            }
        }
    }

    private fun isListenerActive(listener: SessionListener): Boolean =
        activeListener === listener && isEpochActive(listener.epoch)

    private fun isEpochActive(epoch: Long): Boolean =
        epoch == connectionEpoch && !userDisconnectRequested

    private fun hasUsableNetwork(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun updateNetworkAvailability(available: Boolean) {
        networkAvailable.value = available
        if (!available && mutableState.value.status.phase == ConnectionPhase.CONNECTED) {
            activeListener?.takeIf { it.connected }?.let { listener ->
                onEstablishedSessionEnded(
                    listener,
                    ConnectionStatus(
                        ConnectionPhase.DISCONNECTED,
                        "Conexão de rede perdida",
                        retryable = true,
                    ),
                )
            }
        }
        if (!available && mutableState.value.status.phase == ConnectionPhase.RECONNECTING) {
            mutableState.update { current ->
                current.copy(
                    status = ConnectionStatus(
                        ConnectionPhase.RECONNECTING,
                        WAITING_FOR_NETWORK_DETAIL,
                        retryable = true,
                    ),
                )
            }
            updateNotification()
        }
    }

    private fun setParticipantMuted(key: String, muted: Boolean) {
        updateParticipantAudioSettings(key) { copy(muted = muted) }
    }

    private fun setParticipantVolume(key: String, volumePercent: Int) {
        updateParticipantAudioSettings(key) {
            copy(volumePercent = volumePercent.coerceIn(0, MAX_PARTICIPANT_VOLUME_PERCENT))
        }
    }

    private inline fun updateParticipantAudioSettings(
        key: String,
        transform: ParticipantAudioSettings.() -> ParticipantAudioSettings,
    ) {
        if (key.isBlank()) return
        mutableState.update { current ->
            val previous = current.participantAudioSettings[key] ?: ParticipantAudioSettings()
            val updated = previous.transform()
            if (updated == previous) return@update current
            val settings = current.participantAudioSettings.toMutableMap()
            if (updated == ParticipantAudioSettings()) {
                settings.remove(key)
            } else {
                settings[key] = updated
            }
            current.copy(participantAudioSettings = settings)
        }
        applyParticipantAudioSettings(mutableState.value.snapshot)
    }

    private fun applyParticipantAudioSettings(snapshot: SessionSnapshot) {
        val settings = mutableState.value.participantAudioSettings
        audioPlayer.replaceParticipantGains(
            snapshot.participants.associate { participant ->
                participant.id to (settings[participant.audioControlKey()]?.gain ?: 1f)
            },
        )
    }

    private fun setMicrophoneMode(mode: MicrophoneMode) {
        if (
            mode == MicrophoneMode.CONTINUOUS &&
            !hasMicrophonePermission()
        ) {
            mutableState.update {
                it.copy(microphoneError = "É necessária permissão do microfone para ativar o modo sempre ligado")
            }
            return
        }
        if (mode != MicrophoneMode.PUSH_TO_TALK) pushToTalkPressed.set(false)
        mutableState.update {
            it.copy(microphoneMode = mode, microphoneError = null)
        }
        reconcileMicrophone()
    }

    private fun setSuppressionMode(mode: SuppressionMode) {
        val current = suppressionModePreferenceState.request(mode)
        microphone.setSuppressionMode(current)
        mutableState.update { it.copy(suppressionMode = current) }
        audioPreferences.setSuppressionMode(current)
    }

    private fun setPushToTalkPressed(pressed: Boolean) {
        if (mutableState.value.microphoneMode != MicrophoneMode.PUSH_TO_TALK) {
            pushToTalkPressed.set(false)
            return
        }
        pushToTalkPressed.set(pressed)
        if (!pressed) {
            mutableState.update { it.copy(isTransmitting = false) }
        }
        reconcileMicrophone()
    }

    private fun reconcileMicrophone() {
        serviceScope.launch {
            transmitMutex.withLock {
                if (!shouldCaptureMicrophone()) {
                    stopMicrophoneLocked()
                    return@withLock
                }

                if (!hasMicrophonePermission()) {
                    pushToTalkPressed.set(false)
                    mutableState.update {
                        it.copy(
                            isTransmitting = false,
                            microphoneError = "Permissão do microfone não concedida",
                        )
                    }
                    return@withLock
                }

                try {
                    updateForegroundType(includeMicrophone = true)
                    microphone.start()
                    if (!shouldCaptureMicrophone()) {
                        stopMicrophoneLocked()
                    } else {
                        mutableState.update {
                            it.copy(isTransmitting = true, microphoneError = null)
                        }
                        updateNotification()
                    }
                } catch (error: Throwable) {
                    pushToTalkPressed.set(false)
                    microphone.stop()
                    mutableState.update {
                        it.copy(
                            isTransmitting = false,
                            microphoneError = "Falha ao iniciar o microfone: ${error.conciseMessage()}",
                        )
                    }
                    updateForegroundType(includeMicrophone = false)
                }
            }
        }
    }

    private fun shouldCaptureMicrophone(): Boolean {
        val current = mutableState.value
        if (current.status.phase != ConnectionPhase.CONNECTED) return false
        return when (current.microphoneMode) {
            MicrophoneMode.OFF -> false
            MicrophoneMode.PUSH_TO_TALK -> pushToTalkPressed.get()
            MicrophoneMode.CONTINUOUS -> true
        }
    }

    private fun hasMicrophonePermission(): Boolean = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED

    private fun stopMicrophoneLocked() {
        microphone.stop()
        mutableState.update { it.copy(isTransmitting = false) }
        updateForegroundType(includeMicrophone = false)
    }

    private fun onMicrophoneFailure(error: Throwable) {
        pushToTalkPressed.set(false)
        mutableState.update {
            it.copy(
                isTransmitting = false,
                microphoneError = "Falha no microfone: ${error.conciseMessage()}",
            )
        }
        serviceScope.launch {
            transmitMutex.withLock { stopMicrophoneLocked() }
        }
    }

    private fun onAudioRoutingChanged(routing: AudioRoutingState) {
        audioPlayer.setPreferredDevice(audioRouter.preferredOutputDevice())
        microphone.setPreferredDevice(audioRouter.preferredInputDevice())
        mutableState.update { it.copy(audioRouting = routing) }
    }

    private fun updateForegroundType(includeMicrophone: Boolean) {
        val current = mutableState.value
        if (current.status.phase !in foregroundPhases) return
        val host = current.serverLabel ?: return
        startForegroundWithTypes(
            notification = buildNotification(
                host = host,
                status = current.status,
                onlineCount = current.snapshot.participants.size,
                microphoneActive = includeMicrophone,
            ),
            includeMicrophone = includeMicrophone,
        )
    }

    @SuppressLint("InlinedApi")
    private fun startForegroundWithTypes(
        notification: android.app.Notification,
        includeMicrophone: Boolean,
    ) {
        val baseTypes = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        val types = baseTypes or if (includeMicrophone) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, types)
    }

    private fun updateNotification() {
        val current = mutableState.value
        if (current.status.phase !in foregroundPhases) return
        val manager = getSystemService(NotificationManager::class.java)
        val host = current.serverLabel ?: return
        manager.notify(
            NOTIFICATION_ID,
            buildNotification(
                host = host,
                status = current.status,
                onlineCount = current.snapshot.participants.size,
                microphoneActive = current.isTransmitting,
            ),
        )
    }

    private fun buildNotification(
        host: String,
        status: ConnectionStatus,
        onlineCount: Int = 0,
        microphoneActive: Boolean = false,
    ) = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_app)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(
            if (microphoneActive) {
                getString(R.string.notification_microphone_active, host, onlineCount)
            } else {
                when (status.phase) {
                    ConnectionPhase.CONNECTING -> getString(R.string.notification_connecting, host)
                    ConnectionPhase.RECONNECTING -> status.detail
                        ?: getString(R.string.notification_reconnecting, host)
                    ConnectionPhase.DISCONNECTING -> getString(R.string.notification_disconnecting, host)
                    ConnectionPhase.CONNECTED -> resources.getQuantityString(
                        R.plurals.notification_connected,
                        onlineCount,
                        host,
                        onlineCount,
                    )
                    ConnectionPhase.DISCONNECTED,
                    ConnectionPhase.ERROR,
                    -> host
                }
            },
        )
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .addAction(
            0,
            getString(R.string.notification_disconnect),
            PendingIntent.getService(
                this,
                1,
                Intent(this, TeamSpeakService::class.java).setAction(ACTION_DISCONNECT),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .build()

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private fun Intent.toServerConfig(): ServerConfig? {
        val host = getStringExtra(EXTRA_HOST) ?: return null
        val nickname = getStringExtra(EXTRA_NICKNAME) ?: return null
        return ServerConfig(
            host = host,
            port = getIntExtra(EXTRA_PORT, 9987),
            nickname = nickname,
            password = getStringExtra(EXTRA_PASSWORD).orEmpty(),
        )
    }

    private fun Throwable.conciseMessage(): String {
        var cursor: Throwable? = this
        while (cursor != null) {
            cursor.message?.takeIf(String::isNotBlank)?.let { return it.take(180) }
            cursor = cursor.cause
        }
        return this::class.java.simpleName
    }

    private fun joinChannel(channelId: Int, password: String) {
        val current = mutableState.value
        if (current.status.phase != ConnectionPhase.CONNECTED) return
        if (current.snapshot.currentChannelId == channelId) return
        mutableState.update {
            it.copy(switchingChannelId = channelId, channelError = null)
        }
        serviceScope.launch {
            try {
                sessionMutex.withLock {
                    check(mutableState.value.status.phase == ConnectionPhase.CONNECTED) {
                        "Não conectado ao servidor"
                    }
                    session?.joinChannel(channelId, password)
                        ?: error("Não conectado ao servidor")
                }
                lastChannel = ChannelTarget(channelId, password)
                mutableState.update { state ->
                    state.copy(switchingChannelId = null, channelError = null)
                }
            } catch (error: Throwable) {
                mutableState.update { state ->
                    state.copy(
                        switchingChannelId = null,
                        channelError = "Falha ao trocar de canal: ${error.conciseMessage()}",
                    )
                }
            }
        }
    }

    inner class SessionBinder : Binder() {
        val state: StateFlow<TeamSpeakServiceState>
            get() = this@TeamSpeakService.state

        fun setPlaybackMuted(muted: Boolean) {
            audioPlayer.setMuted(muted)
            mutableState.update { it.copy(playbackMuted = muted) }
        }

        fun setParticipantMuted(key: String, muted: Boolean) {
            this@TeamSpeakService.setParticipantMuted(key, muted)
        }

        fun setParticipantVolume(key: String, volumePercent: Int) {
            this@TeamSpeakService.setParticipantVolume(key, volumePercent)
        }

        fun selectAudioRoute(routeId: Int) {
            audioRouter.selectRoute(routeId)
        }

        fun setMicrophoneMode(mode: MicrophoneMode) {
            this@TeamSpeakService.setMicrophoneMode(mode)
        }

        fun setSuppressionMode(mode: SuppressionMode) {
            this@TeamSpeakService.setSuppressionMode(mode)
        }

        fun setPushToTalkPressed(pressed: Boolean) {
            this@TeamSpeakService.setPushToTalkPressed(pressed)
        }

        fun releasePushToTalk() {
            this@TeamSpeakService.setPushToTalkPressed(false)
        }

        fun joinChannel(channelId: Int, password: String = "") {
            this@TeamSpeakService.joinChannel(channelId, password)
        }

        fun reportMicrophonePermissionDenied() {
            pushToTalkPressed.set(false)
            mutableState.update {
                it.copy(
                    isTransmitting = false,
                    microphoneError = "É necessária permissão do microfone para enviar áudio",
                )
            }
        }
    }

    private sealed interface AttemptResult {
        data object Success : AttemptResult
        data object Stale : AttemptResult
        data class Failed(
            val status: ConnectionStatus,
            val retryable: Boolean,
        ) : AttemptResult
    }

    private data class ChannelTarget(
        val id: Int,
        val password: String,
    )

    companion object {
        private const val ACTION_CONNECT = "io.github.ts3mobile.action.CONNECT"
        private const val ACTION_DISCONNECT = "io.github.ts3mobile.action.DISCONNECT"
        private const val EXTRA_HOST = "host"
        private const val EXTRA_PORT = "port"
        private const val EXTRA_NICKNAME = "nickname"
        private const val EXTRA_PASSWORD = "password"
        private const val NOTIFICATION_CHANNEL_ID = "ts3_connection"
        private const val NOTIFICATION_ID = 4103
        private const val STABLE_CONNECTION_MS = 30_000L
        private const val WAITING_FOR_NETWORK_DETAIL = "Rede indisponível; reconexão automática quando restabelecida"
        private const val MAX_PARTICIPANT_VOLUME_PERCENT = 200
        private val foregroundPhases = setOf(
            ConnectionPhase.CONNECTING,
            ConnectionPhase.RECONNECTING,
            ConnectionPhase.CONNECTED,
            ConnectionPhase.DISCONNECTING,
        )
        private val interruptibleConnectionPhases = setOf(
            ConnectionPhase.CONNECTING,
            ConnectionPhase.RECONNECTING,
        )

        fun connect(context: Context, config: ServerConfig) {
            val intent = Intent(context, TeamSpeakService::class.java)
                .setAction(ACTION_CONNECT)
                .putExtra(EXTRA_HOST, config.host)
                .putExtra(EXTRA_PORT, config.port)
                .putExtra(EXTRA_NICKNAME, config.nickname)
                .putExtra(EXTRA_PASSWORD, config.password)
            ContextCompat.startForegroundService(context, intent)
        }

        fun disconnect(context: Context) {
            context.startService(
                Intent(context, TeamSpeakService::class.java).setAction(ACTION_DISCONNECT),
            )
        }
    }
}
