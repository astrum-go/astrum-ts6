package io.github.ts3mobile.app.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.ScreenShare
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.ts3mobile.app.service.WatchedStream
import io.github.ts3mobile.app.video.WebRtcVideoView
import kotlinx.coroutines.delay
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.VideoTrack

@Composable
fun FullscreenStreamOverlay(
    stream: WatchedStream,
    allWatchedStreams: List<WatchedStream>,
    videoTrack: VideoTrack?,
    eglBaseContext: EglBase.Context,
    onClose: () -> Unit,
    onSelectStream: (String) -> Unit,
    onToggleOrientation: (Boolean) -> Unit,
    isLandscape: Boolean,
) {
    val activity = LocalContext.current as? Activity
    var showHud by remember { mutableStateOf(true) }
    var scalingType by remember { mutableStateOf(RendererCommon.ScalingType.SCALE_ASPECT_FIT) }
    var currentZoom by remember { mutableFloatStateOf(1f) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    DisposableEffect(activity) {
        val window = activity?.window
        val insetsController = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        insetsController?.hide(WindowInsetsCompat.Type.systemBars())
        insetsController?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
            onToggleOrientation(false)
        }
    }

    LaunchedEffect(showHud, lastInteractionTime) {
        if (showHud) {
            delay(3500)
            showHud = false
        }
    }

    BackHandler(onBack = onClose)

    val currentIndex = allWatchedStreams.indexOfFirst { it.streamId == stream.streamId }.coerceAtLeast(0)
    val totalCount = allWatchedStreams.size

    val onPrev: (() -> Unit)? = if (totalCount > 1) {
        {
            val prevIdx = if (currentIndex - 1 < 0) totalCount - 1 else currentIndex - 1
            onSelectStream(allWatchedStreams[prevIdx].streamId)
            lastInteractionTime = System.currentTimeMillis()
        }
    } else null

    val onNext: (() -> Unit)? = if (totalCount > 1) {
        {
            val nextIdx = (currentIndex + 1) % totalCount
            onSelectStream(allWatchedStreams[nextIdx].streamId)
            lastInteractionTime = System.currentTimeMillis()
        }
    } else null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (videoTrack != null) {
            WebRtcVideoView(
                videoTrack = videoTrack,
                eglBaseContext = eglBaseContext,
                scalingType = scalingType,
                enableZoom = true,
                onZoomChanged = { zoom -> currentZoom = zoom },
                onTap = {
                    showHud = !showHud
                    lastInteractionTime = System.currentTimeMillis()
                },
                onInteraction = {
                    lastInteractionTime = System.currentTimeMillis()
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(color = Color.White)
                    Text(
                        text = "Carregando transmissão...",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        // Side Navigation Arrow - Previous
        AnimatedVisibility(
            visible = showHud && totalCount > 1,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .displayCutoutPadding(),
        ) {
            if (onPrev != null) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier.padding(start = 16.dp),
                ) {
                    IconButton(onClick = onPrev, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Transmissão anterior",
                            tint = Color.White,
                        )
                    }
                }
            }
        }

        // Side Navigation Arrow - Next
        AnimatedVisibility(
            visible = showHud && totalCount > 1,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .displayCutoutPadding(),
        ) {
            if (onNext != null) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier.padding(end = 16.dp),
                ) {
                    IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                            contentDescription = "Próxima transmissão",
                            tint = Color.White,
                        )
                    }
                }
            }
        }

        // Top HUD Bar
        AnimatedVisibility(
            visible = showHud,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.75f))
                    .statusBarsPadding()
                    .displayCutoutPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Sair da tela cheia",
                            tint = Color.White,
                        )
                    }
                    Icon(
                        imageVector = if (stream.isScreenOrWindow) {
                            Icons.AutoMirrored.Outlined.ScreenShare
                        } else {
                            Icons.Filled.Videocam
                        },
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                    Column {
                        Text(
                            text = stream.nickname.ifBlank { "Vídeo" },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stream.displayFullLabel(),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (totalCount > 1) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White.copy(alpha = 0.15f),
                        ) {
                            Text(
                                text = "${currentIndex + 1}/$totalCount",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.FullscreenExit,
                            contentDescription = "Sair da tela cheia",
                            tint = Color.White,
                        )
                    }
                }
            }
        }

        // Bottom HUD Bar
        AnimatedVisibility(
            visible = showHud,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.75f))
                    .navigationBarsPadding()
                    .displayCutoutPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (currentZoom > 1.05f) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                    ) {
                        Text(
                            text = "${"%.1f".format(currentZoom)}x • Toque duplo para 1x",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                } else {
                    Text(
                        text = if (scalingType == RendererCommon.ScalingType.SCALE_ASPECT_FIT) {
                            "Ajustado (100% visível)"
                        } else {
                            "Preenchido (Tela cheia)"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.75f),
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(
                        onClick = {
                            scalingType = if (scalingType == RendererCommon.ScalingType.SCALE_ASPECT_FIT) {
                                RendererCommon.ScalingType.SCALE_ASPECT_FILL
                            } else {
                                RendererCommon.ScalingType.SCALE_ASPECT_FIT
                            }
                            lastInteractionTime = System.currentTimeMillis()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FitScreen,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (scalingType == RendererCommon.ScalingType.SCALE_ASPECT_FIT) "Ajustar" else "Preencher",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }

                    IconButton(
                        onClick = {
                            onToggleOrientation(!isLandscape)
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ScreenRotation,
                            contentDescription = if (isLandscape) "Modo retrato" else "Girar para paisagem",
                            tint = if (isLandscape) MaterialTheme.colorScheme.primary else Color.White,
                        )
                    }
                }
            }
        }
    }
}
