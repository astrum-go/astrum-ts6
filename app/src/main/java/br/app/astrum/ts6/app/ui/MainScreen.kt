package br.app.astrum.ts6.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import br.app.astrum.ts6.app.R
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ScreenShare
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.automirrored.filled.StopScreenShare
import androidx.compose.material.icons.automirrored.outlined.ScreenShare
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.Color
import br.app.astrum.ts6.app.service.StreamViewer
import br.app.astrum.ts6.app.service.WatchedStream
import br.app.astrum.ts6.app.video.WebRtcManager
import br.app.astrum.ts6.app.video.WebRtcVideoView
import br.app.astrum.ts6.protocol.StreamPreset
import br.app.astrum.ts6.protocol.StreamType
import br.app.astrum.ts6.protocol.Ts6StreamInfo
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.VideoTrack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.app.astrum.ts6.app.ConnectionFormState
import br.app.astrum.ts6.app.MainTab
import br.app.astrum.ts6.app.data.SavedServer
import br.app.astrum.ts6.app.service.MicrophoneMode
import br.app.astrum.ts6.app.service.ParticipantAudioSettings
import br.app.astrum.ts6.app.service.TeamSpeakServiceState
import br.app.astrum.ts6.app.service.audioControlKey
import br.app.astrum.ts6.audio.opus.AudioRoutingState
import br.app.astrum.ts6.audio.opus.SuppressionMode
import br.app.astrum.ts6.protocol.ChannelTree
import br.app.astrum.ts6.protocol.ConnectionPhase
import br.app.astrum.ts6.protocol.Ts3Participant
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    savedServers: List<SavedServer>,
    lastSelectedServerId: String?,
    activeTab: MainTab,
    onTabChanged: (MainTab) -> Unit,
    quickForm: ConnectionFormState,
    onQuickHostChanged: (String) -> Unit,
    onQuickPortChanged: (String) -> Unit,
    onQuickNicknameChanged: (String) -> Unit,
    onQuickPasswordChanged: (String) -> Unit,
    onQuickSaveToListChanged: (Boolean) -> Unit,
    onQuickServerNameChanged: (String) -> Unit,
    onQuickConnect: () -> Unit,
    onConnectToSavedServer: (SavedServer) -> Unit,
    onOpenAddServer: () -> Unit,
    onOpenEditServer: (SavedServer) -> Unit,
    editingServer: SavedServer?,
    isCreatingNew: Boolean,
    onDismissEditor: () -> Unit,
    onSaveServer: (SavedServer, Boolean) -> Unit,
    onDeleteServer: (String) -> Unit,
    serviceState: TeamSpeakServiceState,
    onDisconnect: () -> Unit,
    onPlaybackMutedChange: (Boolean) -> Unit,
    onParticipantMutedChange: (String, Boolean) -> Unit,
    onParticipantVolumeChange: (String, Int) -> Unit,
    onAudioRouteSelected: (Int) -> Unit,
    onMicrophoneModeChanged: (MicrophoneMode) -> Unit,
    onPushToTalkChanged: (Boolean) -> Unit,
    onJoinChannel: (Int, String) -> Unit,
    suppressionMode: SuppressionMode = SuppressionMode.ASTRUM_CLARITY,
    onSuppressionModeChanged: (SuppressionMode) -> Unit = {},
    webRtcManager: WebRtcManager? = null,
    onStartCameraBroadcast: (StreamPreset) -> Unit = {},
    onStopCameraBroadcast: () -> Unit = {},
    onStartScreenBroadcast: (StreamPreset, Boolean) -> Unit = { _, _ -> },
    onStopScreenBroadcast: () -> Unit = {},
    onSwitchCamera: () -> Unit = {},
    onWatchStream: (remoteClientId: Int, streamId: String) -> Unit = { _, _ -> },
    onStopWatchingStream: (String?) -> Unit = {},
    onAcceptViewer: (StreamViewer) -> Unit = {},
    onRejectViewer: (StreamViewer) -> Unit = {},
    onToggleAutoAcceptViewers: () -> Unit = {},
    isLandscape: Boolean = false,
    onToggleOrientation: (Boolean) -> Unit = {},
    isInPip: Boolean = false,
) {
    if (isInPip) {
        val stream = serviceState.watchingStreams.lastOrNull()
        val videoTrack = stream?.let { webRtcManager?.remoteVideoTracks?.value?.get(it.streamId) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (videoTrack != null && webRtcManager != null) {
                WebRtcVideoView(
                    videoTrack = videoTrack,
                    eglBaseContext = webRtcManager.eglBase.eglBaseContext,
                    scalingType = RendererCommon.ScalingType.SCALE_ASPECT_FIT,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        return
    }

    var fullscreenStreamId by rememberSaveable { mutableStateOf<String?>(null) }
    var streamDialogType by rememberSaveable { mutableStateOf<StreamDialogType?>(null) }

    streamDialogType?.let { type ->
        StreamConfigDialog(
            type = type,
            initialPreset = serviceState.currentStreamPreset,
            onDismiss = { streamDialogType = null },
            onConfirm = { preset, shareAudio ->
                streamDialogType = null
                if (type == StreamDialogType.CAMERA) {
                    onStartCameraBroadcast(preset)
                } else {
                    onStartScreenBroadcast(preset, shareAudio)
                }
            },
        )
    }

    val activeWatchedStreams = serviceState.watchingStreams.ifEmpty {
        serviceState.watchingStreamId?.let { sId ->
            val streamer = serviceState.snapshot.participants.firstOrNull { it.id == serviceState.watchingStreamClientId }
            val streamInfo = serviceState.snapshot.activeStreams.firstOrNull { it.streamId == sId }
            listOf(
                WatchedStream(
                    streamId = sId,
                    clientId = serviceState.watchingStreamClientId ?: 0,
                    nickname = streamer?.nickname ?: "Vídeo",
                    type = streamInfo?.type ?: StreamType.CAMERA,
                    name = streamInfo?.description.orEmpty().ifEmpty {
                        when (streamInfo?.type) {
                            StreamType.SCREEN -> "Tela"
                            StreamType.WINDOW -> "Janela"
                            else -> "Câmera"
                        }
                    },
                )
            )
        } ?: emptyList()
    }

    val fullscreenStream = activeWatchedStreams.firstOrNull { it.streamId == fullscreenStreamId }
    if (fullscreenStream != null && webRtcManager != null) {
        val remoteVideoTracks by webRtcManager.remoteVideoTracks.collectAsStateWithLifecycle()
        FullscreenStreamOverlay(
            stream = fullscreenStream,
            allWatchedStreams = activeWatchedStreams,
            videoTrack = remoteVideoTracks[fullscreenStream.streamId],
            eglBaseContext = webRtcManager.eglBase.eglBaseContext,
            onClose = { fullscreenStreamId = null },
            onSelectStream = { fullscreenStreamId = it },
            onToggleOrientation = onToggleOrientation,
            isLandscape = isLandscape,
        )
        return
    }

    var serverToDelete by remember { mutableStateOf<SavedServer?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_app_logo),
                            contentDescription = "Logo",
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                        Text(
                            text = "Astrum TS6",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                actions = {
                    StatusIndicator(serviceState.status.phase)
                    Spacer(Modifier.width(16.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            serviceState.status.detail?.let { detail ->
                StatusMessage(serviceState.status.phase, detail)
            }
            serviceState.microphoneError?.let { detail ->
                StatusMessage(ConnectionPhase.ERROR, detail)
            }
            serviceState.channelError?.let { detail ->
                StatusMessage(ConnectionPhase.ERROR, detail)
            }
            serviceState.audioRouting.error?.let { detail ->
                StatusMessage(ConnectionPhase.ERROR, detail)
            }

            if (serviceState.status.phase == ConnectionPhase.CONNECTED) {
                ConnectedContent(
                    state = serviceState,
                    onDisconnect = onDisconnect,
                    onPlaybackMutedChange = onPlaybackMutedChange,
                    onParticipantMutedChange = onParticipantMutedChange,
                    onParticipantVolumeChange = onParticipantVolumeChange,
                    onAudioRouteSelected = onAudioRouteSelected,
                    onMicrophoneModeChanged = onMicrophoneModeChanged,
                    onPushToTalkChanged = onPushToTalkChanged,
                    suppressionMode = suppressionMode,
                    onSuppressionModeChanged = onSuppressionModeChanged,
                    onJoinChannel = onJoinChannel,
                    webRtcManager = webRtcManager,
                    onRequestCameraBroadcast = { streamDialogType = StreamDialogType.CAMERA },
                    onStopCameraBroadcast = onStopCameraBroadcast,
                    onRequestScreenBroadcast = { streamDialogType = StreamDialogType.SCREEN },
                    onStopScreenBroadcast = onStopScreenBroadcast,
                    onSwitchCamera = onSwitchCamera,
                    onWatchStream = onWatchStream,
                    onStopWatchingStream = onStopWatchingStream,
                    onAcceptViewer = onAcceptViewer,
                    onRejectViewer = onRejectViewer,
                    onToggleAutoAcceptViewers = onToggleAutoAcceptViewers,
                    isLandscape = isLandscape,
                    onToggleOrientation = onToggleOrientation,
                    onToggleFullscreen = { fullscreenStreamId = it },
                )
            } else {
                DisconnectedContent(
                    savedServers = savedServers,
                    lastSelectedServerId = lastSelectedServerId,
                    activeTab = activeTab,
                    onTabChanged = onTabChanged,
                    quickForm = quickForm,
                    onQuickHostChanged = onQuickHostChanged,
                    onQuickPortChanged = onQuickPortChanged,
                    onQuickNicknameChanged = onQuickNicknameChanged,
                    onQuickPasswordChanged = onQuickPasswordChanged,
                    onQuickSaveToListChanged = onQuickSaveToListChanged,
                    onQuickServerNameChanged = onQuickServerNameChanged,
                    onQuickConnect = onQuickConnect,
                    onConnectToSavedServer = onConnectToSavedServer,
                    onOpenAddServer = onOpenAddServer,
                    onOpenEditServer = onOpenEditServer,
                    onRequestDeleteServer = { serverToDelete = it },
                    phase = serviceState.status.phase,
                    onCancelConnection = onDisconnect,
                )
            }
        }
    }

    // Diálogo de Adicionar / Editar Servidor
    editingServer?.let { server ->
        ServerEditDialog(
            initialServer = server,
            isNew = isCreatingNew,
            onDismiss = onDismissEditor,
            onSave = onSaveServer,
        )
    }

    // Diálogo de confirmação de exclusão
    serverToDelete?.let { server ->
        AlertDialog(
            onDismissRequest = { serverToDelete = null },
            title = { Text("Excluir servidor") },
            text = { Text("Deseja realmente remover \"${server.displayName}\" dos seus servidores salvos?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteServer(server.id)
                        serverToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Excluir")
                }
            },
            dismissButton = {
                TextButton(onClick = { serverToDelete = null }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@Composable
private fun DisconnectedContent(
    savedServers: List<SavedServer>,
    lastSelectedServerId: String?,
    activeTab: MainTab,
    onTabChanged: (MainTab) -> Unit,
    quickForm: ConnectionFormState,
    onQuickHostChanged: (String) -> Unit,
    onQuickPortChanged: (String) -> Unit,
    onQuickNicknameChanged: (String) -> Unit,
    onQuickPasswordChanged: (String) -> Unit,
    onQuickSaveToListChanged: (Boolean) -> Unit,
    onQuickServerNameChanged: (String) -> Unit,
    onQuickConnect: () -> Unit,
    onConnectToSavedServer: (SavedServer) -> Unit,
    onOpenAddServer: () -> Unit,
    onOpenEditServer: (SavedServer) -> Unit,
    onRequestDeleteServer: (SavedServer) -> Unit,
    phase: ConnectionPhase,
    onCancelConnection: () -> Unit,
) {
    val isConnecting = phase == ConnectionPhase.CONNECTING ||
        phase == ConnectionPhase.RECONNECTING ||
        phase == ConnectionPhase.DISCONNECTING

    Column(Modifier.fillMaxSize()) {
        if (isConnecting) {
            ConnectingBanner(phase = phase, onCancel = onCancelConnection)
        }

        TabRow(
            selectedTabIndex = if (activeTab == MainTab.SAVED_SERVERS) 0 else 1,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                val selectedIndex = if (activeTab == MainTab.SAVED_SERVERS) 0 else 1
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                    color = MaterialTheme.colorScheme.primary,
                )
            },
        ) {
            Tab(
                selected = activeTab == MainTab.SAVED_SERVERS,
                onClick = { onTabChanged(MainTab.SAVED_SERVERS) },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Outlined.Bookmark, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            text = if (savedServers.isNotEmpty()) {
                                "Meus Servidores (${savedServers.size})"
                            } else {
                                "Meus Servidores"
                            },
                        )
                    }
                },
            )
            Tab(
                selected = activeTab == MainTab.QUICK_CONNECT,
                onClick = { onTabChanged(MainTab.QUICK_CONNECT) },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Outlined.FlashOn, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Conexão Rápida")
                    }
                },
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            when (activeTab) {
                MainTab.SAVED_SERVERS -> SavedServersScreen(
                    servers = savedServers,
                    lastSelectedServerId = lastSelectedServerId,
                    isConnecting = isConnecting,
                    onConnect = onConnectToSavedServer,
                    onOpenAddServer = onOpenAddServer,
                    onEdit = onOpenEditServer,
                    onDelete = onRequestDeleteServer,
                )
                MainTab.QUICK_CONNECT -> QuickConnectScreen(
                    form = quickForm,
                    isConnecting = isConnecting,
                    onHostChanged = onQuickHostChanged,
                    onPortChanged = onQuickPortChanged,
                    onNicknameChanged = onQuickNicknameChanged,
                    onPasswordChanged = onQuickPasswordChanged,
                    onSaveToListChanged = onQuickSaveToListChanged,
                    onServerNameChanged = onQuickServerNameChanged,
                    onConnect = onQuickConnect,
                )
            }
        }
    }
}

@Composable
private fun ConnectingBanner(
    phase: ConnectionPhase,
    onCancel: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = when (phase) {
                        ConnectionPhase.RECONNECTING -> "Reconectando ao servidor..."
                        ConnectionPhase.DISCONNECTING -> "Desconectando..."
                        else -> "Conectando ao servidor..."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            TextButton(
                onClick = onCancel,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Text("Cancelar")
            }
        }
    }
}

@Composable
private fun SavedServersScreen(
    servers: List<SavedServer>,
    lastSelectedServerId: String?,
    isConnecting: Boolean,
    onConnect: (SavedServer) -> Unit,
    onOpenAddServer: () -> Unit,
    onEdit: (SavedServer) -> Unit,
    onDelete: (SavedServer) -> Unit,
) {
    if (servers.isEmpty()) {
        EmptyServersPlaceholder(onAddServer = onOpenAddServer)
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Escolha um servidor para conectar:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                FilledTonalButton(
                    onClick = onOpenAddServer,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Adicionar", style = MaterialTheme.typography.labelMedium)
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(servers, key = { it.id }) { server ->
                    val isLastSelected = server.id == lastSelectedServerId
                    SavedServerCard(
                        server = server,
                        isLastSelected = isLastSelected,
                        isConnecting = isConnecting,
                        onConnect = { onConnect(server) },
                        onEdit = { onEdit(server) },
                        onDelete = { onDelete(server) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedServerCard(
    server: SavedServer,
    isLastSelected: Boolean,
    isConnecting: Boolean,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isConnecting, onClick = onConnect),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isLastSelected) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        border = if (isLastSelected) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Dns,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = server.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (server.password.isNotEmpty()) {
                                Icon(
                                    imageVector = Icons.Outlined.Lock,
                                    contentDescription = "Protegido por senha",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                        if (isLastSelected) {
                            Text(
                                text = "Último servidor usado",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }

                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Mais opções",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Editar") },
                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Excluir", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AlternateEmail,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = server.hostPortDisplay,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = server.nickname,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                thickness = 1.dp,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onConnect,
                    enabled = !isConnecting,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Conectar", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun EmptyServersPlaceholder(
    onAddServer: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(72.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Dns,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        Text(
            text = "Nenhum servidor salvo",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Adicione seus servidores favoritos do TeamSpeak para conectar com apenas um toque quando abrir o app.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onAddServer,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Adicionar Servidor", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun QuickConnectScreen(
    form: ConnectionFormState,
    isConnecting: Boolean,
    onHostChanged: (String) -> Unit,
    onPortChanged: (String) -> Unit,
    onNicknameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onSaveToListChanged: (Boolean) -> Unit,
    onServerNameChanged: (String) -> Unit,
    onConnect: () -> Unit,
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val invalidHost = form.submitted && form.host.isBlank()
    val invalidPort = form.submitted && (form.port.toIntOrNull() !in 1..65535)
    val invalidNickname = form.submitted && form.nickname.trim().length !in 3..30

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Conexão Rápida",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Text(
            text = "Digite os dados do servidor para se conectar diretamente:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            OutlinedTextField(
                value = form.host,
                onValueChange = onHostChanged,
                modifier = Modifier.weight(1f),
                enabled = !isConnecting,
                singleLine = true,
                label = { Text("Endereço do servidor") },
                placeholder = { Text("voice.exemplo.com") },
                leadingIcon = { Icon(Icons.Outlined.Dns, contentDescription = null) },
                isError = invalidHost,
                supportingText = if (invalidHost) {
                    { Text("Digite o endereço") }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                value = form.port,
                onValueChange = onPortChanged,
                modifier = Modifier.width(108.dp),
                enabled = !isConnecting,
                singleLine = true,
                label = { Text("Porta") },
                isError = invalidPort,
                supportingText = if (invalidPort) {
                    { Text("1–65535") }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                ),
            )
        }

        OutlinedTextField(
            value = form.nickname,
            onValueChange = onNicknameChanged,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isConnecting,
            singleLine = true,
            label = { Text("Apelido") },
            leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
            isError = invalidNickname,
            supportingText = if (invalidNickname) {
                { Text("3 a 30 caracteres") }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )

        OutlinedTextField(
            value = form.password,
            onValueChange = onPasswordChanged,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isConnecting,
            singleLine = true,
            label = { Text("Senha do servidor (opcional)") },
            leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (passwordVisible) "Ocultar senha" else "Mostrar senha",
                    )
                }
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Salvar este servidor na minha lista",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Switch(
                        checked = form.saveToList,
                        onCheckedChange = onSaveToListChanged,
                        enabled = !isConnecting,
                    )
                }

                AnimatedVisibility(visible = form.saveToList) {
                    OutlinedTextField(
                        value = form.serverName,
                        onValueChange = onServerNameChanged,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isConnecting,
                        singleLine = true,
                        label = { Text("Nome do servidor (opcional)") },
                        placeholder = { Text("Ex: Servidor dos Amigos") },
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Button(
            onClick = onConnect,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = !isConnecting,
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.Filled.Link, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Conectar", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ServerEditDialog(
    initialServer: SavedServer,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (SavedServer, Boolean) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialServer.name) }
    var host by rememberSaveable { mutableStateOf(initialServer.host) }
    var port by rememberSaveable { mutableStateOf(initialServer.port.toString()) }
    var nickname by rememberSaveable { mutableStateOf(initialServer.nickname) }
    var password by rememberSaveable { mutableStateOf(initialServer.password) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var submitted by rememberSaveable { mutableStateOf(false) }

    val invalidHost = submitted && host.isBlank()
    val invalidPort = submitted && (port.toIntOrNull() !in 1..65535)
    val invalidNick = submitted && nickname.trim().length !in 3..30

    fun validateAndBuild(): SavedServer? {
        submitted = true
        val portInt = port.toIntOrNull() ?: return null
        val built = initialServer.copy(
            name = name.trim(),
            host = host.trim(),
            port = portInt,
            nickname = nickname.trim(),
            password = password,
        )
        return if (built.validationError() == null) built else null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isNew) "Adicionar Servidor" else "Editar Servidor",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Nome do servidor (opcional)") },
                    placeholder = { Text("Ex: Servidor da Galera") },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Endereço *") },
                        placeholder = { Text("voice.exemplo.com") },
                        isError = invalidHost,
                        supportingText = if (invalidHost) {
                            { Text("Obrigatório") }
                        } else {
                            null
                        },
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter(Char::isDigit) },
                        modifier = Modifier.width(92.dp),
                        singleLine = true,
                        label = { Text("Porta") },
                        isError = invalidPort,
                        supportingText = if (invalidPort) {
                            { Text("Inválida") }
                        } else {
                            null
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }

                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Apelido *") },
                    isError = invalidNick,
                    supportingText = if (invalidNick) {
                        { Text("3 a 30 caracteres") }
                    } else {
                        null
                    },
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Senha do servidor (opcional)") },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (passwordVisible) "Ocultar senha" else "Mostrar senha",
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val server = validateAndBuild() ?: return@OutlinedButton
                        onSave(server, false)
                    },
                ) {
                    Text("Salvar")
                }
                Button(
                    onClick = {
                        val server = validateAndBuild() ?: return@Button
                        onSave(server, true)
                    },
                ) {
                    Text("Salvar e Conectar")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}

@Composable
private fun ConnectedContent(
    state: TeamSpeakServiceState,
    onDisconnect: () -> Unit,
    onPlaybackMutedChange: (Boolean) -> Unit,
    onParticipantMutedChange: (String, Boolean) -> Unit,
    onParticipantVolumeChange: (String, Int) -> Unit,
    onAudioRouteSelected: (Int) -> Unit,
    onMicrophoneModeChanged: (MicrophoneMode) -> Unit,
    onPushToTalkChanged: (Boolean) -> Unit,
    suppressionMode: SuppressionMode,
    onSuppressionModeChanged: (SuppressionMode) -> Unit,
    onJoinChannel: (Int, String) -> Unit,
    webRtcManager: WebRtcManager? = null,
    onRequestCameraBroadcast: () -> Unit = {},
    onStopCameraBroadcast: () -> Unit = {},
    onRequestScreenBroadcast: () -> Unit = {},
    onStopScreenBroadcast: () -> Unit = {},
    onSwitchCamera: () -> Unit = {},
    onWatchStream: (remoteClientId: Int, streamId: String) -> Unit = { _, _ -> },
    onStopWatchingStream: (String?) -> Unit = {},
    onAcceptViewer: (StreamViewer) -> Unit = {},
    onRejectViewer: (StreamViewer) -> Unit = {},
    onToggleAutoAcceptViewers: () -> Unit = {},
    isLandscape: Boolean = false,
    onToggleOrientation: (Boolean) -> Unit = {},
    onToggleFullscreen: (String) -> Unit = {},
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            ),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = state.serverLabel.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${state.snapshot.channels.size} canais · " +
                            "${state.snapshot.participants.size} online · " +
                            state.audioRouting.selectedRoute.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AudioRouteMenu(
                    routing = state.audioRouting,
                    onRouteSelected = onAudioRouteSelected,
                )
                IconButton(onClick = {
                    if (state.isBroadcastingCamera) {
                        onStopCameraBroadcast()
                    } else {
                        onRequestCameraBroadcast()
                    }
                }) {
                    Icon(
                        imageVector = if (state.isBroadcastingCamera) {
                            Icons.Filled.Videocam
                        } else {
                            Icons.Outlined.Videocam
                        },
                        contentDescription = if (state.isBroadcastingCamera) "Desativar câmera" else "Ativar câmera",
                        tint = if (state.isBroadcastingCamera) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                if (state.isBroadcastingCamera) {
                    IconButton(onClick = onSwitchCamera) {
                        Icon(
                            imageVector = Icons.Outlined.Cameraswitch,
                            contentDescription = "Alternar câmera",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                IconButton(onClick = {
                    if (state.isBroadcastingScreen) {
                        onStopScreenBroadcast()
                    } else {
                        onRequestScreenBroadcast()
                    }
                }) {
                    Icon(
                        imageVector = if (state.isBroadcastingScreen) {
                            Icons.AutoMirrored.Filled.StopScreenShare
                        } else {
                            Icons.AutoMirrored.Outlined.ScreenShare
                        },
                        contentDescription = if (state.isBroadcastingScreen) "Parar transmissão de tela" else "Transmitir tela",
                        tint = if (state.isBroadcastingScreen) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                IconButton(onClick = { onPlaybackMutedChange(!state.playbackMuted) }) {
                    Icon(
                        imageVector = if (state.playbackMuted) {
                            Icons.AutoMirrored.Outlined.VolumeOff
                        } else {
                            Icons.AutoMirrored.Outlined.VolumeUp
                        },
                        contentDescription = if (state.playbackMuted) "Ativar alto-falante" else "Silenciar alto-falante",
                    )
                }
                IconButton(onClick = onDisconnect) {
                    Icon(
                        Icons.Default.PowerSettingsNew,
                        contentDescription = "Desconectar",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        if (state.isBroadcastingCamera || state.isBroadcastingScreen) {
            LiveBroadcastBanner(
                state = state,
                onStopBroadcast = {
                    if (state.isBroadcastingCamera) onStopCameraBroadcast()
                    if (state.isBroadcastingScreen) onStopScreenBroadcast()
                },
            )
        }

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = MaterialTheme.colorScheme.primary,
                )
            },
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Canais") },
                icon = { Icon(Icons.Outlined.Tag, contentDescription = null) },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Usuários") },
                icon = { Icon(Icons.Outlined.Groups, contentDescription = null) },
            )
        }

        Box(Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize()) {
                val activeWatchedStreams = state.watchingStreams.ifEmpty {
                    state.watchingStreamId?.let { sId ->
                        val streamer = state.snapshot.participants.firstOrNull { it.id == state.watchingStreamClientId }
                        val streamInfo = state.snapshot.activeStreams.firstOrNull { it.streamId == sId }
                        listOf(
                            WatchedStream(
                                streamId = sId,
                                clientId = state.watchingStreamClientId ?: 0,
                                nickname = streamer?.nickname ?: "Vídeo",
                                type = streamInfo?.type ?: StreamType.CAMERA,
                                name = streamInfo?.description.orEmpty().ifEmpty {
                                    when (streamInfo?.type) {
                                        StreamType.SCREEN -> "Tela"
                                        StreamType.WINDOW -> "Janela"
                                        else -> "Câmera"
                                    }
                                },
                            )
                        )
                    } ?: emptyList()
                }

                val otherActiveStreams = remember(state.snapshot.activeStreams, state.snapshot.ownClientId) {
                    val ownId = state.snapshot.ownClientId
                    state.snapshot.activeStreams.filter { it.clientId != ownId }
                }

                if (activeWatchedStreams.isNotEmpty() && webRtcManager != null) {
                    val remoteVideoTracks by webRtcManager.remoteVideoTracks.collectAsStateWithLifecycle()
                    ActiveStreamsSection(
                        watchedStreams = activeWatchedStreams,
                        allActiveStreams = otherActiveStreams,
                        participants = state.snapshot.participants,
                        remoteVideoTracks = remoteVideoTracks,
                        eglBaseContext = webRtcManager.eglBase.eglBaseContext,
                        onWatchStream = onWatchStream,
                        onStopWatchingStream = { streamId -> onStopWatchingStream(streamId) },
                        isLandscape = isLandscape,
                        onToggleOrientation = onToggleOrientation,
                        onToggleFullscreen = onToggleFullscreen,
                    )
                } else if (otherActiveStreams.isNotEmpty()) {
                    AvailableStreamsBanner(
                        activeStreams = otherActiveStreams,
                        participants = state.snapshot.participants,
                        onWatchStream = onWatchStream,
                    )
                }

                for (req in state.pendingViewerRequests) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "${req.nickname} pediu para assistir",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Text(
                                    text = "Transmitir vídeo para este usuário",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilledTonalButton(
                                    onClick = { onRejectViewer(req) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp),
                                ) {
                                    Text("Recusar", style = MaterialTheme.typography.labelSmall)
                                }
                                Button(
                                    onClick = { onAcceptViewer(req) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp),
                                ) {
                                    Text("Aceitar", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }

                Box(Modifier.weight(1f)) {
                    if (selectedTab == 0) {
                        ChannelList(
                            state = state,
                            onJoinChannel = onJoinChannel,
                            onWatchStream = onWatchStream,
                            onStopWatchingStream = { onStopWatchingStream(it) },
                        )
                    } else {
                        ParticipantList(
                            state = state,
                            onMutedChange = onParticipantMutedChange,
                            onVolumeChange = onParticipantVolumeChange,
                            onWatchStream = onWatchStream,
                            onStopWatchingStream = { onStopWatchingStream(it) },
                        )
                    }

                    if (state.isBroadcastingCamera && webRtcManager != null) {
                        val localVideoTrack by webRtcManager.localVideoTrack.collectAsStateWithLifecycle()
                        val isFrontCamera by webRtcManager.isFrontCamera.collectAsStateWithLifecycle()
                        if (localVideoTrack != null) {
                            Card(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(12.dp)
                                    .size(width = 96.dp, height = 136.dp),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                            ) {
                                Box(Modifier.fillMaxSize()) {
                                    WebRtcVideoView(
                                        videoTrack = localVideoTrack,
                                        eglBaseContext = webRtcManager.eglBase.eglBaseContext,
                                        mirror = isFrontCamera,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    IconButton(
                                        onClick = onSwitchCamera,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(26.dp)
                                            .padding(2.dp)
                                            .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Cameraswitch,
                                            contentDescription = "Alternar câmera",
                                            tint = Color.White,
                                            modifier = Modifier.size(15.dp),
                                        )
                                    }
                                    Row(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .background(Color.Black.copy(alpha = 0.55f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Visibility,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp),
                                            )
                                            Text(
                                                text = "${state.activeViewers.size}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White,
                                            )
                                        }
                                        IconButton(
                                            onClick = onToggleAutoAcceptViewers,
                                            modifier = Modifier.size(20.dp),
                                        ) {
                                            Icon(
                                                imageVector = if (state.autoAcceptStreamViewers) Icons.Outlined.CheckCircle else Icons.Outlined.Lock,
                                                contentDescription = if (state.autoAcceptStreamViewers) "Auto-aceitar ativado" else "Aprovação manual",
                                                tint = if (state.autoAcceptStreamViewers) Color(0xFF4CAF50) else Color(0xFFFFC107),
                                                modifier = Modifier.size(13.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (state.isBroadcastingScreen) {
                        Card(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(12.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.StopScreenShare,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Column {
                                    Text(
                                        text = "Transmitindo tela (${state.currentStreamPreset.title})",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Visibility,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(12.dp),
                                        )
                                        Text(
                                            text = "${state.activeViewers.size} espectador(es)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        if (state.isSharingSystemAudio) {
                                            Text(
                                                text = "· Áudio interno",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = onStopScreenBroadcast,
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Parar transmissão de tela",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        MicrophoneControl(
            mode = state.microphoneMode,
            isTransmitting = state.isTransmitting,
            onMicrophoneModeChanged = onMicrophoneModeChanged,
            onPushToTalkChanged = onPushToTalkChanged,
            suppressionMode = suppressionMode,
            onSuppressionModeChanged = onSuppressionModeChanged,
        )
    }
}

@Composable
private fun LiveBroadcastBanner(
    state: TeamSpeakServiceState,
    onStopBroadcast: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.error,
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        text = "AO VIVO",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }

                Text(
                    text = if (state.isBroadcastingScreen) "Tela" else "Câmera",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                Text(
                    text = "• ${state.currentStreamPreset.title}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (state.isSharingSystemAudio) {
                    Icon(
                        imageVector = Icons.Outlined.GraphicEq,
                        contentDescription = "Áudio interno ativo",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Visibility,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "${state.activeViewers.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedButton(
                onClick = onStopBroadcast,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp),
            ) {
                Text(
                    text = "Parar",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun AudioRouteMenu(
    routing: AudioRoutingState,
    onRouteSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Outlined.Headphones,
                contentDescription = "Selecionar dispositivo de áudio; atual: ${routing.selectedRoute.label}",
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            routing.routes.forEach { route ->
                val selected = route.id == routing.selectedRouteId
                DropdownMenuItem(
                    text = {
                        Text(
                            text = route.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        expanded = false
                        onRouteSelected(route.id)
                    },
                    leadingIcon = {
                        if (selected) {
                            Icon(Icons.Outlined.Check, contentDescription = null)
                        } else {
                            Spacer(Modifier.size(24.dp))
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MicrophoneControl(
    mode: MicrophoneMode,
    isTransmitting: Boolean,
    onMicrophoneModeChanged: (MicrophoneMode) -> Unit,
    onPushToTalkChanged: (Boolean) -> Unit,
    suppressionMode: SuppressionMode,
    onSuppressionModeChanged: (SuppressionMode) -> Unit,
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    var suppressionMenuExpanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "chevronRotation",
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            ),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "ÁUDIO & MICROFONE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "• ${when (mode) {
                            MicrophoneMode.OFF -> "Mudo"
                            MicrophoneMode.PUSH_TO_TALK -> "PTT"
                            MicrophoneMode.CONTINUOUS -> "Contínuo"
                        }} | ${when (suppressionMode) {
                            SuppressionMode.OFF -> "Sem filtro"
                            SuppressionMode.RNNOISE -> "RNNoise"
                            SuppressionMode.DEEPFILTER -> "DeepFilter"
                            SuppressionMode.NOISE_SUPPRESSOR -> "Android"
                            SuppressionMode.BOTH -> "Ambos"
                            SuppressionMode.ASTRUM_CLARITY -> "Astrum Clarity"
                        }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Recolher opções" else "Expandir opções",
                        modifier = Modifier.rotate(chevronRotation),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Modo de transmissão",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            MicrophoneMode.entries.forEachIndexed { index, option ->
                                SegmentedButton(
                                    selected = mode == option,
                                    onClick = { onMicrophoneModeChanged(option) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = MicrophoneMode.entries.size,
                                    ),
                                ) {
                                    Text(
                                        text = when (option) {
                                            MicrophoneMode.OFF -> "Desativado"
                                            MicrophoneMode.PUSH_TO_TALK -> "PTT"
                                            MicrophoneMode.CONTINUOUS -> "Contínuo"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Supressão de ruído",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        Box(modifier = Modifier.fillMaxWidth()) {
                            Surface(
                                onClick = { suppressionMenuExpanded = true },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Icon(
                                            imageVector = when (suppressionMode) {
                                                SuppressionMode.ASTRUM_CLARITY -> Icons.Outlined.FlashOn
                                                SuppressionMode.DEEPFILTER -> Icons.Outlined.GraphicEq
                                                SuppressionMode.RNNOISE -> Icons.Outlined.GraphicEq
                                                SuppressionMode.NOISE_SUPPRESSOR -> Icons.Outlined.Headphones
                                                SuppressionMode.BOTH -> Icons.Outlined.Tune
                                                SuppressionMode.OFF -> Icons.Outlined.MicOff
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(22.dp),
                                            tint = if (suppressionMode != SuppressionMode.OFF) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                        )

                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = when (suppressionMode) {
                                                    SuppressionMode.ASTRUM_CLARITY -> "Astrum Clarity (Recomendado)"
                                                    SuppressionMode.RNNOISE -> "RNNoise"
                                                    SuppressionMode.DEEPFILTER -> "DeepFilterNet"
                                                    SuppressionMode.NOISE_SUPPRESSOR -> "Hardware Android"
                                                    SuppressionMode.BOTH -> "Ambos (RNNoise + Android)"
                                                    SuppressionMode.OFF -> "Desativado"
                                                },
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                            Text(
                                                text = when (suppressionMode) {
                                                    SuppressionMode.ASTRUM_CLARITY -> "IA neural avançada com anti-teclado e cliques"
                                                    SuppressionMode.RNNOISE -> "Filtro neural clássico leve para voz"
                                                    SuppressionMode.DEEPFILTER -> "Rede neural profunda de alta qualidade"
                                                    SuppressionMode.NOISE_SUPPRESSOR -> "Cancelador de ruído do hardware do celular"
                                                    SuppressionMode.BOTH -> "RNNoise + cancelador do hardware do celular"
                                                    SuppressionMode.OFF -> "Sem nenhum cancelamento de ruído"
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }

                                    Icon(
                                        imageVector = Icons.Outlined.KeyboardArrowDown,
                                        contentDescription = "Selecionar modo de supressão",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = suppressionMenuExpanded,
                                onDismissRequest = { suppressionMenuExpanded = false },
                            ) {
                                val options = listOf(
                                    SuppressionMode.ASTRUM_CLARITY to Pair(
                                        "Astrum Clarity (Recomendado)",
                                        "IA neural avançada com anti-teclado e cliques",
                                    ),
                                    SuppressionMode.RNNOISE to Pair(
                                        "RNNoise",
                                        "Filtro neural clássico leve para voz",
                                    ),
                                    SuppressionMode.DEEPFILTER to Pair(
                                        "DeepFilterNet",
                                        "Rede neural profunda de alta qualidade",
                                    ),
                                    SuppressionMode.NOISE_SUPPRESSOR to Pair(
                                        "Hardware Android",
                                        "Cancelador nativo do chipset do dispositivo",
                                    ),
                                    SuppressionMode.BOTH to Pair(
                                        "Ambos (RNNoise + Android)",
                                        "Combina o filtro nativo do celular com RNNoise",
                                    ),
                                    SuppressionMode.OFF to Pair(
                                        "Desativado",
                                        "Sem nenhum processamento de ruído",
                                    ),
                                )

                                options.forEach { (option, labels) ->
                                    val isSelected = suppressionMode == option
                                    DropdownMenuItem(
                                        text = {
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(
                                                    text = labels.first,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                )
                                                Text(
                                                    text = labels.second,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        },
                                        onClick = {
                                            suppressionMenuExpanded = false
                                            onSuppressionModeChanged(option)
                                        },
                                        leadingIcon = {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Check,
                                                    contentDescription = "Selecionado",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                )
                                            } else {
                                                Spacer(Modifier.size(24.dp))
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 1.dp,
                    )
                }
            }

            when (mode) {
                MicrophoneMode.PUSH_TO_TALK -> PushToTalkButton(
                    isTransmitting = isTransmitting,
                    onPushToTalkChanged = onPushToTalkChanged,
                )

                MicrophoneMode.OFF,
                MicrophoneMode.CONTINUOUS,
                -> Row(
                    modifier = Modifier.height(48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = if (mode == MicrophoneMode.OFF) {
                            Icons.Outlined.MicOff
                        } else {
                            Icons.Filled.Mic
                        },
                        contentDescription = null,
                        tint = if (isTransmitting) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Text(
                        text = if (mode == MicrophoneMode.OFF) {
                            "Microfone desativado"
                        } else if (isTransmitting) {
                            "Microfone transmitindo (sempre ligado)"
                        } else {
                            "Microfone ativo (sempre ligado)"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isTransmitting) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PushToTalkButton(
    isTransmitting: Boolean,
    onPushToTalkChanged: (Boolean) -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val currentPushToTalkChanged by rememberUpdatedState(onPushToTalkChanged)
    val active = pressed || isTransmitting

    Surface(
        modifier = Modifier
            .size(58.dp)
            .semantics {
                role = Role.Button
                contentDescription = if (active) "Falando" else "Segure para falar"
                onClick {
                    onPushToTalkChanged(!isTransmitting)
                    true
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true
                    currentPushToTalkChanged(true)
                    try {
                        waitForUpOrCancellation()
                    } finally {
                        pressed = false
                        currentPushToTalkChanged(false)
                    }
                }
            },
        shape = CircleShape,
        color = if (active) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primary
        },
        contentColor = if (active) {
            MaterialTheme.colorScheme.onError
        } else {
            MaterialTheme.colorScheme.onPrimary
        },
        shadowElevation = 2.dp,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun ActiveStreamsSection(
    watchedStreams: List<WatchedStream>,
    allActiveStreams: List<Ts6StreamInfo>,
    participants: List<Ts3Participant>,
    remoteVideoTracks: Map<String, VideoTrack>,
    eglBaseContext: EglBase.Context,
    onWatchStream: (remoteClientId: Int, streamId: String) -> Unit,
    onStopWatchingStream: (String) -> Unit,
    onToggleFullscreen: (String) -> Unit = {},
    isLandscape: Boolean = false,
    onToggleOrientation: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var isGridView by rememberSaveable { mutableStateOf(false) }
    var focusedStreamId by rememberSaveable { mutableStateOf<String?>(null) }

    val currentFocused = watchedStreams.firstOrNull { it.streamId == focusedStreamId }
        ?: watchedStreams.lastOrNull()
    if (focusedStreamId != currentFocused?.streamId) {
        focusedStreamId = currentFocused?.streamId
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (allActiveStreams.size > 1 || watchedStreams.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (stream in allActiveStreams) {
                        val isWatched = watchedStreams.any { it.streamId == stream.streamId }
                        val isFocused = stream.streamId == currentFocused?.streamId
                        val streamer = participants.firstOrNull { it.id == stream.clientId }
                        val streamerName = streamer?.nickname ?: "Usuário ${stream.clientId}"
                        val streamTitle = stream.displayTitle()

                        FilterChip(
                            selected = isFocused,
                            onClick = {
                                if (!isWatched) {
                                    onWatchStream(stream.clientId, stream.streamId)
                                }
                                focusedStreamId = stream.streamId
                                if (isGridView && watchedStreams.size > 1) {
                                    isGridView = false
                                }
                            },
                            label = {
                                Text(
                                    text = "$streamerName: $streamTitle",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (stream.isScreenOrWindow) {
                                        Icons.AutoMirrored.Outlined.ScreenShare
                                    } else {
                                        Icons.Filled.Videocam
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                )
                            },
                            trailingIcon = if (isWatched) {
                                {
                                    IconButton(
                                        onClick = { onStopWatchingStream(stream.streamId) },
                                        modifier = Modifier.size(16.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Fechar transmissão",
                                            modifier = Modifier.size(12.dp),
                                        )
                                    }
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }

                if (watchedStreams.size > 1) {
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = { isGridView = !isGridView },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = if (isGridView) Icons.Outlined.CropSquare else Icons.Filled.GridView,
                            contentDescription = if (isGridView) "Modo foco" else "Modo grade",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }

        if (isGridView && watchedStreams.size > 1) {
            when {
                watchedStreams.size == 2 -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        for (stream in watchedStreams) {
                            SingleStreamCard(
                                stream = stream,
                                videoTrack = remoteVideoTracks[stream.streamId],
                                eglBaseContext = eglBaseContext,
                                onClose = { onStopWatchingStream(stream.streamId) },
                                onCardClick = {
                                    focusedStreamId = stream.streamId
                                    isGridView = false
                                },
                                onToggleFullscreen = {
                                    onToggleFullscreen(stream.streamId)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            )
                        }
                    }
                }
                else -> {
                    val row1 = watchedStreams.take(2)
                    val row2 = watchedStreams.drop(2).take(2)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(125.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        for (stream in row1) {
                            SingleStreamCard(
                                stream = stream,
                                videoTrack = remoteVideoTracks[stream.streamId],
                                eglBaseContext = eglBaseContext,
                                onClose = { onStopWatchingStream(stream.streamId) },
                                onCardClick = {
                                    focusedStreamId = stream.streamId
                                    isGridView = false
                                },
                                onToggleFullscreen = {
                                    onToggleFullscreen(stream.streamId)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(125.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        for (stream in row2) {
                            SingleStreamCard(
                                stream = stream,
                                videoTrack = remoteVideoTracks[stream.streamId],
                                eglBaseContext = eglBaseContext,
                                onClose = { onStopWatchingStream(stream.streamId) },
                                onCardClick = {
                                    focusedStreamId = stream.streamId
                                    isGridView = false
                                },
                                onToggleFullscreen = {
                                    onToggleFullscreen(stream.streamId)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            )
                        }
                        if (row2.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        } else {
            currentFocused?.let { stream ->
                val currentIndex = watchedStreams.indexOf(stream)
                val totalCount = watchedStreams.size
                val indexText = if (totalCount > 1) "${currentIndex + 1}/$totalCount" else null
                val onPrev = if (totalCount > 1) {
                    {
                        val prevIdx = if (currentIndex - 1 < 0) totalCount - 1 else currentIndex - 1
                        focusedStreamId = watchedStreams[prevIdx].streamId
                    }
                } else null
                val onNext = if (totalCount > 1) {
                    {
                        val nextIdx = (currentIndex + 1) % totalCount
                        focusedStreamId = watchedStreams[nextIdx].streamId
                    }
                } else null

                SingleStreamCard(
                    stream = stream,
                    videoTrack = remoteVideoTracks[stream.streamId],
                    eglBaseContext = eglBaseContext,
                    onClose = { onStopWatchingStream(stream.streamId) },
                    streamIndexText = indexText,
                    onPreviousStream = onPrev,
                    onNextStream = onNext,
                    onToggleFullscreen = {
                        onToggleFullscreen(stream.streamId)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp),
                )
            }
        }
    }
}

@Composable
private fun AvailableStreamsBanner(
    activeStreams: List<Ts6StreamInfo>,
    participants: List<Ts3Participant>,
    onWatchStream: (remoteClientId: Int, streamId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.LiveTv,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "Transmissões ativas no canal (${activeStreams.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (stream in activeStreams) {
                    val streamer = participants.firstOrNull { it.id == stream.clientId }
                    val streamerName = streamer?.nickname ?: "Usuário ${stream.clientId}"
                    val streamTitle = stream.displayTitle()
                    val icon = if (stream.isScreenOrWindow) {
                        Icons.AutoMirrored.Outlined.ScreenShare
                    } else {
                        Icons.Filled.Videocam
                    }

                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Column {
                                Text(
                                    text = streamerName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "Transmissão: $streamTitle",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            FilledTonalButton(
                                onClick = { onWatchStream(stream.clientId, stream.streamId) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp),
                            ) {
                                Text("Assistir", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SingleStreamCard(
    stream: WatchedStream,
    videoTrack: VideoTrack?,
    eglBaseContext: EglBase.Context,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    streamIndexText: String? = null,
    onPreviousStream: (() -> Unit)? = null,
    onNextStream: (() -> Unit)? = null,
    onCardClick: (() -> Unit)? = null,
    onToggleFullscreen: (() -> Unit)? = null,
) {
    val displayName = stream.nickname.ifBlank { "Vídeo" }
    val streamTitle = stream.displayTitle()

    Card(
        modifier = modifier.then(if (onCardClick != null) Modifier.clickable { onCardClick() } else Modifier),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = if (videoTrack != null) Color.Transparent else Color.Black),
    ) {
        Box(Modifier.fillMaxSize()) {
            if (videoTrack != null) {
                WebRtcVideoView(
                    videoTrack = videoTrack,
                    eglBaseContext = eglBaseContext,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(6.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                        Text(
                            text = "Conectando transmissão: $displayName ($streamTitle)...",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // Top overlay banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    Icon(
                        imageVector = if (stream.isScreenOrWindow) {
                            Icons.AutoMirrored.Outlined.ScreenShare
                        } else {
                            Icons.Filled.Videocam
                        },
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        text = "$displayName • Transmissão: $streamTitle",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (streamIndexText != null) {
                        Text(
                            text = streamIndexText,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    if (onPreviousStream != null) {
                        IconButton(
                            onClick = onPreviousStream,
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "Transmissão anterior",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    if (onNextStream != null) {
                        IconButton(
                            onClick = onNextStream,
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                                contentDescription = "Próxima transmissão",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    if (onToggleFullscreen != null) {
                        IconButton(
                            onClick = onToggleFullscreen,
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "Tela cheia",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fechar transmissão",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelList(
    state: TeamSpeakServiceState,
    onJoinChannel: (Int, String) -> Unit,
    onWatchStream: (remoteClientId: Int, streamId: String) -> Unit = { _, _ -> },
    onStopWatchingStream: (String) -> Unit = {},
) {
    var passwordChannel by remember { mutableStateOf<br.app.astrum.ts6.protocol.Ts3Channel?>(null) }
    var channelPassword by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var expandedChannelIds by rememberSaveable { mutableStateOf(intArrayOf()) }
    val currentChannelId = state.snapshot.currentChannelId
    val rows = remember(state.snapshot.channels) {
        ChannelTree.flatten(state.snapshot.channels)
    }
    val participantsByChannel = remember(state.snapshot.participants) {
        state.snapshot.participants.groupBy(Ts3Participant::channelId)
    }

    LaunchedEffect(currentChannelId) {
        if (currentChannelId != null && currentChannelId !in expandedChannelIds) {
            expandedChannelIds += currentChannelId
        }
    }

    passwordChannel?.let { channel ->
        AlertDialog(
            onDismissRequest = {
                passwordChannel = null
                channelPassword = ""
                passwordVisible = false
            },
            title = { Text("Entrar em “${channel.name}”") },
            text = {
                OutlinedTextField(
                    value = channelPassword,
                    onValueChange = { channelPassword = it },
                    singleLine = true,
                    label = { Text("Senha do canal") },
                    leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (passwordVisible) "Ocultar senha" else "Mostrar senha",
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onJoinChannel(channel.id, channelPassword)
                        passwordChannel = null
                        channelPassword = ""
                        passwordVisible = false
                    },
                ) {
                    Text("Entrar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        passwordChannel = null
                        channelPassword = ""
                        passwordVisible = false
                    },
                ) {
                    Text("Cancelar")
                }
            },
        )
    }

    if (rows.isEmpty()) {
        EmptyList("Nenhum canal visível")
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        items(rows, key = { it.channel.id }) { row ->
            val isCurrent = row.channel.id == currentChannelId
            val isSwitching = row.channel.id == state.switchingChannelId
            val participants = participantsByChannel[row.channel.id].orEmpty()
            val isExpanded = row.channel.id in expandedChannelIds
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isCurrent) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        )
                        .combinedClickable(
                            onClickLabel = if (isExpanded) "Recolher canal" else "Expandir canal",
                            onClick = {
                                expandedChannelIds = if (isExpanded) {
                                    expandedChannelIds.filterNot { it == row.channel.id }.toIntArray()
                                } else {
                                    expandedChannelIds + row.channel.id
                                }
                            },
                            onDoubleClick = {
                                if (!isCurrent && state.switchingChannelId == null) {
                                    if (row.channel.hasPassword) {
                                        channelPassword = ""
                                        passwordVisible = false
                                        passwordChannel = row.channel
                                    } else {
                                        onJoinChannel(row.channel.id, "")
                                    }
                                }
                            },
                        )
                        .padding(
                            start = (8 + row.depth * 20).coerceAtMost(88).dp,
                            end = 16.dp,
                            top = 13.dp,
                            bottom = 13.dp,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (isExpanded) {
                            Icons.Outlined.KeyboardArrowDown
                        } else {
                            Icons.AutoMirrored.Outlined.KeyboardArrowRight
                        },
                        contentDescription = if (isExpanded) "Expandido" else "Recolhido",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = if (row.channel.hasPassword) Icons.Outlined.Lock else Icons.Outlined.Tag,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (row.channel.isDefault || isCurrent) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = row.channel.name,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = row.channel.clientCount.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (isCurrent) {
                        Spacer(Modifier.width(10.dp))
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = "Canal atual",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    } else if (isSwitching) {
                        Spacer(Modifier.width(10.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
                if (isExpanded) {
                    participants.forEach { participant ->
                        ChannelParticipantRow(
                            participant = participant,
                            isOwnClient = participant.id == state.snapshot.ownClientId,
                            depth = row.depth,
                            activeStreams = state.snapshot.activeStreams,
                            watchingStreams = state.watchingStreams,
                            watchingStreamId = state.watchingStreamId,
                            onWatchStream = onWatchStream,
                            onStopWatchingStream = onStopWatchingStream,
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
        }
    }
}

@Composable
private fun ChannelParticipantRow(
    participant: Ts3Participant,
    isOwnClient: Boolean,
    depth: Int,
    activeStreams: List<Ts6StreamInfo> = emptyList(),
    watchingStreams: List<WatchedStream> = emptyList(),
    watchingStreamId: String? = null,
    onWatchStream: (remoteClientId: Int, streamId: String) -> Unit = { _, _ -> },
    onStopWatchingStream: (String) -> Unit = {},
) {
    val clientStreams = activeStreams.filter { it.clientId == participant.id }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f))
            .padding(
                start = (48 + depth * 20).coerceAtMost(112).dp,
                end = 16.dp,
                top = 9.dp,
                bottom = 9.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = when {
                participant.isTalking -> Icons.Outlined.GraphicEq
                participant.isInputMuted -> Icons.Outlined.MicOff
                participant.isOutputMuted -> Icons.AutoMirrored.Outlined.VolumeOff
                else -> Icons.Outlined.Person
            },
            contentDescription = when {
                participant.isTalking -> "Falando"
                participant.isInputMuted -> "Microfone silenciado"
                participant.isOutputMuted -> "Alto-falante silenciado"
                else -> null
            },
            modifier = Modifier.size(19.dp),
            tint = if (participant.isTalking) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = participant.nickname,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isOwnClient) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (isOwnClient) {
            Text(
                text = "Você",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            for (stream in clientStreams) {
                val isWatching = watchingStreams.any { it.streamId == stream.streamId } || watchingStreamId == stream.streamId
                val streamTitle = stream.displayTitle()
                val icon = if (stream.isScreenOrWindow) {
                    Icons.AutoMirrored.Outlined.ScreenShare
                } else {
                    Icons.Filled.Videocam
                }
                IconButton(
                    onClick = {
                        if (isWatching) {
                            onStopWatchingStream(stream.streamId)
                        } else {
                            onWatchStream(participant.id, stream.streamId)
                        }
                    },
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = if (isWatching) {
                            "Parar de assistir transmissão: $streamTitle de ${participant.nickname}"
                        } else {
                            "Assistir transmissão: $streamTitle de ${participant.nickname}"
                        },
                        tint = if (isWatching) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ParticipantList(
    state: TeamSpeakServiceState,
    onMutedChange: (String, Boolean) -> Unit,
    onVolumeChange: (String, Int) -> Unit,
    onWatchStream: (remoteClientId: Int, streamId: String) -> Unit = { _, _ -> },
    onStopWatchingStream: (String) -> Unit = {},
) {
    val channelsById = remember(state.snapshot.channels) {
        state.snapshot.channels.associateBy { it.id }
    }
    var expandedKey by remember { mutableStateOf<String?>(null) }
    if (state.snapshot.participants.isEmpty()) {
        EmptyList("Nenhum usuário visível")
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        items(state.snapshot.participants, key = { it.audioControlKey() }) { participant ->
            val key = participant.audioControlKey()
            val settings = state.participantAudioSettings[key] ?: ParticipantAudioSettings()
            val isOwnClient = participant.id == state.snapshot.ownClientId
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = when {
                            participant.isTalking -> Icons.Outlined.GraphicEq
                            participant.isInputMuted -> Icons.Outlined.MicOff
                            participant.isOutputMuted -> Icons.AutoMirrored.Outlined.VolumeOff
                            else -> Icons.Outlined.Person
                        },
                        contentDescription = null,
                        tint = if (participant.isTalking) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = participant.nickname,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = participantAudioDetail(
                                channelName = channelsById[participant.channelId]?.name.orEmpty(),
                                settings = settings,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val clientStreams = state.snapshot.activeStreams.filter { it.clientId == participant.id }
                    if (!isOwnClient) {
                        for (stream in clientStreams) {
                            val isWatching = state.watchingStreams.any { it.streamId == stream.streamId } || state.watchingStreamId == stream.streamId
                            val streamTitle = stream.displayTitle()
                            val icon = if (stream.isScreenOrWindow) {
                                Icons.AutoMirrored.Outlined.ScreenShare
                            } else {
                                Icons.Filled.Videocam
                            }
                            IconButton(onClick = {
                                if (isWatching) {
                                    onStopWatchingStream(stream.streamId)
                                } else {
                                    onWatchStream(participant.id, stream.streamId)
                                }
                            }) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = if (isWatching) {
                                        "Parar transmissão: $streamTitle de ${participant.nickname}"
                                    } else {
                                        "Assistir transmissão: $streamTitle de ${participant.nickname}"
                                    },
                                    tint = if (isWatching) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    if (!isOwnClient) {
                        IconButton(onClick = { onMutedChange(key, !settings.muted) }) {
                            Icon(
                                imageVector = if (settings.muted) {
                                    Icons.AutoMirrored.Outlined.VolumeOff
                                } else {
                                    Icons.AutoMirrored.Outlined.VolumeUp
                                },
                                contentDescription = if (settings.muted) {
                                    "Ativar som de ${participant.nickname}"
                                } else {
                                    "Silenciar ${participant.nickname}"
                                },
                            )
                        }
                        IconButton(
                            onClick = {
                                expandedKey = if (expandedKey == key) null else key
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = "Ajustar o volume de ${participant.nickname}",
                                tint = if (settings.volumePercent != 100) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
                if (!isOwnClient && expandedKey == key) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .padding(start = 54.dp, end = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Slider(
                            value = settings.volumePercent.toFloat(),
                            onValueChange = { value ->
                                val volume = (value / 5f).roundToInt() * 5
                                onVolumeChange(key, volume)
                            },
                            modifier = Modifier.weight(1f),
                            valueRange = 0f..200f,
                            steps = 39,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "${settings.volumePercent}%",
                            modifier = Modifier.width(52.dp),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
        }
    }
}

private fun participantAudioDetail(
    channelName: String,
    settings: ParticipantAudioSettings,
): String = when {
    settings.muted -> "$channelName · Silenciado"
    settings.volumePercent != 100 -> "$channelName · ${settings.volumePercent}%"
    else -> channelName
}

@Composable
private fun EmptyList(label: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatusIndicator(phase: ConnectionPhase) {
    val (label, color) = when (phase) {
        ConnectionPhase.DISCONNECTED -> "Desconectado" to MaterialTheme.colorScheme.outline
        ConnectionPhase.CONNECTING -> "Conectando..." to MaterialTheme.colorScheme.tertiary
        ConnectionPhase.RECONNECTING -> "Reconectando..." to MaterialTheme.colorScheme.tertiary
        ConnectionPhase.CONNECTED -> "Conectado" to MaterialTheme.colorScheme.primary
        ConnectionPhase.DISCONNECTING -> "Desconectando..." to MaterialTheme.colorScheme.tertiary
        ConnectionPhase.ERROR -> "Falha" to MaterialTheme.colorScheme.error
    }

    Row(
        modifier = Modifier
            .background(
                color = if (phase == ConnectionPhase.CONNECTED) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = RoundedCornerShape(50),
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(color, CircleShape),
        )
        Spacer(Modifier.width(7.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun StatusMessage(phase: ConnectionPhase, detail: String) {
    val background = if (phase == ConnectionPhase.ERROR) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val foreground = if (phase == ConnectionPhase.ERROR) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Text(
        text = detail,
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        color = foreground,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}
