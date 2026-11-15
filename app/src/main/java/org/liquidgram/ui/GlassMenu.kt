package org.liquidgram.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.glass.render.Backdrop
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.ui.input.pointer.pointerInput
import io.github.neoruaa.glasmio.glass.render.drawPlainBackdrop
import io.github.neoruaa.glasmio.glass.render.effects.blur

internal const val REACTION_BAR_DP = 52

class MenuItem(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val destructive: Boolean = false, val onClick: () -> Unit = {})

/** One menu per screen; triggers call [show] with their bounds, the [GlassMenuHost] draws it. */
class GlassMenuState {
    var anchor by mutableStateOf(Rect.Zero)
        private set
    var items by mutableStateOf(emptyList<MenuItem>())
        private set
    /** true: the panel grows out of the button and covers it (toolbar menus); false: opens beside it (context menus). */
    var cover by mutableStateOf(true)
        private set
    var open by mutableStateOf(false)
        private set
    /** The button's look (content and glass tint), drawn by the blob while it is still button-sized. */
    var face by mutableStateOf<(@Composable () -> Unit)?>(null)
        private set
    var faceTint by mutableStateOf(Color.Unspecified)
        private set
    /** Message menus: reaction bar above the panel; [reacted] are ours (drawn selected). */
    var onReact by mutableStateOf<((String) -> Unit)?>(null)
        private set
    var reacted by mutableStateOf(emptySet<String>())
        private set
    /** Message menus: the message itself, redrawn in place over the blur (it does not move). */
    var preview by mutableStateOf<(@Composable () -> Unit)?>(null)
        private set
    /** The blob is on screen (opening, open or closing). */
    var visible by mutableStateOf(false)
        internal set

    fun show(
        anchor: Rect, items: List<MenuItem>, cover: Boolean = true, faceTint: Color = Color.Unspecified,
        onReact: ((String) -> Unit)? = null, reacted: Set<String> = emptySet(), face: (@Composable () -> Unit)? = null,
        preview: (@Composable () -> Unit)? = null,
    ) {
        this.preview = preview
        this.anchor = anchor
        this.onReact = onReact
        this.reacted = reacted
        this.items = items
        this.cover = cover
        this.face = face
        this.faceTint = faceTint
        open = true
    }

    /** Kept for call sites: Glasmio's menu leaves its source button in place. */
    @Suppress("UNUSED_PARAMETER")
    fun buttonAlpha(bounds: Rect) = 1f

    fun dismiss() { open = false }
}

@Composable
fun rememberGlassMenu() = remember { GlassMenuState() }

/** Reports this element's bounds in window coordinates, for anchoring a menu. */
fun Modifier.menuAnchor(onBounds: (Rect) -> Unit) = onGloballyPositioned { onBounds(it.boundsInRoot()) }

/**
 * The screen's menu, drawn by Glasmio's iOS menu (github.com/NEORUAA/Glasmio, Apache-2.0): its glass material,
 * open/close motion, row highlight and drag-to-select. Message menus get the reaction bar right above it.
 * Must fill the screen, drawn above everything else.
 */
@Composable
fun GlassMenuHost(state: GlassMenuState, backdrop: Backdrop) {
    LaunchedEffect(state.open) { state.visible = state.open }
    var menuBounds by remember { mutableStateOf(Rect.Zero) }
    var menuAlpha by remember { mutableStateOf(0f) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val focus by androidx.compose.animation.core.animateFloatAsState(
        if (state.open && state.preview != null) 1f else 0f,
        androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = 380f), label = "focus",
    )
    val barGap = with(LocalDensity.current) { (REACTION_BAR_DP + 8).dp.toPx() }
    // Message menus: the reaction bar sits right above the message, so the menu anchors to message + bar together
    // (below the message, or above the bar when there is no room below) and never covers the message.
    val menuAnchorRect = if (state.onReact != null) state.anchor.copy(top = state.anchor.top - barGap) else state.anchor
    Box(Modifier.fillMaxSize()) {
        val preview = state.preview
        if (preview != null && focus > 0.001f) {
            // Everything around the message blurs and dims; tapping it closes the menu.
            Box(
                Modifier.fillMaxSize().graphicsLayer { alpha = focus }
                    .drawPlainBackdrop(backdrop, shape = { androidx.compose.ui.graphics.RectangleShape }, effects = { blur(18.dp.toPx()) })
                    .background(Color.Black.copy(alpha = 0.18f))
                    .pointerInput(Unit) { detectTapGestures { state.dismiss() } },
            )
            val d = LocalDensity.current
            Box(
                Modifier.offset { IntOffset(state.anchor.left.roundToInt(), state.anchor.top.roundToInt()) }
                    .size(with(d) { state.anchor.width.toDp() }, with(d) { state.anchor.height.toDp() }),
            ) { preview() }
        }
        if (preview != null || focus > 0.001f) MessageMenu(state, backdrop, barGap)
        if (state.items.isNotEmpty() && state.preview == null) io.github.neoruaa.glasmio.ui.GlasmioMenu(
            expanded = state.open,
            onExpandedChange = { if (!it) state.dismiss() },
            itemCount = state.items.size,
            backdrop = backdrop,
            externalAnchorBounds = menuAnchorRect,
            // Wide enough for the longest Russian items ("Поделиться контактом").
            menuWidth = 270.dp,
            onMenuBoundsChanged = { menuBounds = it },
            onMenuAlphaChanged = { menuAlpha = it },
            onClosed = { menuBounds = Rect.Zero; pending?.invoke(); pending = null },
            anchor = {},
            morphFromAnchor = false,
        ) { menuBackdrop, close ->
            state.items.forEach { item ->
                io.github.neoruaa.glasmio.ui.GlasmioMenuItem(
                    title = Strings.tr(item.label),
                    // Runs once the popup has closed, so it isn't left over the next screen (see MenuButton).
                    onClick = { pending = item.onClick; close(); state.dismiss() },
                    destructive = item.destructive,
                    backdrop = menuBackdrop,
                    icon = { Icon(item.icon, if (item.destructive) Tg.Red else Tg.Text, 21.dp) },
                )
            }
        }
    }
}

/**
 * A button with its menu, Glasmio's complete element (github.com/NEORUAA/Glasmio): the glass button morphs into the
 * menu and back with its native motion. [items] is read when the menu opens.
 */
@Composable
fun MenuButton(
    backdrop: Backdrop,
    items: () -> List<MenuItem>,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 44.dp,
    tint: Color? = null,
    /** Plain (not filled) glass tint, e.g. the dark glass over a profile photo. */
    surface: Color? = null,
    /** What the open menu blurs; defaults to the button's [backdrop]. */
    menuBackdrop: Backdrop = backdrop,
    // 238 dp is Glasmio's own (internal) PopupMenuWidth.
    menuWidth: androidx.compose.ui.unit.Dp = 238.dp,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    // The menu lives in its own popup window, above every screen: an item that opens a screen waits until the menu
    // has folded back into the button, or the button would be drawn on top of the new screen during the transition.
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val list = items()
    io.github.neoruaa.glasmio.ui.GlasmioMenu(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        itemCount = list.size,
        modifier = modifier,
        backdrop = menuBackdrop,
        menuWidth = menuWidth,
        onClosed = { pending?.invoke(); pending = null },
        anchor = { open ->
            GlassBox(
                backdrop, Modifier.size(size), androidx.compose.foundation.shape.CircleShape, tint = tint ?: surface,
                prominent = tint != null, onClick = open, material = null, contentAlignment = Alignment.Center, content = content,
            )
        },
    ) { menuBackdrop, close ->
        list.forEach { item ->
            io.github.neoruaa.glasmio.ui.GlasmioMenuItem(
                title = Strings.tr(item.label),
                onClick = { pending = item.onClick; close() },
                destructive = item.destructive,
                backdrop = menuBackdrop,
                icon = { Icon(item.icon, if (item.destructive) Tg.Red else Tg.Text, 21.dp) },
            )
        }
    }
}

/**
 * Message menu: the message stays where it is (its copy is drawn by [GlassMenuHost]); the reaction bar sits right
 * above it and Glasmio's context-menu panel opens below it, or above the bar when there is no room below. Motion
 * follows Glasmio's message menu: opens on spring(0.8, 385.5) growing from the message's corner, closes on
 * spring(0.85, 322.3).
 */
@Composable
private fun MessageMenu(state: GlassMenuState, backdrop: Backdrop, barGap: Float) {
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(state.open) {
        if (state.open) progress.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 385.5314f))
        else progress.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 322.2728f))
    }
    // Closed and settled: draw nothing, so no invisible rows catch taps.
    if (!state.open && progress.value < 0.01f) return
    val d = LocalDensity.current
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()
        val top = androidx.compose.foundation.layout.WindowInsets.statusBars.getTop(d).toFloat()
        val bottom = androidx.compose.foundation.layout.WindowInsets.navigationBars.getBottom(d).toFloat()
        val margin = with(d) { 10.dp.toPx() }
        val gap = with(d) { 8.dp.toPx() }
        val a = state.anchor
        val right = a.center.x > screenW / 2
        val menuW = with(d) { 238.dp.toPx() }
        val menuH = with(d) { (state.items.size * 44 + 28).dp.toPx() }
        // Bar above the message; if the message is too high for it, under the message instead.
        val barTop = (a.top - barGap).let { if (it < top + margin) a.bottom + gap else it }
        val barBelow = barTop > a.top
        val belowTop = (if (barBelow) barTop + barGap else a.bottom + gap)
        val menuTop = when {
            belowTop + menuH < screenH - bottom - margin -> belowTop
            else -> (minOf(a.top, barTop) - gap - menuH).coerceAtLeast(top + margin)
        }
        val menuLeft = (if (right) a.right - menuW else a.left).coerceIn(margin, screenW - menuW - margin)
        val opensAbove = menuTop < a.top
        val p = progress.value

        state.onReact?.let { react ->
            ReactionBar(
                backdrop, state.reacted, expandedUp = false,
                modifier = Modifier
                    .offset { IntOffset(0, barTop.roundToInt()) }
                    .padding(horizontal = 10.dp)
                    .fillMaxWidth()
                    .wrapContentSize(if (right) Alignment.TopEnd else Alignment.TopStart)
                    .graphicsLayer {
                        alpha = p.coerceIn(0f, 1f)
                        val sc = 0.6f + 0.4f * p
                        scaleX = sc; scaleY = sc
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (right) 1f else 0f, if (barBelow) 0f else 1f)
                    },
            ) { state.dismiss(); react(it) }
        }

        Box(
            Modifier.offset { IntOffset(menuLeft.roundToInt(), menuTop.roundToInt()) }
                .graphicsLayer {
                    alpha = p.coerceIn(0f, 1f)
                    val sc = 0.2f + 0.8f * p
                    scaleX = sc; scaleY = sc
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (right) 1f else 0f, if (opensAbove) 1f else 0f)
                },
        ) {
            io.github.neoruaa.glasmio.ui.GlasmioContextMenu(
                visible = true,
                backdrop = backdrop,
                compact = true,
                itemCount = state.items.size,
                menuWidth = 238.dp,
                heightOverride = with(d) { menuH.toDp() },
                exportChildBackdrop = false,
                opensAbove = opensAbove,
            ) { child ->
                androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                    state.items.forEach { item ->
                        io.github.neoruaa.glasmio.ui.GlasmioMenuItem(
                            title = Strings.tr(item.label),
                            onClick = { state.dismiss(); item.onClick() },
                            destructive = item.destructive,
                            backdrop = child,
                            icon = { Icon(item.icon, if (item.destructive) Tg.Red else Tg.Text, 21.dp) },
                        )
                    }
                }
            }
        }
    }
}
