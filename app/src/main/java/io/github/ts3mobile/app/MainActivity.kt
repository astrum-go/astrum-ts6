package io.github.ts3mobile.app

import android.Manifest
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Rational
import android.view.WindowManager
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
    private var isLandscape by mutableStateOf(false)
    private var isInPip by mutableStateOf(false)

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

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            serviceBinder?.startCameraBroadcast()
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        setContent {
            Ts3MobileTheme {
                val fallbackState = remember { MutableStateFlow(TeamSpeakServiceState()) }
                val serviceState by (serviceBinder?.state ?: fallbackState)
                    .collectAsStateWithLifecycle()
                val savedServers by viewModel.savedServers.collectAsStateWithLifecycle()
                val lastSelectedServerId by viewModel.lastSelectedServerId.collectAsStateWithLifecycle()
                val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
                val quickForm by viewModel.quickForm.collectAsStateWithLifecycle()
                val editingServer by viewModel.editingServer.collectAsStateWithLifecycle()
                val isCreatingNew by viewModel.isCreatingNew.collectAsStateWithLifecycle()

                MainScreen(
                    savedServers = savedServers,
                    lastSelectedServerId = lastSelectedServerId,
                    activeTab = activeTab,
                    onTabChanged = viewModel::setActiveTab,
                    quickForm = quickForm,
                    onQuickHostChanged = viewModel::setQuickHost,
                    onQuickPortChanged = viewModel::setQuickPort,
                    onQuickNicknameChanged = viewModel::setQuickNickname,
                    onQuickPasswordChanged = viewModel::setQuickPassword,
                    onQuickSaveToListChanged = viewModel::setQuickSaveToList,
                    onQuickServerNameChanged = viewModel::setQuickServerName,
                    onQuickConnect = {
                        viewModel.submitQuickConnect()?.let(::requestConnection)
                    },
                    onConnectToSavedServer = { server ->
                        requestConnection(viewModel.connectToSavedServer(server))
                    },
                    onOpenAddServer = viewModel::openAddServerDialog,
                    onOpenEditServer = viewModel::openEditServerDialog,
                    editingServer = editingServer,
                    isCreatingNew = isCreatingNew,
                    onDismissEditor = viewModel::dismissEditorDialog,
                    onSaveServer = { server, connectImmediately ->
                        val config = viewModel.saveEditingServer(server, connectImmediately)
                        if (connectImmediately && config != null) {
                            requestConnection(config)
                        }
                    },
                    onDeleteServer = viewModel::deleteServer,
                    serviceState = serviceState,
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
                    webRtcManager = serviceBinder?.webRtc,
                    onToggleCameraBroadcast = {
                        if (serviceState.isBroadcastingCamera) {
                            serviceBinder?.stopCameraBroadcast()
                        } else {
                            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                serviceBinder?.startCameraBroadcast()
                            } else {
                                cameraPermission.launch(Manifest.permission.CAMERA)
                            }
                        }
                    },
                    onSwitchCamera = {
                        serviceBinder?.switchCamera()
                    },
                    onWatchStream = { clientId, streamId ->
                        serviceBinder?.watchStream(clientId, streamId)
                    },
                    onStopWatchingStream = { streamId ->
                        serviceBinder?.stopWatchingStream(streamId)
                    },
                    onAcceptViewer = { viewer ->
                        serviceBinder?.acceptViewerRequest(viewer)
                    },
                    onRejectViewer = { viewer ->
                        serviceBinder?.rejectViewerRequest(viewer)
                    },
                    onToggleAutoAcceptViewers = {
                        serviceBinder?.toggleAutoAcceptViewers()
                    },
                    isLandscape = isLandscape,
                    onToggleOrientation = ::setOrientation,
                    isInPip = isInPip,
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

    private fun setOrientation(landscape: Boolean) {
        isLandscape = landscape
        requestedOrientation = if (landscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPip = isInPictureInPictureMode
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val hasStream = serviceBinder?.state?.value?.watchingStreams?.isNotEmpty() == true
            || serviceBinder?.state?.value?.watchingStreamId != null
        if (hasStream && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (_: Exception) {
            }
        }
    }
}
