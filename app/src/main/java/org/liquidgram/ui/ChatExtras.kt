package org.liquidgram.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.util.Rational
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewOutlineProvider
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.LifecycleOwner
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.glass.render.drawBackdrop
import io.github.neoruaa.glasmio.glass.render.effects.blur
import io.github.neoruaa.glasmio.glass.render.effects.lens
import io.github.neoruaa.glasmio.glass.render.effects.vibrancy
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.delay
import org.liquidgram.data.Message
import java.io.File

// ------------------------------------------------------------------ files

/** Opens a downloaded file in whatever app handles its type. */
fun openFile(context: Context, path: String, name: String) {
    val file = File(path)
    val uri = runCatching { FileProvider.getUriForFile(context, context.packageName + ".files", file) }.getOrNull() ?: return
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "*/*"
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(Intent.createChooser(intent, name)) }
        .onFailure { Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show() }
}

/** Document row inside a bubble: icon (download arrow / spinner / file), name, size. Tap downloads or opens. */
@Composable
fun DocumentContent(m: Message, meta: Color, onDownload: () -> Unit) {
    val context = LocalContext.current
    Row(
        Modifier.widthIn(max = 260.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .clickable { val p = m.filePath; if (p != null) openFile(context, p, m.fileName) else if (!m.fileLoading) onDownload() }
            .padding(start = 6.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).background(Tg.Accent, CircleShape), contentAlignment = Alignment.Center) {
            when {
                m.fileLoading -> Spinner(Color.White)
                m.filePath == null -> Icon(Icons.Rounded.ArrowDownward, Color.White, 24.dp)
                else -> Icon(Icons.Rounded.Description, Color.White, 22.dp)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f, fill = false)) {
            T(m.fileName, 15.sp, Tg.Text, FontWeight.Medium, maxLines = 2)
            Row(verticalAlignment = Alignment.CenterVertically) {
                T(m.fileSize, 13.sp, meta)
                Spacer(Modifier.width(8.dp))
                T(m.time, 11.sp, meta)
            }
        }
    }
}


// ------------------------------------------------------------------ round videos

/** Circle-clipped video: plays muted on a loop like Telegram; tap toggles sound. */
@Composable
fun RoundVideo(path: String?, size: Dp, onDownload: () -> Unit) {
    var sound by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    Box(Modifier.size(size).clip(CircleShape).background(Tg.Placeholder).clickable {
        if (path == null) onDownload() else { sound = !sound; player?.let { p -> val v = if (sound) 1f else 0f; p.setVolume(v, v); if (sound) p.seekTo(0) } }
    }, contentAlignment = Alignment.Center) {
        if (path == null) { Spinner(Color.White); return@Box }
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    outlineProvider = object : ViewOutlineProvider() {
                        override fun getOutline(view: View, outline: Outline) = outline.setOval(0, 0, view.width, view.height)
                    }
                    clipToOutline = true
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                            player = runCatching {
                                MediaPlayer().apply {
                                    setDataSource(path)
                                    setSurface(Surface(st))
                                    isLooping = true
                                    setVolume(0f, 0f)
                                    setOnVideoSizeChangedListener { _, vw, vh -> centerCrop(this@apply, vw, vh) }
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
            modifier = Modifier.fillMaxSize(),
        )
        if (!sound) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp).background(Color.Black.copy(alpha = 0.35f), CircleShape).padding(5.dp)) {
            Icon(Icons.Rounded.VolumeOff, Color.White, 14.dp)
        }
    }
    DisposableEffect(Unit) { onDispose { player?.release(); player = null } }
}

/** Non-square videos fill the circle (cropped) instead of being squashed. */
private fun TextureView.centerCrop(@Suppress("UNUSED_PARAMETER") mp: MediaPlayer, vw: Int, vh: Int) {
    if (vw == 0 || vh == 0 || width == 0) return
    val m = Matrix()
    if (vw > vh) m.setScale(vw.toFloat() / vh, 1f, width / 2f, height / 2f) else m.setScale(1f, vh.toFloat() / vw, width / 2f, height / 2f)
    setTransform(m)
}

/**
 * Records a round video (CameraX). Hold the button to start; letting go keeps recording until Send or Cancel in
 * the overlay. The recording is persistent, so the camera can be switched mid-recording (front ↔ back and every
 * other module the phone exposes); zoom by pinch or the 0.5×/1×/2× chips; stabilization when the camera has it.
 */
@androidx.annotation.OptIn(androidx.camera.video.ExperimentalPersistentRecording::class)
class RoundRecorder(private val context: Context) {
    val preview: PreviewView = PreviewView(context).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE   // TextureView, so it can be clipped round
        scaleType = PreviewView.ScaleType.FILL_CENTER
    }
    var active by mutableStateOf(false)
        private set
    var startedAt by mutableLongStateOf(0L)
        private set
    var zoom by mutableStateOf(1f)
        private set
    var zoomRange by mutableStateOf(1f..1f)
        private set
    /** Cameras to cycle through: the default one first (front or back per settings), then the rest. */
    var cameraCount by mutableStateOf(0)
        private set
    private var cameras = emptyList<androidx.camera.core.CameraInfo>()
    private var cameraIndex = 0
    private var provider: ProcessCameraProvider? = null
    private var owner: LifecycleOwner? = null
    private var camera: androidx.camera.core.Camera? = null
    private var previewCase: Preview? = null
    private var capture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var stopRequested = false
    private var cancelled = false
    private var onDone: (File?, Int) -> Unit = { _, _ -> }

    @SuppressLint("MissingPermission") // the caller checks CAMERA and RECORD_AUDIO first
    fun start(owner: LifecycleOwner, onDone: (File?, Int) -> Unit) {
        val file = File(context.cacheDir, "round_${System.currentTimeMillis()}.mp4")
        this.owner = owner; this.onDone = onDone
        stopRequested = false; cancelled = false; active = true; startedAt = System.currentTimeMillis()
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val p = runCatching { future.get() }.getOrNull() ?: run { active = false; return@addListener }
            provider = p
            if (stopRequested) { active = false; return@addListener }
            val back = Look.roundBackCamera
            cameras = p.availableCameraInfos.sortedBy { info ->
                val facing = info.lensFacing
                when {
                    back && facing == CameraSelector.LENS_FACING_BACK -> 0
                    !back && facing == CameraSelector.LENS_FACING_FRONT -> 0
                    else -> 1
                }
            }
            cameraCount = cameras.size
            if (cameras.isEmpty()) { active = false; Toast.makeText(context, "No camera", Toast.LENGTH_SHORT).show(); return@addListener }
            cameraIndex = 0
            val recorder = Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.SD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))).build()
            val stabilized = runCatching { Recorder.getVideoCapabilities(cameras[0]).isStabilizationSupported }.getOrDefault(false)
            capture = VideoCapture.Builder(recorder).apply { if (stabilized) setVideoStabilizationEnabled(true) }.build()
            val previewStable = runCatching { Preview.getPreviewCapabilities(cameras[0]).isStabilizationSupported }.getOrDefault(false)
            previewCase = Preview.Builder().apply { if (previewStable) setPreviewStabilizationEnabled(true) }.build()
                .also { it.surfaceProvider = preview.surfaceProvider }
            if (!bind()) { active = false; return@addListener }
            startedAt = System.currentTimeMillis()
            recording = capture!!.output.prepareRecording(context, FileOutputOptions.Builder(file).build())
                .withAudioEnabled().asPersistentRecording()
                .start(ContextCompat.getMainExecutor(context)) { e ->
                    if (e is VideoRecordEvent.Finalize) {
                        provider?.unbindAll()
                        active = false
                        val sec = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
                        val ok = !cancelled && e.error == VideoRecordEvent.Finalize.ERROR_NONE && sec >= 1
                        if (!ok) file.delete()
                        this.onDone(file.takeIf { ok }, sec.coerceAtLeast(1))
                    }
                }
        }, ContextCompat.getMainExecutor(context))
    }

    /** (Re)binds preview + capture to the current camera; the persistent recording carries on across it. */
    private fun bind(): Boolean {
        val p = provider ?: return false
        val o = owner ?: return false
        val info = cameras.getOrNull(cameraIndex) ?: return false
        // 1:1 viewport: Telegram video messages are square.
        val group = UseCaseGroup.Builder().addUseCase(previewCase!!).addUseCase(capture!!)
            .setViewPort(ViewPort.Builder(Rational(1, 1), preview.display?.rotation ?: Surface.ROTATION_0).build()).build()
        return runCatching {
            p.unbindAll()
            camera = p.bindToLifecycle(o, info.cameraSelector, group)
            val z = camera!!.cameraInfo.zoomState.value
            zoomRange = (z?.minZoomRatio ?: 1f)..(z?.maxZoomRatio ?: 1f)
            zoom = 1f.coerceIn(zoomRange)
            camera!!.cameraControl.setZoomRatio(zoom)
            true
        }.getOrElse { Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show(); false }
    }

    /** Next camera module (front, back, ultra-wide… whatever the phone lists), without stopping the recording. */
    fun switchCamera() {
        if (cameras.size < 2) return
        cameraIndex = (cameraIndex + 1) % cameras.size
        bind()
    }

    fun zoomTo(ratio: Float) {
        zoom = ratio.coerceIn(zoomRange)
        camera?.cameraControl?.setZoomRatio(zoom)
    }

    fun stop(cancel: Boolean) {
        cancelled = cancel
        stopRequested = true
        val r = recording
        recording = null
        if (r != null) r.stop() else { provider?.unbindAll(); active = false }
    }
}

/** Full-screen round camera preview: timer, zoom chips, pinch to zoom, switch camera, cancel and send. */
@Composable
fun RoundRecordingOverlay(rec: RoundRecorder) {
    androidx.activity.compose.BackHandler(rec.active) { rec.stop(cancel = true) }
    AnimatedVisibility(rec.active, enter = fadeIn() + scaleIn(initialScale = 0.8f), exit = fadeOut() + scaleOut(targetScale = 0.8f)) {
        var elapsed by remember { mutableLongStateOf(0L) }
        LaunchedEffect(Unit) { while (true) { elapsed = System.currentTimeMillis() - rec.startedAt; delay(200) } }
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f))
                .clickable(interactionSource = null, indication = null) {},   // swallow taps behind the overlay
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AndroidView(
                    { rec.preview },
                    Modifier.size(280.dp).clip(CircleShape).pointerInput(Unit) {
                        detectTransformGestures { _, _, z, _ -> rec.zoomTo(rec.zoom * z) }
                    },
                )
                Spacer(Modifier.size(14.dp))
                T("%d:%02d".format(elapsed / 60000, (elapsed / 1000) % 60), 17.sp, Color.White, FontWeight.SemiBold)
                Spacer(Modifier.size(12.dp))
                // 0.5× appears when the camera reaches the ultra-wide module through zoom.
                val stops = listOf(rec.zoomRange.start, 1f, 2f, 5f).filter { it in rec.zoomRange }.distinct()
                if (stops.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    stops.forEach { z ->
                        val on = kotlin.math.abs(rec.zoom - z) < 0.05f
                        Box(
                            Modifier.size(40.dp).background(if (on) Color.White else Color.White.copy(alpha = 0.18f), CircleShape).clip(CircleShape).clickable { rec.zoomTo(z) },
                            contentAlignment = Alignment.Center,
                        ) { T(if (z < 1f) "%.1f×".format(z).replace(".0×", "×") else "${z.toInt()}×", 13.sp, if (on) Color.Black else Color.White, FontWeight.SemiBold) }
                    }
                }
                Spacer(Modifier.size(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundButton(Icons.Rounded.Close, Color.White.copy(alpha = 0.18f)) { rec.stop(cancel = true) }
                    if (rec.cameraCount > 1) RoundButton(Icons.Rounded.Cameraswitch, Color.White.copy(alpha = 0.18f)) { rec.switchCamera() }
                    RoundButton(Icons.Rounded.ArrowUpward, Tg.Accent, big = true) { rec.stop(cancel = false) }
                }
            }
        }
    }
}

@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, big: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(if (big) 64.dp else 52.dp).background(color, CircleShape).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, Color.White, if (big) 30.dp else 24.dp) }
}

// ------------------------------------------------------------------ search

/** Glass search field that replaces the chat header while searching. */
@Composable
fun ChatSearchBar(backdrop: Backdrop, query: String, onQuery: (String) -> Unit, onCancel: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassBox(backdrop, Modifier.weight(1f).heightIn(min = 44.dp), com.kyant.shapes.Capsule()) { Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, Tg.Secondary, 20.dp)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f).padding(vertical = 11.dp)) {
                if (query.isEmpty()) T("Search messages", 17.sp, Tg.Secondary)
                BasicTextField(
                    query, onQuery, Modifier.fillMaxWidth().focusRequester(focus), singleLine = true,
                    textStyle = TextStyle(color = Tg.Text, fontSize = 17.sp * Look.textScale, fontFamily = appFont()), cursorBrush = SolidColor(Tg.Accent),
                )
            }
        } }
        GlassPill(backdrop, onCancel) { T("Cancel", 17.sp, Tg.Text, FontWeight.Medium) }
    }
}

/** Results under the search bar: who, when, the text; tap jumps to the message. */
@Composable
fun ChatSearchResults(backdrop: Backdrop, results: List<Message>?, chatTitle: String, onPick: (Message) -> Unit) {
    if (results == null) return
    GlassBox(
        backdrop, Modifier.padding(horizontal = 12.dp).fillMaxWidth().heightIn(max = 360.dp), RoundedRectangle(24.dp),
        material = io.github.neoruaa.glasmio.glass.NavigationGlassMaterial.RegularMedium,
    ) {
        if (results.isEmpty()) { T("No results", 15.sp, Tg.Secondary, modifier = Modifier.padding(18.dp)); return@GlassBox }
        LazyColumn {
            items(results, key = { it.id }) { m ->
                Column(Modifier.fillMaxWidth().clickable { onPick(m) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row {
                        T(if (m.out) "You" else m.senderName ?: chatTitle, 15.sp, Tg.Text, FontWeight.SemiBold, Modifier.weight(1f))
                        T(m.time, 13.sp, Tg.Secondary)
                    }
                    T(m.text.ifBlank { if (m.isPhoto) "🖼 Photo" else if (m.isFile) "📎 " + m.fileName else "Message" }, 14.sp, Tg.Secondary, maxLines = 2)
                }
            }
        }
    }
}

/** True when the app already holds [permission]. */
fun hasPermission(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
