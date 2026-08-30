package io.github.ts3mobile.app

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ts3mobile.app.service.TeamSpeakService
import io.github.ts3mobile.app.service.TeamSpeakServiceState
import io.github.ts3mobile.app.service.MicrophoneMode
import io.github.ts3mobile.audio.opus.SuppressionMode
import io.github.ts3mobile.app.ui.MainScreen
import io.github.ts3mobile.app.ui.theme.Ts3MobileTheme
import io.github.ts3mobile.protocol.ServerConfig
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()
    private var serviceBinder by mutableStateOf<TeamSpeakService.SessionBinder?>(null)
    private var isBound = false
    private var pendingConnection: ServerConfig? = null
    private var pendingMicrophoneMode: MicrophoneMode? = null
    private var pushToTalkPressed = false

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        pendingConnection?.let { TeamSpeakService.connect(this, it) }
        pendingConnection = null
    }

    private val microphonePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val requestedMode = pendingMicrophoneMode
        pendingMicrophoneMode = null
        if (granted && requestedMode != null) {
            serviceBinder?.setMicrophoneMode(requestedMode)
        } else if (granted && pushToTalkPressed) {
            serviceBinder?.setPushToTalkPressed(true)
        } else if (!granted) {
            pushToTalkPressed = false
            serviceBinder?.reportMicrophonePermissionDenied()
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            serviceBinder = binder as? TeamSpeakService.SessionBinder
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBinder = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Ts3MobileTheme {
                val fallbackState = remember { MutableStateFlow(TeamSpeakServiceState()) }
                val serviceState by (serviceBinder?.state ?: fallbackState)
                    .collectAsStateWithLifecycle()
                val form by viewModel.form.collectAsStateWithLifecycle()

                MainScreen(
                    form = form,
                    serviceState = serviceState,
                    onHostChanged = viewModel::setHost,
                    onPortChanged = viewModel::setPort,
                    onNicknameChanged = viewModel::setNickname,
                    onPasswordChanged = viewModel::setPassword,
                    onConnect = {
                        viewModel.submit()?.let(::requestConnection)
                    },
                    onDisconnect = {
                        TeamSpeakService.disconnect(this)
                    },
                    onPlaybackMutedChange = { muted ->
                        serviceBinder?.setPlaybackMuted(muted)
                    },
                    onParticipantMutedChange = { key, muted ->
                        serviceBinder?.setParticipantMuted(key, muted)
                    },
                    onParticipantVolumeChange = { key, volumePercent ->
                        serviceBinder?.setParticipantVolume(key, volumePercent)
                    },
                    onAudioRouteSelected = { routeId ->
                        serviceBinder?.selectAudioRoute(routeId)
                    },
                    onMicrophoneModeChanged = ::setMicrophoneMode,
                    onPushToTalkChanged = ::setPushToTalkPressed,
                    suppressionMode = serviceState.suppressionMode,
                    onSuppressionModeChanged = ::onSuppressionModeChanged,
                    onJoinChannel = { channelId, password ->
                        serviceBinder?.joinChannel(channelId, password)
                    },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        isBound = bindService(
            Intent(this, TeamSpeakService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE,
        )
    }

    override fun onPause() {
        pushToTalkPressed = false
        serviceBinder?.releasePushToTalk()
        super.onPause()
    }

    override fun onStop() {
        pushToTalkPressed = false
        serviceBinder?.releasePushToTalk()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
            serviceBinder = null
        }
        super.onStop()
    }

    private fun requestConnection(config: ServerConfig) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingConnection = config
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            TeamSpeakService.connect(this, config)
        }
    }

    private fun setPushToTalkPressed(pressed: Boolean) {
        pushToTalkPressed = pressed
        if (!pressed) {
            serviceBinder?.setPushToTalkPressed(false)
            return
        }

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        ) {
            serviceBinder?.setPushToTalkPressed(true)
        } else {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun setMicrophoneMode(mode: MicrophoneMode) {
        pushToTalkPressed = false
        serviceBinder?.releasePushToTalk()
        if (
            mode == MicrophoneMode.CONTINUOUS &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingMicrophoneMode = mode
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            pendingMicrophoneMode = null
            serviceBinder?.setMicrophoneMode(mode)
        }
    }

    private fun onSuppressionModeChanged(mode: SuppressionMode) {
        serviceBinder?.setSuppressionMode(mode)
    }
}
