package io.github.ts3mobile.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import io.github.ts3mobile.app.ConnectionFormState
import io.github.ts3mobile.app.service.MicrophoneMode
import io.github.ts3mobile.app.service.ParticipantAudioSettings
import io.github.ts3mobile.app.service.audioControlKey
import io.github.ts3mobile.app.service.TeamSpeakServiceState
import io.github.ts3mobile.audio.opus.AudioRoutingState
import io.github.ts3mobile.audio.opus.SuppressionMode
import io.github.ts3mobile.protocol.ChannelTree
import io.github.ts3mobile.protocol.ConnectionPhase
import io.github.ts3mobile.protocol.Ts3Participant
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    form: ConnectionFormState,
    serviceState: TeamSpeakServiceState,
    onHostChanged: (String) -> Unit,
    onPortChanged: (String) -> Unit,
    onNicknameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onPlaybackMutedChange: (Boolean) -> Unit,
    onParticipantMutedChange: (String, Boolean) -> Unit,
    onParticipantVolumeChange: (String, Int) -> Unit,
    onAudioRouteSelected: (Int) -> Unit,
    onMicrophoneModeChanged: (MicrophoneMode) -> Unit,
    onPushToTalkChanged: (Boolean) -> Unit,
    onJoinChannel: (Int, String) -> Unit,
    suppressionMode: SuppressionMode = SuppressionMode.RNNOISE,
    onSuppressionModeChanged: (SuppressionMode) -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "TS3 MOBILE",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                actions = {
                    StatusIndicator(serviceState.status.phase)
                    Spacer(Modifier.width(20.dp))
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
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
                )
            } else {
                ConnectionForm(
                    form = form,
                    phase = serviceState.status.phase,
                    onHostChanged = onHostChanged,
                    onPortChanged = onPortChanged,
                    onNicknameChanged = onNicknameChanged,
                    onPasswordChanged = onPasswordChanged,
                    onConnect = onConnect,
                    onDisconnect = onDisconnect,
                )
            }
        }
    }
}

@Composable
private fun ConnectionForm(
    form: ConnectionFormState,
    phase: ConnectionPhase,
    onHostChanged: (String) -> Unit,
    onPortChanged: (String) -> Unit,
    onNicknameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val isConnecting = phase == ConnectionPhase.CONNECTING ||
        phase == ConnectionPhase.RECONNECTING ||
        phase == ConnectionPhase.DISCONNECTING
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val invalidHost = form.submitted && form.host.isBlank()
    val invalidPort = form.submitted && (form.port.toIntOrNull() !in 1..65535)
    val invalidNickname = form.submitted && form.nickname.trim().length !in 3..30

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "CONECTAR AO SERVIDOR",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
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
                placeholder = { Text("voice.example.com") },
                leadingIcon = { Icon(Icons.Outlined.Dns, contentDescription = null) },
                isError = invalidHost,
                supportingText = if (invalidHost) {
                    { Text("Digite o endereço do servidor") }
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
            leadingIcon = { Icon(Icons.Outlined.AlternateEmail, contentDescription = null) },
            isError = invalidNickname,
            supportingText = if (invalidNickname) {
                { Text("O apelido deve ter de 3 a 30 caracteres") }
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
                        imageVector = if (passwordVisible) {
                            Icons.Outlined.VisibilityOff
                        } else {
                            Icons.Outlined.Visibility
                        },
                        contentDescription = if (passwordVisible) "Ocultar senha" else "Mostrar senha",
                    )
                }
            },
            visualTransformation = if (passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )

        Spacer(Modifier.height(4.dp))

        if (isConnecting) {
            OutlinedButton(
                onClick = onDisconnect,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = phase != ConnectionPhase.DISCONNECTING,
            ) {
                if (phase != ConnectionPhase.DISCONNECTING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    when (phase) {
                        ConnectionPhase.RECONNECTING -> "Cancelar reconexão"
                        ConnectionPhase.DISCONNECTING -> "Desconectando"
                        else -> "Cancelar conexão"
                    },
                )
            }
        } else {
            Button(
                onClick = onConnect,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Icon(Icons.Default.Link, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (phase == ConnectionPhase.ERROR) "Reconectar" else "Conectar")
            }
        }
    }
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
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = state.serverLabel.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
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
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = "Desconectar")
                }
            }
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
            if (selectedTab == 0) {
                ChannelList(state, onJoinChannel)
            } else {
                ParticipantList(
                    state = state,
                    onMutedChange = onParticipantMutedChange,
                    onVolumeChange = onParticipantVolumeChange,
                )
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
    Surface(
        modifier = Modifier.border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "ÁUDIO / MICROFONE",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
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
                            when (option) {
                                MicrophoneMode.OFF -> "Desativado"
                                MicrophoneMode.PUSH_TO_TALK -> "Segure"
                                MicrophoneMode.CONTINUOUS -> "Sempre ligado"
                            },
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Supressão de ruído",
                    style = MaterialTheme.typography.labelLarge,
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SuppressionMode.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = suppressionMode == option,
                            onClick = { onSuppressionModeChanged(option) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = SuppressionMode.entries.size,
                            ),
                        ) {
                            Text(
                                text = when (option) {
                                    SuppressionMode.OFF -> "Off"
                                    SuppressionMode.RNNOISE -> "RNNoise"
                                    SuppressionMode.DEEPFILTER -> "DeepFilter"
                                    SuppressionMode.NOISE_SUPPRESSOR -> "Android"
                                    SuppressionMode.BOTH -> "Ambos"
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Text(
                    text = when (suppressionMode) {
                        SuppressionMode.OFF -> "Sem supressão"
                        SuppressionMode.RNNOISE -> "Filtro neural RNNoise"
                        SuppressionMode.DEEPFILTER -> "Filtro neural DeepFilterNet"
                        SuppressionMode.NOISE_SUPPRESSOR -> "Filtro nativo Android"
                        SuppressionMode.BOTH -> "RNNoise + Android combinados"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics {
                        contentDescription = "Modo atual: ${when (suppressionMode) {
                            SuppressionMode.OFF -> "sem supressão"
                            SuppressionMode.RNNOISE -> "RNNoise"
                            SuppressionMode.DEEPFILTER -> "DeepFilter"
                            SuppressionMode.NOISE_SUPPRESSOR -> "Android"
                            SuppressionMode.BOTH -> "ambos"
                        }}"
                    },
                )
            }

            when (mode) {
                MicrophoneMode.PUSH_TO_TALK -> PushToTalkButton(
                    isTransmitting = isTransmitting,
                    onPushToTalkChanged = onPushToTalkChanged,
                )

                MicrophoneMode.OFF,
                MicrophoneMode.CONTINUOUS,
                -> Row(
                    modifier = Modifier.height(58.dp),
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
                            "Microfone sempre ligado"
                        } else {
                            "Iniciando microfone"
                        },
                        style = MaterialTheme.typography.labelLarge,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelList(
    state: TeamSpeakServiceState,
    onJoinChannel: (Int, String) -> Unit,
) {
    var passwordChannel by remember { mutableStateOf<io.github.ts3mobile.protocol.Ts3Channel?>(null) }
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
                                imageVector = if (passwordVisible) {
                                    Icons.Outlined.VisibilityOff
                                } else {
                                    Icons.Outlined.Visibility
                                },
                                contentDescription = if (passwordVisible) "Ocultar senha" else "Mostrar senha",
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
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
                            Icons.Outlined.KeyboardArrowRight
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
) {
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
        }
    }
}

@Composable
private fun ParticipantList(
    state: TeamSpeakServiceState,
    onMutedChange: (String, Boolean) -> Unit,
    onVolumeChange: (String, Int) -> Unit,
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
        ConnectionPhase.CONNECTING -> "Conectando" to MaterialTheme.colorScheme.tertiary
        ConnectionPhase.RECONNECTING -> "Reconectando" to MaterialTheme.colorScheme.tertiary
        ConnectionPhase.CONNECTED -> "Conectado" to MaterialTheme.colorScheme.primary
        ConnectionPhase.DISCONNECTING -> "Desconectando" to MaterialTheme.colorScheme.tertiary
        ConnectionPhase.ERROR -> "Falha na conexão" to MaterialTheme.colorScheme.error
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
        Text(label, style = MaterialTheme.typography.labelLarge)
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
            .padding(horizontal = 20.dp, vertical = 10.dp),
        color = foreground,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}
