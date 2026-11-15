package org.liquidgram.ui

import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.border
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.composed
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.produceState
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.unit.lerp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.glass.progressiveBlurBackdrop
import io.github.neoruaa.glasmio.glass.render.drawBackdrop
import io.github.neoruaa.glasmio.glass.render.drawPlainBackdrop
import io.github.neoruaa.glasmio.glass.render.effects.blur
import io.github.neoruaa.glasmio.glass.render.effects.lens
import io.github.neoruaa.glasmio.glass.render.effects.vibrancy
import com.kyant.shapes.RoundedRectangle
import org.liquidgram.glass.LiquidButton

@Composable
fun T(
    text: String,
    size: TextUnit = 17.sp,
    color: Color = Tg.Text,
    weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    align: androidx.compose.ui.text.style.TextAlign = androidx.compose.ui.text.style.TextAlign.Unspecified,
) = BasicText(
    Strings.tr(text),
    // "Everything in theme colour": emoji anywhere (names, previews, buttons) get the accent duotone too.
    if (Look.mono && hasEmoji(text)) modifier.themeTint() else modifier,
    style = TextStyle(color = color, fontSize = size * Look.textScale, fontWeight = weight, letterSpacing = (-0.2).sp, fontFamily = appFont(), textAlign = align),
    maxLines = maxLines, overflow = TextOverflow.Ellipsis,
)

@Composable
fun Icon(v: ImageVector, tint: Color = Tg.Text, size: Dp = 24.dp, modifier: Modifier = Modifier) =
    Image(v, null, modifier.size(size), colorFilter = ColorFilter.tint(tint))

fun initials(name: String) = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase()

fun avatarBrush(colorIndex: Int): Brush {
    val (top, bottom) = Tg.avatarColors[colorIndex % Tg.avatarColors.size]
    return Brush.verticalGradient(listOf(top, bottom))
}

@Composable
fun Avatar(name: String, colorIndex: Int, size: Dp, saved: Boolean = false, modifier: Modifier = Modifier, photoPath: String? = null) {
    val photo = rememberPhoto(photoPath)
    val shape = avatarShape()
    Box(
        modifier.size(size)
            .clip(shape).background(
            when {
                Look.mono -> Brush.verticalGradient(listOf(Tg.Accent.copy(alpha = 0.75f), Tg.Accent))
                saved -> avatarBrush(5)
                else -> avatarBrush(colorIndex)
            }
        ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            saved -> Icon(Icons.Rounded.Bookmark, Color.White, size * 0.5f)
            photo != null -> FadeImage(photo, Modifier.fillMaxSize().themeTint())
            else -> T(initials(name), (size.value * 0.38f).sp, Color.White, FontWeight.SemiBold)
        }
        // The glass rim from Glasmio's material (its Highlight), drawn alone: the avatar keeps its own picture.
        Box(
            Modifier.matchParentSize().drawBackdrop(
                backdrop = io.github.neoruaa.glasmio.glass.render.backdrops.emptyBackdrop(),
                shape = { shape },
                effects = {},
                // Glasmio's rim (same SDF falloff); additive so it draws on its own below Android 16, where the plain
                // white variant is left to the glass material's shader and an avatar has none.
                highlight = { AvatarRim },
                shadow = null,
            )
        )
    }
}

private val AvatarRim = io.github.neoruaa.glasmio.glass.render.highlight.Highlight(
    width = 1.5.dp,
    style = io.github.neoruaa.glasmio.glass.render.highlight.HighlightStyle.Default(
        color = Color.White.copy(alpha = 0.75f), blendMode = androidx.compose.ui.graphics.BlendMode.Plus,
    ),
)

/** Rough emoji test: surrogate pairs (most emoji) or the symbol blocks (☀️ ❤️ ✨…). */
private fun hasEmoji(text: String) = text.any { Character.isSurrogate(it) || it.code in 0x2190..0x2BFF }

/** Avatar outline from the "Avatar roundness" setting; story rings use it too. */
fun avatarShape() = RoundedCornerShape(percent = (50 * Look.avatarRound).toInt())

/** Decodes a local image file off the main thread; null until ready or if there is none. */
@Composable
fun rememberPhoto(path: String?): ImageBitmap? = produceState<ImageBitmap?>(null, path) {
    value = path?.let { withContext(Dispatchers.IO) { BitmapFactory.decodeFile(it)?.asImageBitmap() } }
}.value

/** A decoded photo that fades in (iOS-style) instead of popping in once it is ready; nothing while [bitmap] is null. */
@Composable
fun FadeImage(bitmap: ImageBitmap?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    if (bitmap == null) return
    val shown = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) { shown.animateTo(1f, androidx.compose.animation.core.tween(200)) }
    Image(bitmap, null, modifier.graphicsLayer { alpha = shown.value }, contentScale = contentScale)
}

@Composable
fun Badge(count: Int, color: Color = Tg.Badge, modifier: Modifier = Modifier) {
    Box(
        modifier.height(20.dp).background(color, CircleShape).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) { T(count.toString(), 14.sp, Color.White, FontWeight.Medium) }
}

/** Round iOS 26 glass button. */
@Composable
fun GlassCircle(backdrop: Backdrop, onClick: () -> Unit, size: Dp = 44.dp, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) =
    GlassBox(backdrop, modifier.size(size), CircleShape, onClick = onClick, material = null, contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, content = content)
    }

/** Capsule glass button ("Edit"). */
@Composable
fun GlassPill(backdrop: Backdrop, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) =
    LiquidButton(onClick, backdrop, modifier, height = 44.dp, horizontalPadding = 16.dp, content = content)

/** Feedback for buttons whose feature isn't built yet. */
@Composable
fun rememberSoon(): (String) -> Unit {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember(context) { { what: String -> android.widget.Toast.makeText(context, Strings.tr(what) + if (Look.language == 1) ": скоро" else ": coming soon", android.widget.Toast.LENGTH_SHORT).show() } }
}


/** Small iOS-style busy indicator: a rotating arc. */
@Composable
fun Spinner(color: Color) {
    val turn by androidx.compose.animation.core.rememberInfiniteTransition(label = "spin").animateFloat(
        0f, 360f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(800, easing = androidx.compose.animation.core.LinearEasing)),
        label = "turn",
    )
    androidx.compose.foundation.Canvas(Modifier.size(22.dp).graphicsLayer { rotationZ = turn }) {
        drawArc(color, 0f, 270f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(2.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
    }
}

/**
 * "Everything in the theme colour": repaints emoji and stickers as a duotone of the accent (dark accent → white),
 * keeping their shading. Needs Android 12+ (RenderEffect); older devices show the originals.
 */
fun Modifier.themeTint(): Modifier = if (!Look.mono || android.os.Build.VERSION.SDK_INT < 31) this else graphicsLayer {
    val a = Tg.Accent
    val dark = floatArrayOf(a.red * 0.3f, a.green * 0.3f, a.blue * 0.3f)
    val m = FloatArray(20)
    for (row in 0..2) {
        val span = 1f - dark[row]
        m[row * 5] = span * 0.299f; m[row * 5 + 1] = span * 0.587f; m[row * 5 + 2] = span * 0.114f
        m[row * 5 + 4] = dark[row] * 255f
    }
    m[18] = 1f
    renderEffect = android.graphics.RenderEffect.createColorFilterEffect(android.graphics.ColorMatrixColorFilter(m)).asComposeRenderEffect()
}

private var fontCache: Pair<String, androidx.compose.ui.text.font.FontFamily?>? = null

/**
 * Text font with the user's emoji font (Appearance → Emoji) slotted in: system Roboto for letters, then the
 * emoji file, then the usual system fallbacks. Null (= default font) when none is chosen or below Android 10.
 */
fun appFont(): androidx.compose.ui.text.font.FontFamily? {
    val path = Look.emojiFont ?: return null
    if (android.os.Build.VERSION.SDK_INT < 29) return null
    fontCache?.let { if (it.first == path) return it.second }
    val family = runCatching {
        fun fam(file: String) = android.graphics.fonts.FontFamily.Builder(android.graphics.fonts.Font.Builder(java.io.File(file)).build()).build()
        val roboto = "/system/fonts/Roboto-Regular.ttf".takeIf { java.io.File(it).exists() }
        val builder = if (roboto != null) android.graphics.Typeface.CustomFallbackBuilder(fam(roboto)).addCustomFallback(fam(path))
        else android.graphics.Typeface.CustomFallbackBuilder(fam(path))
        androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Typeface(builder.setSystemFallback("sans-serif").build()))
    }.getOrNull()
    fontCache = path to family
    return family
}

/**
 * Scroll edge behind a floating bar: Glasmio's progressive backdrop blur (github.com/NEORUAA/Glasmio), reaching
 * [reach] past the bar: down from a header, up from a bottom bar.
 */
fun Modifier.scrollEdge(backdrop: Backdrop, top: Boolean = true, reach: Dp = 36.dp) = this
    .layout { m, c ->
        val r = reach.roundToPx()
        val p = m.measure(c.copy(minHeight = c.minHeight + r, maxHeight = if (c.hasBoundedHeight) c.maxHeight + r else c.maxHeight))
        layout(p.width, p.height - r) { p.place(0, if (top) 0 else -r) }
    }
    .progressiveBlurBackdrop(backdrop, progress = 1f, fromBottom = !top)
    .padding(if (top) PaddingValues(bottom = reach) else PaddingValues(top = reach))

/** Distance of every floating glass element from the screen edges (sides, and bottom for bottom bars). */
val EdgeGap = 26.dp

/**
 * Floating bar at the bottom: [gap] from the screen's bottom edge, the same as from its sides (Apple Music tab bar).
 * The gesture strip may overlap the gap as the iOS home indicator does; 3-button navigation is kept clear.
 */
fun Modifier.bottomGap(gap: Dp) = composed { windowInsetsPadding(
    androidx.compose.foundation.layout.WindowInsets.tappableElement.only(androidx.compose.foundation.layout.WindowInsetsSides.Bottom)
        .union(androidx.compose.foundation.layout.WindowInsets(bottom = gap))
) }

/** Telegram icons for the SF symbol names the Glasmio tab bar asks for; everything else uses Glasmio's Material fallback. */
object TelegramIcons : io.github.neoruaa.glasmio.icons.GlasmioIconResolver {
    override fun resolve(systemName: String): androidx.compose.ui.graphics.vector.ImageVector? = when (systemName) {
        "person.fill" -> androidx.compose.material.icons.Icons.Rounded.AccountCircle
        "clock.fill" -> androidx.compose.material.icons.Icons.Rounded.Call
        "house.fill" -> androidx.compose.material.icons.Icons.Rounded.ChatBubble
        "gearshape" -> androidx.compose.material.icons.Icons.Rounded.Settings
        "face.smiling" -> androidx.compose.material.icons.Icons.Outlined.EmojiEmotions
        else -> io.github.neoruaa.glasmio.icons.MaterialGlasmioIconResolver.resolve(systemName)
    }
}

/**
 * Every glass surface in the app: Glasmio's iOS navigation material (github.com/NEORUAA/Glasmio, Apache-2.0) with its
 * own highlight, shadow and, when [onClick] is set, its press lift, drag and glow. [tint] tints the glass; [prominent]
 * fills it with the tint instead (filled buttons).
 */
@Composable
fun GlassBox(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = com.kyant.shapes.RoundedRectangle(22.dp),
    tint: androidx.compose.ui.graphics.Color? = null,
    prominent: Boolean = false,
    // Glasmio only gives clickable glass its press physics, so every surface is clickable (a no-op by default):
    // fields, labels and bubbles stretch under the finger too.
    onClick: (() -> Unit)? = {},
    onLongClick: (() -> Unit)? = null,
    // null = Glasmio's button material (what its icon buttons and capsules use).
    material: io.github.neoruaa.glasmio.glass.NavigationGlassMaterial? = io.github.neoruaa.glasmio.glass.NavigationGlassMaterial.RegularSmall,
    contentAlignment: androidx.compose.ui.Alignment = androidx.compose.ui.Alignment.TopStart,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) = io.github.neoruaa.glasmio.glass.GlassSurface(
    modifier = modifier,
    backdrop = backdrop,
    shape = shape,
    navigationSurfaceColor = tint,
    emphasis = if (prominent) io.github.neoruaa.glasmio.glass.GlassEmphasis.Prominent else io.github.neoruaa.glasmio.glass.GlassEmphasis.Regular,
    onClick = onClick,
    onLongClick = onLongClick,
    contentAlignment = contentAlignment,
    material = material,
    content = content,
)
