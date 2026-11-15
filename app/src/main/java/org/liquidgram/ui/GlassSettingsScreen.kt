package org.liquidgram.ui

import io.github.neoruaa.glasmio.glass.trackBackdropPosition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.neoruaa.glasmio.glass.render.backdrops.LayerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.layerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberLayerBackdrop
import org.liquidgram.glass.LiquidSlider
import org.liquidgram.glass.LiquidToggle
import kotlin.math.roundToInt

/** Customisation of the whole client: theme, colours, chat look, and the Liquid Glass parameters. */
@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    val pageBackdrop = rememberLayerBackdrop()
    // The header floats above the scrolling page and refracts the real content; the controls inside the page
    // can't use that (they'd sample themselves), so they keep the plain card-coloured [pageBackdrop].
    val contentBackdrop = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize().background(Tg.Grouped)) {
        // Controls sit inside the scrolling page, so they can't sample the page itself (they'd see their own
        // drawing). They sample this card-coloured layer underneath instead.
        Box(Modifier.fillMaxSize().layerBackdrop(pageBackdrop).trackBackdropPosition(pageBackdrop).background(Tg.Card))
        Column(
            Modifier.fillMaxSize().layerBackdrop(contentBackdrop).trackBackdropPosition(contentBackdrop).background(Tg.Grouped).verticalScroll(rememberScrollState())
                .statusBarsPadding().padding(top = 60.dp).navigationBarsPadding().padding(bottom = 24.dp)
        ) {
            Preview()

            Section("Theme") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        T(if (Tg.dark) "Dark" else "Light", 17.sp, Tg.Text)
                        T("Tap to switch", 13.sp, Tg.Secondary)
                    }
                    ThemeButton(pageBackdrop, 48.dp)
                }
                Divider()
                ToggleRow("Follow device theme", { Look.followSystem }, { Look.followSystem = it }, pageBackdrop)
            }

            Section("Accent colour") {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Look.accentColors.forEachIndexed { i, color ->
                        val selected = Look.accent == i
                        val s by animateFloatAsState(if (selected) 1.15f else 1f, spring(0.45f, 400f), label = "accent")
                        Box(
                            Modifier.size(34.dp).graphicsLayer { scaleX = s; scaleY = s }.clip(CircleShape).background(color)
                                .clickable { Look.accent = i },
                            contentAlignment = Alignment.Center,
                        ) { if (selected) Icon(Icons.Rounded.Check, Color.White, 20.dp) }
                    }
                }
            }

            Section("Chats") {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Look.wallpapers.forEachIndexed { i, wp ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // The real wallpaper in miniature, so doodles/accent/theme show exactly as in a chat.
                            Box(
                                Modifier.size(72.dp, 110.dp).clip(RoundedCornerShape(14.dp))
                                    .border(if (Look.wallpaper == i) 3.dp else 0.5.dp, if (Look.wallpaper == i) Tg.Accent else Tg.Separator, RoundedCornerShape(14.dp))
                                    .clickable { Look.wallpaper = i },
                            ) { Wallpaper(Modifier.fillMaxSize(), index = i) }
                            Spacer(Modifier.height(6.dp))
                            T(wp.name, 13.sp, if (Look.wallpaper == i) Tg.Accent else Tg.Secondary)
                        }
                    }
                }
                Divider()
                SliderRow("Text size", { Look.textScale }, { Look.textScale = it }, 0.85f..1.3f, pageBackdrop) { "${(it * 100).roundToInt()}%" }
                Divider()
                SliderRow("Bubble corners", { Look.bubbleRadius }, { Look.bubbleRadius = it }, 4f..24f, pageBackdrop) { "${it.roundToInt()} dp" }
                Divider()
                SliderRow("Own message tint", { Look.ownTint }, { Look.ownTint = it }, 0f..0.6f, pageBackdrop) { "${(it * 100).roundToInt()}%" }
                Divider()
                SliderRow("Pattern intensity", { Look.patternAlpha }, { Look.patternAlpha = it }, 0f..2f, pageBackdrop) { "${(it * 100).roundToInt()}%" }
                Divider()
                SliderRow("Pattern size", { Look.patternSize }, { Look.patternSize = it }, 0.6f..1.6f, pageBackdrop) { "${(it * 100).roundToInt()}%" }
            }

            Section("Interface") {
                SliderRow("Avatar roundness", { Look.avatarRound }, { Look.avatarRound = it }, 0f..1f, pageBackdrop) { "${(it * 100).roundToInt()}%" }

                Divider()
                ToggleRow("Show stories", { Look.showStories }, { Look.showStories = it }, pageBackdrop)
                Divider()
                ToggleRow("Compact chat list", { Look.compactList }, { Look.compactList = it }, pageBackdrop)
                Divider()
                ToggleRow("Everything in theme colour", { Look.mono }, { Look.mono = it }, pageBackdrop)
                Divider()
                EmojiFontRow()
            }

            // Glasmio's GlassAppearance switches (github.com/NEORUAA/Glasmio).
            Section("Liquid Glass") {
                SliderRow("Glass opacity", { Glass.opacity }, { Glass.opacity = it }, 0f..2.5f, pageBackdrop) { "${(it / Glass.DEFAULT_OPACITY * 100).roundToInt()}%" }
                Divider()
                SliderRow("Glass blur", { Glass.blur }, { Glass.blur = it }, 0f..20f, pageBackdrop) { "${it.roundToInt()} dp" }
                Divider()
                ToggleRow("Liquid refraction", { Glass.liquid }, { Glass.liquid = it }, pageBackdrop)
                Divider()
                ToggleRow("Reduce transparency", { Glass.reduceTransparency }, { Glass.reduceTransparency = it }, pageBackdrop)
            }
            Note("Blur and opacity change all the glass: buttons, panels, the tab bar, menus and switches.")

            Section(null) {
                Row(Modifier.fillMaxWidth().clickable { Look.resetAll() }.padding(16.dp), horizontalArrangement = Arrangement.Center) {
                    T("Reset to Defaults", 17.sp, Tg.Red)
                }
            }
        }

        Box(Modifier.fillMaxWidth().scrollEdge(contentBackdrop).statusBarsPadding().height(60.dp).padding(horizontal = EdgeGap)) {
            GlassCircle(contentBackdrop, onBack, modifier = Modifier.align(Alignment.CenterStart)) { Icon(Icons.Rounded.ArrowBackIosNew, Tg.Text, 20.dp) }
            T("Appearance", 17.sp, Tg.Text, FontWeight.SemiBold, Modifier.align(Alignment.Center))
        }
    }
}

/** A tiny chat on the current wallpaper with real glass controls on top: every change shows up here at once. */
@Composable
private fun Preview() {
    val backdrop = rememberLayerBackdrop()
    // Bubbles sample the wallpaper alone (glass can't sample itself); the controls sample wallpaper + bubbles.
    val wallBackdrop = rememberLayerBackdrop()
    var tab by remember { mutableIntStateOf(1) }
    Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(280.dp).clip(RoundedCornerShape(26.dp))) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop).trackBackdropPosition(backdrop)) {
            Box(Modifier.fillMaxSize().layerBackdrop(wallBackdrop).trackBackdropPosition(wallBackdrop)) { Wallpaper() }
            Column(Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 70.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PreviewBubble(wallBackdrop, "Have you tried the new glass? ✨", out = false)
                PreviewBubble(wallBackdrop, "It refracts everything behind it 😍", out = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { repeat(5) { Avatar("Liquid Glass", it, 36.dp) } }
            }
        }
        Row(Modifier.align(Alignment.TopEnd).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassPill(backdrop, {}) { T("Edit", 17.sp, Tg.Text, FontWeight.Medium) }
            GlassCircle(backdrop, {}) { Icon(Icons.Rounded.Star, Tg.Text, 22.dp) }
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(12.dp)) {
            val items = remember(Look.language) {
                listOf(
                    io.github.neoruaa.glasmio.ui.GlasmioTabItem(0, Strings.tr("Calls"), io.github.neoruaa.glasmio.icons.SfSymbol.ClockFilled),
                    io.github.neoruaa.glasmio.ui.GlasmioTabItem(1, Strings.tr("Chats"), io.github.neoruaa.glasmio.icons.SfSymbol.HouseFilled),
                    io.github.neoruaa.glasmio.ui.GlasmioTabItem(2, Strings.tr("Settings"), io.github.neoruaa.glasmio.icons.SfSymbol.Gearshape),
                )
            }
            io.github.neoruaa.glasmio.ui.GlasmioNavigationBar(
                items = items, selectedKey = tab, onSelected = { tab = it }, onExpand = {},
                compactProgress = remember { androidx.compose.runtime.mutableStateOf(0f) }, compactSize = io.github.neoruaa.glasmio.ui.CompactBottomControlSize,
                backdrop = backdrop,
            )
        }
    }
}

@Composable
private fun PreviewBubble(backdrop: io.github.neoruaa.glasmio.glass.render.Backdrop, text: String, out: Boolean) {
    val r = Look.bubbleRadius.dp
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (out) Arrangement.End else Arrangement.Start) {
        GlassBox(backdrop, Modifier.widthIn(max = 240.dp), RoundedCornerShape(r), tint = if (out) Tg.Accent.copy(alpha = Look.ownTint) else null) {
            T(text, 15.sp, Tg.Text, maxLines = 2, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
        }
    }
}

@Composable
private fun Section(title: String?, content: @Composable () -> Unit) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 22.dp)) {
        if (title != null) T(Strings.tr(title).uppercase(), 13.sp, Tg.Secondary, modifier = Modifier.padding(start = 16.dp, bottom = 6.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Tg.Card)) { content() }
    }
}

@Composable
private fun SliderRow(
    label: String, value: () -> Float, onChange: (Float) -> Unit, range: ClosedFloatingPointRange<Float>,
    backdrop: LayerBackdrop, format: (Float) -> String,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth()) {
            T(label, 17.sp, Tg.Text, modifier = Modifier.weight(1f))
            T(format(value()), 15.sp, Tg.Secondary)
        }
        LiquidSlider(value, onChange, range, (range.endInclusive - range.start) / 200f, backdrop, Modifier.fillMaxWidth())
    }
}

@Composable
private fun ToggleRow(label: String, value: () -> Boolean, onChange: (Boolean) -> Unit, backdrop: LayerBackdrop) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        T(label, 17.sp, Tg.Text, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        LiquidToggle(value, onChange, backdrop)
    }
}

@Composable
private fun Divider() = Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(0.5.dp).background(Tg.Separator))

/** Emoji font: the system one, or a .ttf the user picks (copied into app storage, used for every text). */
@Composable
private fun EmojiFontRow() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val dir = java.io.File(context.filesDir, "fonts").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = java.io.File(dir, "emoji_${System.currentTimeMillis()}.ttf")
        runCatching { context.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } } }
        Look.emojiFont = out.absolutePath.takeIf { out.length() > 0 }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            T("Emoji font", 17.sp, Tg.Text)
            T(if (Look.emojiFont == null) "System" else "Custom font", 13.sp, Tg.Secondary)
        }
        if (Look.emojiFont != null) T("System", 15.sp, Tg.Accent, modifier = Modifier.clickable { Look.emojiFont = null }.padding(8.dp))
        T("Choose file…", 15.sp, Tg.Accent, modifier = Modifier.clickable { pick.launch(arrayOf("font/*", "application/x-font-ttf", "application/octet-stream", "*/*")) }.padding(8.dp))
    }
}
