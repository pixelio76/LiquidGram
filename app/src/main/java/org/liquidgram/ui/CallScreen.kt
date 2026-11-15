package org.liquidgram.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.neoruaa.glasmio.glass.render.backdrops.LayerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.layerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberLayerBackdrop
import org.liquidgram.data.ActiveCall
import org.liquidgram.data.CallPhase
import org.liquidgram.data.TelegramRepository

/**
 * Full-screen call (Telegram iOS look): the caller's photo blurred behind, name and status at the top, big avatar,
 * glass buttons at the bottom. Slides up over whatever screen is open while there is a call.
 */
@Composable
fun CallOverlay(repo: TelegramRepository) {
    val call by repo.activeCall.collectAsState()
    // Kept while the screen slides away after the call is dismissed.
    var shown by remember { mutableStateOf(call) }
    if (call != null) shown = call
    AnimatedVisibility(
        call != null,
        enter = slideInVertically(spring(dampingRatio = 1f, stiffness = 380f)) { it } + fadeIn(),
        exit = slideOutVertically(spring(dampingRatio = 1f, stiffness = 380f)) { it } + fadeOut(),
    ) {
        shown?.let { CallScreen(repo, it) }
    }
}

@Composable
private fun CallScreen(repo: TelegramRepository, call: ActiveCall) {
    val chat = repo.chats.collectAsState().value.firstOrNull { it.id == call.chatId } ?: repo.chatInfo(call.chatId)
    val name = chat?.title.orEmpty()
    val photo = rememberPhoto(chat?.photoPath)
    val incoming = !call.outgoing && call.phase == CallPhase.Ringing
    var muted by remember { mutableStateOf(false) }
    var camera by remember(call.id) { mutableStateOf(call.video) }
    var speaker by remember { mutableStateOf(false) }
    val soon = rememberSoon()
    val context = LocalContext.current

    BackHandler {} // the call stays on screen until it is ended
    // A finished call shows its outcome for a moment, then leaves.
    LaunchedEffect(call.over, call.id) { if (call.over) { kotlinx.coroutines.delay(1600); repo.dismissCall() } }
    // Ringtone while an incoming call rings.
    DisposableEffect(incoming) {
        val tone = if (incoming) runCatching {
            android.media.RingtoneManager.getRingtone(context, android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE))
        }.getOrNull()?.also { it.play() } else null
        onDispose { tone?.stop() }
    }

    val backdrop = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize()) {
        // Background: the photo (or the avatar colours) heavily blurred and darkened, as on iOS.
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(avatarBrush(chat?.colorIndex ?: 0))) {
            FadeImage(photo, Modifier.fillMaxSize().blur(60.dp))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.55f)))))
        }
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(72.dp))
            Avatar(name, chat?.colorIndex ?: 0, 140.dp, photoPath = chat?.photoPath)
            Spacer(Modifier.height(24.dp))
            T(name, 30.sp, Color.White, FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(6.dp))
            CallStatus(call)
            if (call.phase == CallPhase.Active) {
                Spacer(Modifier.height(10.dp))
                // Honest about what is missing: TDLib connects the call, the media engine isn't in yet.
                T("Voice isn't supported yet", 14.sp, Color.White.copy(alpha = 0.6f))
            }
            Spacer(Modifier.weight(1f))
            if (incoming) Row(Modifier.fillMaxWidth().padding(horizontal = 48.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                CallButton(backdrop, Icons.Rounded.CallEnd, "Decline", tint = Tg.Red) { repo.endCall() }
                CallButton(backdrop, if (call.video) Icons.Rounded.Videocam else Icons.Rounded.Call, "Accept", tint = Color(0xFF34C759)) { repo.acceptCall() }
            } else Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                CallButton(backdrop, Icons.Rounded.VolumeUp, "speaker", on = speaker) { speaker = !speaker; soon("Speaker") }
                CallButton(backdrop, if (camera) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff, "video", on = camera) { camera = !camera; soon("Video calls") }
                CallButton(backdrop, if (muted) Icons.Rounded.MicOff else Icons.Rounded.Mic, "mute call", on = muted) { muted = !muted }
                CallButton(backdrop, Icons.Rounded.CallEnd, "end", tint = Tg.Red, enabled = !call.over) { repo.endCall() }
            }
        }
    }
}

@Composable
private fun CallStatus(call: ActiveCall) {
    val seconds by produceState(0L, call.connectedAt) {
        while (call.connectedAt > 0) {
            value = (System.currentTimeMillis() - call.connectedAt) / 1000
            kotlinx.coroutines.delay(1000L - System.currentTimeMillis() % 1000)
        }
    }
    val text = when (call.phase) {
        CallPhase.Requesting -> "Requesting…"
        CallPhase.Ringing -> if (call.outgoing) "Ringing…" else if (call.video) "Telegram Video Call" else "Telegram Audio Call"
        CallPhase.Connecting -> "Connecting…"
        CallPhase.Active -> "%d:%02d".format(seconds / 60, seconds % 60)
        CallPhase.Ended -> "Call Ended"
        CallPhase.Declined -> "Call Declined"
        CallPhase.Missed -> "No Answer"
        CallPhase.Failed -> "Call Failed"
    }
    // The timer ticks in place; a new stage slides in.
    AnimatedContent(
        text, contentKey = { if (call.phase == CallPhase.Active) "timer" else it }, label = "callStatus",
        transitionSpec = { (fadeIn() + slideInVertically { it / 2 }) togetherWith (fadeOut() + slideOutVertically { -it / 2 }) },
    ) { T(it, 17.sp, Color.White.copy(alpha = 0.85f)) }
}

/** Round glass call button with its caption; [on] fills it white (iOS toggles), [tint] makes it a coloured button. */
@Composable
private fun CallButton(
    backdrop: LayerBackdrop, icon: ImageVector, label: String, on: Boolean = false, tint: Color? = null,
    enabled: Boolean = true, onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GlassBox(
            backdrop, Modifier.size(72.dp), CircleShape,
            tint = tint ?: if (on) Color.White else null, prominent = tint != null || on,
            onClick = if (enabled) onClick else ({}), material = null, contentAlignment = Alignment.Center,
        ) { Icon(icon, if (on && tint == null) Color.Black else Color.White, 30.dp) }
        Spacer(Modifier.height(8.dp))
        T(label, 13.sp, Color.White, FontWeight.Medium)
    }
}
