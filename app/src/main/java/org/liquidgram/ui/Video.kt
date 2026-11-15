package org.liquidgram.ui

import android.graphics.SurfaceTexture
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.liquidgram.data.Message

/** Preview of a video: its thumbnail, or the first frame of the file when there is no thumbnail. */
@Composable
private fun rememberVideoFrame(m: Message): ImageBitmap? {
    val thumb = rememberPhoto(m.videoThumb)
    val frame by produceState<ImageBitmap?>(null, m.videoPath, thumb == null) {
        val path = m.videoPath
        if (thumb == null && path != null) value = withContext(Dispatchers.IO) {
            runCatching {
                val r = MediaMetadataRetriever()
                try { r.setDataSource(path); r.getFrameAtTime(0)?.asImageBitmap() } finally { r.release() }
            }.getOrNull()
        }
    }
    return thumb ?: frame
}

private fun duration(sec: Int) = "%d:%02d".format(sec / 60, sec % 60)

/** Video (or GIF) inside a bubble. GIFs loop silently in place; videos open full screen, downloading first if needed. */
@Composable
fun VideoContent(m: Message, onPlay: (Message) -> Unit, onDownload: () -> Unit, meta: @Composable (Modifier) -> Unit) {
    val frame = rememberVideoFrame(m)
    Box(
        Modifier.width(260.dp).aspectRatio(m.photoAspect.coerceIn(0.6f, 1.8f))
            .clip(RoundedCornerShape((Look.bubbleRadius - 3f).coerceAtLeast(4f).dp))
            .background(Tg.Placeholder)
            .clickable { if (m.videoPath != null) onPlay(m) else if (!m.fileLoading) onDownload() },
        contentAlignment = Alignment.Center,
    ) {
        if (frame != null) Image(frame, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (m.isGif && m.videoPath != null) LoopVideo(m.videoPath, Modifier.fillMaxSize())
        // Centre: play, a spinner while it downloads, or the download arrow.
        if (!m.isGif || m.videoPath == null) Box(Modifier.size(48.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape), contentAlignment = Alignment.Center) {
            when {
                m.fileLoading -> Spinner(Color.White)
                m.videoPath == null -> Icon(Icons.Rounded.ArrowDownward, Color.White, 26.dp)
                else -> Icon(Icons.Rounded.PlayArrow, Color.White, 30.dp)
            }
        }
        Box(Modifier.align(Alignment.TopStart).padding(6.dp).background(Color.Black.copy(alpha = 0.35f), CircleShape).padding(horizontal = 7.dp, vertical = 2.dp)) {
            T(if (m.isGif) "GIF" else duration(m.videoDuration), 12.sp, Color.White)
        }
        if (m.text.isBlank()) meta(Modifier.align(Alignment.BottomEnd).padding(6.dp))
    }
}

/** Silent looping video drawn into a TextureView, so it is clipped and blended like any other content. */
@Composable
fun LoopVideo(path: String, modifier: Modifier) {
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    AndroidView(
        factory = { ctx ->
            TextureView(ctx).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                        player = runCatching {
                            MediaPlayer().apply {
                                setDataSource(path)
                                setSurface(Surface(st))
                                isLooping = true
                                setVolume(0f, 0f)
                                setOnPreparedListener { it.start() }
                                prepareAsync()
                            }
                        }.getOrNull()
                    }
                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean { player?.release(); player = null; return true }
                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                }
            }
        },
        modifier = modifier,
    )
    DisposableEffect(path) { onDispose { player?.release(); player = null } }
}
