package org.liquidgram.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.glass.render.drawBackdrop
import io.github.neoruaa.glasmio.glass.render.effects.blur
import io.github.neoruaa.glasmio.glass.render.effects.lens
import io.github.neoruaa.glasmio.glass.render.effects.vibrancy
import io.github.neoruaa.glasmio.glass.render.highlight.Highlight
import com.kyant.shapes.RoundedRectangle
import org.liquidgram.data.Message
import org.liquidgram.data.TelegramRepository

/** Telegram stores ❤ without the emoji selector, which Android draws as a black text glyph. */
fun emojiShown(e: String) = if (e.isNotEmpty() && !e.last().isSurrogate() && e.last() != '️') e + "️" else e

/**
 * Glass reaction strip above a message menu (iOS Telegram): quick reactions in a capsule, the chevron
 * unfolds every reaction into a grid on the same glass.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReactionBar(backdrop: Backdrop, reacted: Set<String>, @Suppress("UNUSED_PARAMETER") expandedUp: Boolean, modifier: Modifier, startAll: Boolean = false, onPick: (String) -> Unit) {
    var all by remember { mutableStateOf(startAll) }
    GlassBox(
        backdrop, modifier, RoundedRectangle(26.dp),
        material = io.github.neoruaa.glasmio.glass.NavigationGlassMaterial.RegularMedium,
    ) { Box(
        // Inside the glass: animateContentSize clips, so outside it would cut off the glass edge.
        Modifier.animateContentSize(spring(dampingRatio = 0.8f, stiffness = 500f)).padding(horizontal = 6.dp),
    ) {
        if (!all) Row(Modifier.height(REACTION_BAR_DP.dp), verticalAlignment = Alignment.CenterVertically) {
            Look.quickReactions.forEach { e -> ReactionCell(e, e in reacted) { onPick(e) } }
            Box(Modifier.size(32.dp).clip(CircleShape).background(Tg.Text.copy(alpha = 0.08f)).clickable { all = true }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.KeyboardArrowDown, Tg.Secondary, 22.dp)
            }
        } else Column(Modifier.widthIn(max = 340.dp).heightIn(max = 260.dp).verticalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
            FlowRow {
                Look.ALL_REACTIONS.forEach { e -> ReactionCell(e, e in reacted) { onPick(e) } }
                Box(Modifier.size(40.dp).clickable { all = false }, contentAlignment = Alignment.Center) { Icon(Icons.Rounded.KeyboardArrowUp, Tg.Secondary, 22.dp) }
            }
        }
    }
    }
}

@Composable
private fun ReactionCell(e: String, chosen: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(if (chosen) Tg.Accent.copy(alpha = 0.22f) else Color.Transparent).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { T(emojiShown(e), 26.sp) }
}

/** Reaction pills at the bottom of a bubble; ours is filled with the accent. Tap toggles. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReactionPills(m: Message, onReact: (String) -> Unit) {
    if (m.reactions.isEmpty()) return
    FlowRow(Modifier.padding(start = 6.dp, end = 6.dp, top = 2.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        m.reactions.forEach { r ->
            Row(
                Modifier.height(30.dp).clip(CircleShape)
                    .background(if (r.chosen) Tg.Accent else Tg.Accent.copy(alpha = 0.16f))
                    .clickable { onReact(r.emoji) }
                    .padding(horizontal = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                T(emojiShown(r.emoji), 17.sp)
                Spacer(Modifier.width(5.dp))
                T(r.count.toString(), 14.sp, if (r.chosen) Color.White else Tg.Accent, FontWeight.SemiBold)
            }
        }
    }
}

/** "Forwarded from …" line at the top of a bubble. */
@Composable
fun ForwardedLabel(name: String) = Column(Modifier.padding(start = 9.dp, top = 4.dp, end = 9.dp)) {
    T("Forwarded from", 13.sp, Tg.Accent)
    T(name, 14.sp, Tg.Accent, FontWeight.SemiBold)
}

/** Glass capsule under the chat header with the newest pinned message; tap jumps to it. */
@Composable
fun PinnedBar(backdrop: Backdrop, m: Message, modifier: Modifier, onOpen: () -> Unit, onUnpin: () -> Unit) {
    GlassBox(backdrop, modifier.fillMaxWidth(), com.kyant.shapes.Capsule(), onClick = onOpen) { Row(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(2.dp).height(30.dp).background(Tg.Accent, RoundedCornerShape(1.dp)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            T("Pinned Message", 13.sp, Tg.Accent, FontWeight.SemiBold)
            T(m.text.ifBlank { if (m.isPhoto) "🖼 Photo" else if (m.isVideo) "📹 Video" else "Message" }, 14.sp, Tg.Text)
        }
        Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onUnpin), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Close, Tg.Secondary, 18.dp)
        }
    }
    }
}

/** "Forward to…" chat picker, drawn over the chat. */
@Composable
fun ForwardPicker(repo: TelegramRepository, onPick: (Long) -> Unit, onBack: () -> Unit) = FormPage("Forward to…", onBack) { _ ->
    val chats = Plugins.chats(repo.chats.collectAsState().value)
    FormCard {
        chats.forEachIndexed { i, c ->
            if (i > 0) Line()
            Row(
                Modifier.fillMaxWidth().clickable { onPick(c.id) }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(c.title, c.colorIndex, 40.dp, saved = c.isSaved, photoPath = c.photoPath)
                Spacer(Modifier.width(12.dp))
                T(if (c.isSaved) "Saved Messages" else c.title, 17.sp, Tg.Text)
            }
        }
    }
}

/**
 * Settings → Quick Reactions: which reactions the message menu offers (in tap order, up to 7) and
 * which one a double tap on a message sends.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickReactionsScreen(onBack: () -> Unit) = FormPage("Quick Reactions", onBack) { controls ->
    SectionTitle("Preview")
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        ReactionBar(controls, setOf(Look.doubleTap), false, Modifier) {}
    }
    SectionTitle("Reactions in Menu")
    FormCard {
        FlowRow(
            Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Look.ALL_REACTIONS.forEach { e ->
                val i = Look.quickReactions.indexOf(e)
                Box(
                    // No clip: the order badge sits half outside the cell.
                    Modifier.size(40.dp)
                        .background(if (i >= 0) Tg.Accent.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(12.dp))
                        .clickable {
                            Look.quickReactions = when {
                                i >= 0 && Look.quickReactions.size > 1 -> Look.quickReactions - e
                                i < 0 && Look.quickReactions.size < 7 -> Look.quickReactions + e
                                else -> Look.quickReactions
                            }
                            if (Look.doubleTap.isNotEmpty() && Look.doubleTap !in Look.quickReactions) Look.doubleTap = Look.quickReactions.first()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    T(emojiShown(e), 26.sp)
                    if (i >= 0) Box(
                        Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-3).dp).size(16.dp).background(Tg.Accent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { T("${i + 1}", 10.sp, Color.White, FontWeight.Bold) }
                }
            }
        }
    }
    Note("Pick up to 7, in the order they appear above the message menu. Tap again to remove.")
    SectionTitle("Double Tap")
    FormCard {
        FlowRow(
            Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            (listOf("") + Look.quickReactions).forEach { e ->
                val on = Look.doubleTap == e
                Box(
                    Modifier.height(40.dp).widthIn(min = 40.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (on) Tg.Accent.copy(alpha = 0.18f) else Color.Transparent)
                        .clickable { Look.doubleTap = e }.padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center,
                ) { if (e.isEmpty()) T("Off", 15.sp, if (on) Tg.Accent else Tg.Secondary, FontWeight.SemiBold) else T(emojiShown(e), 26.sp) }
            }
        }
    }
    Note("Double tap a message to react with this emoji.")
}
