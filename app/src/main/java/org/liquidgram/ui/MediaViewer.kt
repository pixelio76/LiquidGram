package org.liquidgram.ui

import io.github.neoruaa.glasmio.glass.trackBackdropPosition
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.os.Build
import android.provider.MediaStore
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Forward
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import io.github.neoruaa.glasmio.glass.render.backdrops.layerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.liquidgram.data.Message
import java.io.File
import kotlin.math.abs

/**
 * iOS 26 Photos-style viewer for a chat's photos and videos: swipe between them, pinch to zoom, swipe down
 * or ‹ to close, tap to hide the chrome. Glass chrome over the media: back · title capsule · "…" on top,
 * thumbnail strip, share · ♥ ⓘ ↩ capsule · delete at the bottom; videos add glass −10 / play / +10 buttons
 * and a glass timeline capsule (reference: iOS Photos and the iOS 26 video player).
 */
@Composable
fun MediaViewer(
    items: List<Message>, start: Long?, chatTitle: String, onClose: () -> Unit,
    onReact: (Message, String) -> Unit, onReply: (Message) -> Unit, onForward: (Message) -> Unit,
    onDelete: (Message) -> Unit, onShowInChat: (Message) -> Unit,
) {
    var lastStart by remember { mutableStateOf(start) }
    if (start != null) lastStart = start
    BackHandler(start != null, onClose)
    AnimatedVisibility(start != null, enter = fadeIn() + scaleIn(initialScale = 0.92f), exit = fadeOut() + scaleOut(targetScale = 0.92f)) {
        if (items.isEmpty()) return@AnimatedVisibility
        val first = remember { items.indexOfFirst { it.id == lastStart }.coerceAtLeast(0) }
        val pager = rememberPagerState(first) { items.size }
        val current = items.getOrNull(pager.currentPage) ?: items.last()
        val backdrop = rememberLayerBackdrop()
        val menu = rememberGlassMenu()
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var chrome by remember { mutableStateOf(true) }
        val chromeAlpha by animateFloatAsState(if (chrome) 1f else 0f, spring(stiffness = 600f), label = "chrome")
        val drag = remember { Animatable(0f) }
        val closeAt = with(LocalDensity.current) { 110.dp.toPx() }
        val video = remember(current.id) { ViewerVideo() }
        // Playing hides the chrome after a moment, as in iOS.
        LaunchedEffect(video.playing) { if (video.playing) { delay(2500); chrome = false } }

        // iOS Photos: page colour with the chrome, black without it.
        val bg = if (chrome) Tg.Bg else Color.Black
        val fade = (1f - abs(drag.value) / (closeAt * 3)).coerceIn(0f, 1f)
        Box(Modifier.fillMaxSize().background(bg.copy(alpha = fade))) {
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop).trackBackdropPosition(backdrop)) {
                HorizontalPager(pager, Modifier.fillMaxSize().graphicsLayer { translationY = drag.value }, beyondViewportPageCount = 1, key = { items[it].id }) { i ->
                    val m = items[i]
                    var scale by remember { mutableFloatStateOf(1f) }
                    var offset by remember { mutableStateOf(Offset.Zero) }
                    val zoom = rememberTransformableState { z, pan, _ ->
                        scale = (scale * z).coerceIn(1f, 5f)
                        offset = if (scale == 1f) Offset.Zero else offset + pan
                    }
                    Box(
                        Modifier.fillMaxSize()
                            .pointerInput(Unit) { detectTapGestures(onTap = { chrome = !chrome }, onDoubleTap = { scale = if (scale > 1f) 1f else 2.5f; offset = Offset.Zero }) }
                            .pointerInput(scale) {
                                if (scale > 1f) return@pointerInput
                                detectVerticalDragGestures(
                                    onDragEnd = { scope.launch { if (abs(drag.value) > closeAt) onClose() else drag.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 500f)) } },
                                    onDragCancel = { scope.launch { drag.animateTo(0f) } },
                                ) { _, dy -> scope.launch { drag.snapTo(drag.value + dy) } }
                            }
                            .transformable(zoom, canPan = { scale > 1f }, enabled = !m.isVideo),
                        contentAlignment = Alignment.Center,
                    ) {
                        val transform = Modifier.graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }
                        if (m.isVideo && m.videoPath != null && i == pager.currentPage && pager.settledPage == i) {
                            ViewerVideoSurface(m.videoPath, m.isGif, video, Modifier.fillMaxWidth().aspectRatio(m.photoAspect.coerceIn(0.3f, 3f)))
                        } else {
                            val img = rememberPhoto(m.photoPath ?: m.videoThumb)
                            if (img != null) Image(img, null, transform.fillMaxSize(), contentScale = ContentScale.Fit)
                            else Spinner(Tg.Secondary)
                        }
                    }
                }
            }

            Box(Modifier.fillMaxSize().graphicsLayer { alpha = chromeAlpha * fade }) {
                if (chromeAlpha > 0.01f) {
                    // Top: ‹ · title capsule · "…"
                    Box(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 6.dp)) {
                        GlassCircle(backdrop, onClose, modifier = Modifier.align(Alignment.CenterStart)) { Icon(Icons.Rounded.ArrowBackIosNew, Tg.Text, 20.dp) }
                        GlassBox(backdrop, Modifier.align(Alignment.Center).widthIn(max = 220.dp), com.kyant.shapes.Capsule()) {
                            Column(Modifier.padding(horizontal = 18.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                T(if (current.out) "You" else current.senderName ?: chatTitle, 15.sp, Tg.Text, FontWeight.SemiBold)
                                T(current.time, 12.sp, Tg.Secondary)
                            }
                        }
                        MenuButton(
                            backdrop,
                            {
                                listOf(
                                    MenuItem("Save to Gallery", Icons.Rounded.Download) { saveToGallery(context, current) },
                                    MenuItem("Show in Chat", Icons.Outlined.Info) { onShowInChat(current) },
                                    MenuItem("Forward", Icons.AutoMirrored.Rounded.Forward) { onForward(current) },
                                )
                            },
                            Modifier.align(Alignment.CenterEnd),
                        ) { Icon(Icons.Rounded.MoreHoriz, Tg.Text, 24.dp, Modifier.align(Alignment.Center)) }
                    }

                    // Video: glass −10 · play · +10 in the middle.
                    if (current.isVideo && !current.isGif && current.videoPath != null) Row(
                        Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(26.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GlassCircle(backdrop, { video.seekBy(-10_000) }, size = 60.dp) { Icon(Icons.Rounded.Replay10, Tg.Text, 34.dp) }
                        GlassCircle(backdrop, { video.toggle() }, size = 80.dp) { Icon(if (video.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, Tg.Text, 48.dp) }
                        GlassCircle(backdrop, { video.seekBy(10_000) }, size = 60.dp) { Icon(Icons.Rounded.Forward10, Tg.Text, 34.dp) }
                    }

                    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 8.dp)) {
                        if (current.isVideo && !current.isGif && current.videoPath != null) Timeline(backdrop, video, Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                        Thumbnails(items, pager.currentPage) { scope.launch { pager.animateScrollToPage(it) } }
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                            GlassCircle(backdrop, { share(context, current) }, modifier = Modifier.align(Alignment.CenterStart)) { Icon(Icons.Rounded.IosShare, Tg.Text, 22.dp) }
                            val liked = current.reactions.any { it.chosen && it.emoji == "❤" }
                            GlassPill(backdrop, {}, Modifier.align(Alignment.Center)) {
                                Box(Modifier.size(40.dp).clip(CircleShape).clickable { onReact(current, "❤") }, contentAlignment = Alignment.Center) {
                                    Icon(if (liked) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder, if (liked) Tg.Red else Tg.Text, 22.dp)
                                }
                                Box(Modifier.size(40.dp).clip(CircleShape).clickable { onShowInChat(current) }, contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Info, Tg.Text, 22.dp) }
                                Box(Modifier.size(40.dp).clip(CircleShape).clickable { onReply(current) }, contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Rounded.Reply, Tg.Text, 22.dp) }
                            }
                            GlassCircle(backdrop, { onDelete(current) }, modifier = Modifier.align(Alignment.CenterEnd)) { Icon(Icons.Outlined.Delete, Tg.Text, 22.dp) }
                        }
                    }
                }
            }
            GlassMenuHost(menu, backdrop)
        }
    }
}

/** iOS Photos strip: small thumbnails, the current one wider; tap jumps there. */
@Composable
private fun Thumbnails(items: List<Message>, current: Int, onPick: (Int) -> Unit) {
    val list = rememberLazyListState()
    LaunchedEffect(current) { list.animateScrollToItem((current - 4).coerceAtLeast(0)) }
    LazyRow(Modifier.fillMaxWidth().height(40.dp), list, contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        itemsIndexed(items, key = { _, m -> m.id }) { i, m ->
            val w by animateFloatAsState(if (i == current) 52f else 28f, spring(dampingRatio = 0.8f, stiffness = 500f), label = "thumb")
            val img = rememberPhoto(m.photoPath ?: m.videoThumb)
            Box(
                Modifier.width(w.dp).fillMaxHeight().padding(horizontal = if (i == current) 4.dp else 0.dp)
                    .clip(RoundedCornerShape(3.dp)).background(Tg.Placeholder).clickable { onPick(i) },
            ) { if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
    }
}

/** Glass capsule: elapsed · track · −remaining; drag or tap the track to seek. */
@Composable
private fun Timeline(backdrop: io.github.neoruaa.glasmio.glass.render.Backdrop, v: ViewerVideo, modifier: Modifier) {
    GlassBox(backdrop, modifier.fillMaxWidth().height(48.dp), com.kyant.shapes.Capsule()) { Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        T(clock(v.position), 15.sp, Tg.Secondary)
        var width by remember { mutableIntStateOf(1) }
        val p = if (v.duration > 0) v.position.toFloat() / v.duration else 0f
        Box(
            Modifier.weight(1f).padding(horizontal = 12.dp).height(28.dp)
                .onSizeChanged { width = it.width.coerceAtLeast(1) }
                .pointerInput(v) { detectTapGestures { v.seekTo((it.x / width * v.duration).toInt()) } }
                .pointerInput(v) { detectHorizontalDragGestures { ch, _ -> v.seekTo((ch.position.x / width * v.duration).toInt().coerceIn(0, v.duration)) } },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(6.dp).background(Tg.Text.copy(alpha = 0.15f), CircleShape))
            Box(Modifier.fillMaxWidth(p.coerceIn(0f, 1f)).height(6.dp).background(Tg.Text.copy(alpha = 0.55f), CircleShape))
        }
        T("−" + clock((v.duration - v.position).coerceAtLeast(0)), 15.sp, Tg.Secondary)
    } }
}

private fun clock(ms: Int) = (ms / 1000).let { "%d:%02d".format(it / 60, it % 60) }

/** The viewer's own player, so the glass buttons and timeline can drive it. */
class ViewerVideo {
    var player: MediaPlayer? = null
    var playing by mutableStateOf(false)
    var position by mutableIntStateOf(0)
    var duration by mutableIntStateOf(0)

    fun toggle() { val p = player ?: return; if (p.isPlaying) p.pause() else p.start(); playing = p.isPlaying }
    fun seekTo(ms: Int) { player?.seekTo(ms); position = ms }
    fun seekBy(ms: Int) = seekTo((position + ms).coerceIn(0, duration))
}

@Composable
private fun ViewerVideoSurface(path: String, loop: Boolean, v: ViewerVideo, modifier: Modifier) {
    AndroidView(
        factory = { ctx ->
            TextureView(ctx).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                        v.player = runCatching {
                            MediaPlayer().apply {
                                setDataSource(path)
                                setSurface(Surface(st))
                                isLooping = loop
                                setOnPreparedListener { it.start(); v.duration = it.duration; v.playing = true }
                                setOnCompletionListener { v.playing = false }
                                prepareAsync()
                            }
                        }.getOrNull()
                    }
                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean { v.player?.release(); v.player = null; v.playing = false; return true }
                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                }
            }
        },
        modifier = modifier,
    )
    LaunchedEffect(v) {
        while (true) { v.player?.let { p -> runCatching { if (p.isPlaying) v.position = p.currentPosition } }; delay(100) }
    }
    DisposableEffect(path) { onDispose { v.player?.release(); v.player = null; v.playing = false } }
}

private fun mediaFile(m: Message): File? = (m.photoPath ?: m.videoPath)?.let(::File)?.takeIf { it.exists() }

private fun share(context: Context, m: Message) {
    val f = mediaFile(m) ?: return
    val uri = runCatching { FileProvider.getUriForFile(context, context.packageName + ".files", f) }.getOrNull() ?: return
    val send = Intent(Intent.ACTION_SEND).setType(if (m.isVideo) "video/*" else "image/*")
        .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(Intent.createChooser(send, null)) }
}

/** Copies the photo or video into the phone's gallery (Pictures/LiquidGram, Movies/LiquidGram). */
private fun saveToGallery(context: Context, m: Message) {
    val f = mediaFile(m) ?: return
    // shortcut: Android 8–9 need the storage permission for MediaStore writes; there we hand the file to the share sheet instead.
    if (Build.VERSION.SDK_INT < 29) { share(context, m); return }
    val video = m.isVideo
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "LiquidGram_${System.currentTimeMillis()}.${f.extension.ifEmpty { if (video) "mp4" else "jpg" }}")
        put(MediaStore.MediaColumns.MIME_TYPE, if (video) "video/mp4" else "image/jpeg")
        put(MediaStore.MediaColumns.RELATIVE_PATH, if (video) "Movies/LiquidGram" else "Pictures/LiquidGram")
    }
    val ok = runCatching {
        val uri = context.contentResolver.insert(if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
        context.contentResolver.openOutputStream(uri)!!.use { out -> f.inputStream().use { it.copyTo(out) } }
    }.isSuccess
    Toast.makeText(context, Strings.tr(if (ok) "Saved to Gallery" else "Error"), Toast.LENGTH_SHORT).show()
}
