package org.liquidgram.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Icecream
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalFlorist
import androidx.compose.material.icons.outlined.LocalPizza
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Touches on the chat that part the doodles: figures near the finger slowly move aside, follow it while it moves,
 * and drift back after release. The chat feeds [press]/[move]/[release]; the wallpaper reads the pushes while drawing.
 */
class WallpaperTouches(private val scope: CoroutineScope) {
    class Push(at: Offset) {
        // The gap glides after the finger on the same slow spring as the first press, never jumping with it.
        val position = Animatable(at, Offset.VectorConverter)
        val at get() = position.value
        val strength = Animatable(0f)
    }
    val pushes = mutableStateListOf<Push>()

    fun press(at: Offset): Push {
        val push = Push(at)
        pushes += push
        if (pushes.size > 4) pushes.removeAt(0)
        scope.launch { push.strength.animateTo(1f, spring(dampingRatio = 1f, stiffness = 60f)) }
        return push
    }

    fun move(push: Push, at: Offset) {
        scope.launch { push.position.animateTo(at, spring(dampingRatio = 1f, stiffness = 60f)) }
    }

    fun release(push: Push) {
        scope.launch {
            push.strength.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 40f))
            pushes -= push
        }
    }
}

/** One doodle: where it sits, how big, how tilted, which icon. */
private class Doodle(val x: Float, val y: Float, val scale: Float, val angle: Float, val icon: Int)

/** Chat background: the user's choice (or [index]), redrawn whenever theme or accent changes. */
@Composable
fun Wallpaper(modifier: Modifier = Modifier.fillMaxSize(), index: Int = Look.wallpaper, touches: WallpaperTouches? = null) {
    val wp = Look.wallpapers[index.coerceIn(0, Look.wallpapers.lastIndex)]
    val dark = Tg.dark
    val accent = Tg.Accent
    val density = LocalDensity.current
    val doodles = doodleIcons().map { rememberVectorPainter(it) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val base = with(density) { 30.dp.toPx() } * Look.patternSize
    // Scatter is computed once per size: random, dense, non-overlapping (dart throwing on a grid).
    val layout = remember(size, base) { scatter(size, base, doodles.size) }
    val pushReach = with(density) { 90.dp.toPx() }
    val pushDistance = with(density) { 9.dp.toPx() }
    val alpha = (if (dark) 0.42f else 0.34f) * Look.patternAlpha
    // The still pattern, drawn once into a bitmap: redrawing hundreds of vector icons every frame of a scroll
    // (each scroll starts with a touch that animates the pattern) was what made the chat lag.
    val cache = remember(layout, accent, alpha) { arrayOfNulls<ImageBitmap>(1) }

    Canvas(modifier.onSizeChanged { size = it }) {
        // Pure black / pure white; only the figures carry colour.
        drawRect(if (dark) Color.Black else Color.White)
        if (wp.plain || size.width <= 0 || size.height <= 0) return@Canvas
        val tint = ColorFilter.tint(accent)
        val still = cache[0] ?: ImageBitmap(size.width, size.height).also { bmp ->
            CanvasDrawScope().draw(this, layoutDirection, androidx.compose.ui.graphics.Canvas(bmp), Size(size.width.toFloat(), size.height.toFloat())) {
                layout.forEach { d -> drawIcon(doodles[d.icon], d.x, d.y, base, d.scale, d.angle, tint, alpha) }
            }
            cache[0] = bmp
        }
        val pushes = touches?.pushes.orEmpty().filter { it.strength.value != 0f }
        if (pushes.isEmpty()) { drawImage(still); return@Canvas }
        // Under a finger: the bitmap everywhere except circles around the touches, and inside them only the nearby
        // figures, pushed aside. At the circle edge the push is ~0, so the seam doesn't show.
        val radius = pushReach * 2.5f
        val holes = Path().apply { pushes.forEach { addOval(Rect(it.at, radius)) } }
        clipPath(holes, ClipOp.Difference) { drawImage(still) }
        clipPath(holes) {
            layout.forEach { d ->
                if (pushes.none { p -> (d.x - p.at.x).let { it * it } + (d.y - p.at.y).let { it * it } < (radius + base * 1.5f).let { it * it } }) return@forEach
                var x = d.x
                var y = d.y
                pushes.forEach { p ->
                    val s = p.strength.value
                    val dx = d.x - p.at.x
                    val dy = d.y - p.at.y
                    val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                    val k = s * pushDistance * exp(-(dist / pushReach) * (dist / pushReach))
                    x += dx / dist * k
                    y += dy / dist * k
                }
                drawIcon(doodles[d.icon], x, y, base, d.scale, d.angle, tint, alpha)
            }
        }
    }
}

private fun doodleIcons() = listOf(
    Icons.Outlined.LocalCafe, Icons.Outlined.Cake, Icons.Outlined.Icecream, Icons.Outlined.EmojiEmotions,
    Icons.Outlined.Celebration, Icons.Outlined.LocalFlorist, Icons.Outlined.Pets, Icons.Outlined.MusicNote,
    Icons.Outlined.StarBorder, Icons.Outlined.FavoriteBorder, Icons.Outlined.Cloud, Icons.Outlined.NightsStay,
    Icons.Outlined.WbSunny, Icons.Outlined.Headphones, Icons.Outlined.CameraAlt, Icons.Outlined.Flight,
    Icons.Outlined.SportsEsports, Icons.Outlined.Lightbulb, Icons.Outlined.RocketLaunch, Icons.Outlined.LocalPizza,
)

/** Icon centred at (cx, cy). Always rasterised at [base] and scaled, so the vector cache is reused every frame. */
private fun DrawScope.drawIcon(p: VectorPainter, cx: Float, cy: Float, base: Float, scale: Float, angle: Float, tint: ColorFilter, alpha: Float) {
    translate(cx - base / 2, cy - base / 2) {
        rotate(angle, Offset(base / 2, base / 2)) {
            scale(scale, Offset(base / 2, base / 2)) {
                with(p) { draw(Size(base, base), alpha = alpha, colorFilter = tint) }
            }
        }
    }
}

/**
 * Random but even scatter: candidates are thrown at random and kept only if they don't crowd an existing
 * figure (distance tied to both sizes). A coarse grid makes the neighbour check cheap.
 */
private fun scatter(size: IntSize, base: Float, icons: Int): List<Doodle> {
    if (size.width <= 0 || size.height <= 0) return emptyList()
    val rnd = Random(20261111)
    val cell = base
    val cols = (size.width / cell).toInt() + 3
    val rows = (size.height / cell).toInt() + 3
    val grid = Array(cols * rows) { mutableListOf<Doodle>() }
    val out = ArrayList<Doodle>()
    val attempts = (size.width.toFloat() * size.height / (base * base) * 12).toInt()
    repeat(attempts) {
        val s = 0.75f + rnd.nextFloat() * 0.55f
        val x = -base / 2 + rnd.nextFloat() * (size.width + base)
        val y = -base / 2 + rnd.nextFloat() * (size.height + base)
        val gx = ((x + base) / cell).toInt().coerceIn(0, cols - 1)
        val gy = ((y + base) / cell).toInt().coerceIn(0, rows - 1)
        var ok = true
        loop@ for (ix in (gx - 2)..(gx + 2)) for (iy in (gy - 2)..(gy + 2)) {
            if (ix !in 0 until cols || iy !in 0 until rows) continue
            for (o in grid[iy * cols + ix]) {
                val minD = base * (s + o.scale) * 0.5f * 1.05f
                val dx = o.x - x; val dy = o.y - y
                if (dx * dx + dy * dy < minD * minD) { ok = false; break@loop }
            }
        }
        if (ok) {
            val d = Doodle(x, y, s, rnd.nextFloat() * 80f - 40f, rnd.nextInt(icons))
            out += d
            grid[gy * cols + gx] += d
        }
    }
    return out
}
