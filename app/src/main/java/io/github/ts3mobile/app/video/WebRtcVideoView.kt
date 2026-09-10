package io.github.ts3mobile.app.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@Composable
fun WebRtcVideoView(
    videoTrack: VideoTrack?,
    eglBaseContext: EglBase.Context,
    modifier: Modifier = Modifier,
    scalingType: RendererCommon.ScalingType = RendererCommon.ScalingType.SCALE_ASPECT_FIT,
    mirror: Boolean = false,
) {
    val context = LocalContext.current
    val renderer = remember {
        SurfaceViewRenderer(context).apply {
            init(eglBaseContext, null)
            setEnableHardwareScaler(true)
        }
    }

    DisposableEffect(renderer, videoTrack) {
        videoTrack?.addSink(renderer)
        onDispose {
            videoTrack?.removeSink(renderer)
        }
    }

    DisposableEffect(renderer) {
        onDispose {
            renderer.release()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { renderer },
        update = { view ->
            view.setMirror(mirror)
            view.setScalingType(scalingType)
        },
    )
}
