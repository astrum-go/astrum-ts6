package br.app.astrum.ts6.app.video

import android.util.Log
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import kotlin.math.abs

@Composable
fun WebRtcVideoView(
    videoTrack: VideoTrack?,
    eglBaseContext: EglBase.Context,
    modifier: Modifier = Modifier,
    scalingType: RendererCommon.ScalingType = RendererCommon.ScalingType.SCALE_ASPECT_FIT,
    mirror: Boolean = false,
    enableZoom: Boolean = false,
    onZoomChanged: ((Float) -> Unit)? = null,
    onTap: (() -> Unit)? = null,
    onInteraction: (() -> Unit)? = null,
) {
    var activeRenderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    var videoWidth by remember { mutableIntStateOf(0) }
    var videoHeight by remember { mutableIntStateOf(0) }
    var videoRotation by remember { mutableIntStateOf(0) }

    var lastTapTime by remember { mutableLongStateOf(0L) }
    var lastTapPosition by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(videoTrack) {
        scale = 1f
        offset = Offset.Zero
        onZoomChanged?.invoke(1f)
    }

    LaunchedEffect(scalingType) {
        scale = 1f
        offset = Offset.Zero
        onZoomChanged?.invoke(1f)
    }

    DisposableEffect(activeRenderer, videoTrack) {
        val renderer = activeRenderer
        if (renderer != null && videoTrack != null) {
            videoTrack.addSink(renderer)
        }
        onDispose {
            if (renderer != null && videoTrack != null) {
                videoTrack.removeSink(renderer)
            }
        }
    }

    val gestureModifier = if (enableZoom) {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                onInteraction?.invoke()
                val downTime = System.currentTimeMillis()
                val downPos = down.position
                var zoomAccum = 1f
                var panAccum = Offset.Zero
                var pastTouchSlop = false
                val touchSlop = viewConfiguration.touchSlop

                while (true) {
                    val event = awaitPointerEvent()
                    val canceled = event.changes.any { it.isConsumed }
                    if (canceled) break

                    val downCount = event.changes.count { it.pressed }
                    if (downCount == 0) {
                        break
                    }

                    if (downCount >= 2) {
                        onInteraction?.invoke()
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()

                        if (!pastTouchSlop) {
                            zoomAccum *= zoomChange
                            panAccum += panChange
                            val centroidSize = event.calculateCentroidSize(useCurrent = false)
                            val zoomMotion = abs(1 - zoomAccum) * centroidSize
                            val panMotion = panAccum.getDistance()
                            if (zoomMotion > touchSlop || panMotion > touchSlop) {
                                pastTouchSlop = true
                            }
                        }

                        if (pastTouchSlop) {
                            if (zoomChange != 1f || panChange != Offset.Zero) {
                                val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                                scale = newScale
                                if (newScale <= 1.05f) {
                                    offset = Offset.Zero
                                } else {
                                    val maxOffsetX = (size.width * (newScale - 1f)) / 2f
                                    val maxOffsetY = (size.height * (newScale - 1f)) / 2f
                                    offset = Offset(
                                        x = (offset.x + panChange.x).coerceIn(-maxOffsetX, maxOffsetX),
                                        y = (offset.y + panChange.y).coerceIn(-maxOffsetY, maxOffsetY),
                                    )
                                }
                                onZoomChanged?.invoke(scale)
                            }
                            event.changes.forEach { it.consume() }
                        }
                    } else if (downCount == 1 && scale > 1.05f) {
                        val change = event.changes.first { it.pressed }
                        val panChange = change.position - change.previousPosition
                        panAccum += panChange
                        if (!pastTouchSlop) {
                            if (panAccum.getDistance() > touchSlop) {
                                pastTouchSlop = true
                            }
                        }
                        if (pastTouchSlop) {
                            onInteraction?.invoke()
                            val maxOffsetX = (size.width * (scale - 1f)) / 2f
                            val maxOffsetY = (size.height * (scale - 1f)) / 2f
                            offset = Offset(
                                x = (offset.x + panChange.x).coerceIn(-maxOffsetX, maxOffsetX),
                                y = (offset.y + panChange.y).coerceIn(-maxOffsetY, maxOffsetY),
                            )
                            change.consume()
                        }
                    }
                }

                if (!pastTouchSlop) {
                    val upTime = System.currentTimeMillis()
                    val tapDuration = upTime - downTime
                    if (tapDuration < 450) {
                        val isDoubleTap = (upTime - lastTapTime < 350) &&
                            ((downPos - lastTapPosition).getDistance() < touchSlop * 4)
                        if (isDoubleTap) {
                            lastTapTime = 0L
                            val newScale = if (scale > 1.2f) 1f else 2.5f
                            scale = newScale
                            offset = Offset.Zero
                            onZoomChanged?.invoke(newScale)
                            onInteraction?.invoke()
                        } else {
                            lastTapTime = upTime
                            lastTapPosition = downPos
                            onTap?.invoke()
                            onInteraction?.invoke()
                        }
                    }
                }
            }
        }
    } else Modifier

    val effectiveWidth = if (videoRotation == 90 || videoRotation == 270) videoHeight else videoWidth
    val effectiveHeight = if (videoRotation == 90 || videoRotation == 270) videoWidth else videoHeight
    val videoAspectRatio = if (effectiveWidth > 0 && effectiveHeight > 0) {
        effectiveWidth.toFloat() / effectiveHeight.toFloat()
    } else {
        16f / 9f
    }

    BoxWithConstraints(
        modifier = modifier
            .clipToBounds()
            .then(gestureModifier),
        contentAlignment = Alignment.Center,
    ) {
        val containerWidth = maxWidth.value
        val containerHeight = maxHeight.value
        val containerAspectRatio = if (containerHeight > 0) containerWidth / containerHeight else 1f

        val (targetWidth, targetHeight) = remember(
            scalingType, videoAspectRatio, containerWidth, containerHeight
        ) {
            if (scalingType == RendererCommon.ScalingType.SCALE_ASPECT_FIT) {
                if (videoAspectRatio > containerAspectRatio) {
                    // Video is wider than container: fit width, letterbox vertically
                    val w = containerWidth
                    val h = containerWidth / videoAspectRatio
                    w to h
                } else {
                    // Video is taller than container: fit height, pillarbox horizontally
                    val h = containerHeight
                    val w = containerHeight * videoAspectRatio
                    w to h
                }
            } else {
                // SCALE_ASPECT_FILL: fill 100% of container, crop excess symmetrically
                if (videoAspectRatio > containerAspectRatio) {
                    // Video is wider: fill height, crop sides
                    val h = containerHeight
                    val w = containerHeight * videoAspectRatio
                    w to h
                } else {
                    // Video is taller: fill width, crop top/bottom
                    val w = containerWidth
                    val h = containerWidth / videoAspectRatio
                    w to h
                }
            }
        }

        AndroidView(
            modifier = Modifier
                .requiredSize(targetWidth.dp, targetHeight.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            factory = { context ->
                SurfaceViewRenderer(context).apply {
                    setZOrderMediaOverlay(true)
                    init(eglBaseContext, object : RendererCommon.RendererEvents {
                        override fun onFirstFrameRendered() {
                            Log.i("WebRtcVideoView", "First video frame rendered successfully")
                        }

                        override fun onFrameResolutionChanged(width: Int, height: Int, rotation: Int) {
                            Log.i("WebRtcVideoView", "Video resolution changed: ${width}x${height} rot=$rotation")
                            videoWidth = width
                            videoHeight = height
                            videoRotation = rotation
                        }
                    })
                    setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                    setMirror(mirror)
                    videoTrack?.addSink(this)
                    activeRenderer = this
                }
            },
            update = { renderer ->
                renderer.setMirror(mirror)
                if (activeRenderer != renderer) {
                    activeRenderer = renderer
                }
            },
            onRelease = { renderer ->
                if (activeRenderer == renderer) {
                    activeRenderer = null
                }
                runCatching {
                    videoTrack?.removeSink(renderer)
                    renderer.release()
                }
            },
        )
    }
}
