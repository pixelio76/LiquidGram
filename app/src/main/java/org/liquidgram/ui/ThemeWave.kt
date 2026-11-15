package org.liquidgram.ui

import android.graphics.BitmapShader
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.glass.render.Backdrop
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.max

/** Switches the theme with a wave starting at the given point (window coordinates). */
val LocalThemeSwitcher = staticCompositionLocalOf<(Offset, Boolean) -> Unit> { { _, dark -> Look.followSystem = false; Look.darkChoice = dark } }

// A refracting ring: inside it the new theme, outside the old one. On the ring both sides bend like a glass
// lens (displacement follows the slope of the wave profile), RGB split apart (chromatic aberration), and a
// light rim glows at the crest.
private const val WAVE_SHADER = """
uniform shader content;
uniform shader old;
uniform float2 center;
uniform float radius;
uniform float band;
uniform float chroma;
// 0: inside the ring is the live screen, outside the snapshot. 1: the other way round (wave running back).
uniform float invert;
uniform float2 size;

half4 main(float2 p) {
    float2 d = p - center;
    float dist = length(d);
    float2 dir = dist > 0.001 ? d / dist : float2(0.0, 0.0);
    float x = (dist - radius) / band;
    float t = clamp(1.0 - abs(x), 0.0, 1.0);
    // While the ring is smaller than its own width, the lens would fold around the centre (and the direction
    // flips there), so the whole effect grows in with the radius.
    float strength = smoothstep(0.0, band * 1.2, radius);
    float bump = t * t * (3.0 - 2.0 * t) * strength;
    float slope = (x < 0.0 ? 1.0 : -1.0) * 6.0 * t * (1.0 - t) * strength;
    float2 off = dir * slope * band * 0.22;
    float2 ca = dir * bump * chroma;
    // Never sample outside the screen: off-screen reads come back as garbage colours.
    float2 lo = float2(0.5, 0.5);
    float2 hi = size - float2(0.5, 0.5);
    float2 pR = clamp(p + off + ca, lo, hi);
    float2 pG = clamp(p + off * 1.08, lo, hi);
    float2 pB = clamp(p + off * 1.16 - ca, lo, hi);

    float4 nR = float4(content.eval(pR));
    float4 nG = float4(content.eval(pG));
    float4 nB = float4(content.eval(pB));
    float4 oR = float4(old.eval(pR));
    float4 oG = float4(old.eval(pG));
    float4 oB = float4(old.eval(pB));

    float4 n = float4(nR.r, nG.g, nB.b, 1.0);
    float4 o = float4(oR.r, oG.g, oB.b, 1.0);
    float4 inside = invert > 0.5 ? o : n;
    float4 outside = invert > 0.5 ? n : o;
    float m = smoothstep(-0.1, 0.1, x);
    float4 c = mix(inside, outside, m);
    c.rgb += bump * 0.08 + pow(bump, 8.0) * 0.22;
    return half4(c);
}
"""

/**
 * Wraps the whole app. The content is recorded into [content] every frame and drawn through [WAVE_SHADER] while
 * a switch runs. Each switch snapshots the live screen, flips the theme and swaps which side of the ring is live,
 * then moves the ring outwards (towards the new theme) or back towards the button. So tapping again mid-wave
 * sends the same ring back from wherever it is, and tapping once more sends it out again, without any jump.
 * Needs Android 13 (RuntimeShader); older devices switch instantly.
 */
@Composable
fun ThemeWaveHost(content: @Composable () -> Unit) {
    val contentLayer = rememberGraphicsLayer()
    val effectLayer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val radius = remember { Animatable(0f) }
    var wave by remember { mutableStateOf<WaveState?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    val density = LocalDensity.current

    val switcher: (Offset, Boolean) -> Unit = remember(contentLayer, density) {
        { center, dark ->
            if (Build.VERSION.SDK_INT < 33) {
                Look.followSystem = false; Look.darkChoice = dark
            } else {
                val previous = job
                job = scope.launch {
                    // Snapshot first, while any running wave keeps moving, so the turn-around doesn't stall a frame.
                    // The content layer has no effect applied, so this is exactly what the screen shows as "live".
                    val snapshot = contentLayer.toImageBitmap().asAndroidBitmap()
                    // Animatable zeroes its velocity when cancelled, so grab it first and hand it to the new
                    // animation: the ring then carries on, turns around smoothly and rolls back.
                    val carried = radius.velocity
                    previous?.cancelAndJoin()
                    val w = contentLayer.size.width.toFloat()
                    val h = contentLayer.size.height.toFloat()
                    val running = wave
                    val c = running?.center ?: center
                    val far = maxOf(hypot(c.x, c.y), hypot(w - c.x, c.y), hypot(c.x, h - c.y), hypot(w - c.x, h - c.y))
                    val band = with(density) { 110.dp.toPx() }
                    val end = max(far, 1f) + band
                    // A fresh switch grows the ring; a switch during a running wave flips its direction.
                    val outwards = running == null || running.invert
                    if (running == null) radius.snapTo(0f)
                    wave = WaveState(snapshot, c, band, with(density) { 7.dp.toPx() }, invert = !outwards, size = Offset(w, h))
                    Look.followSystem = false
                    Look.darkChoice = dark
                    val target = if (outwards) end else 0f
                    if (running == null) {
                        // Fresh switch: the original timing.
                        radius.animateTo(target, tween(1100, easing = CubicBezierEasing(0.33f, 0f, 0.2f, 1f)))
                    } else {
                        // Turn-around: a critically damped spring starting at the ring's current velocity, tuned to
                        // take about as long as a fresh wave. No dead stop, no overshoot past the button.
                        radius.animateTo(target, spring(dampingRatio = 1f, stiffness = 60f, visibilityThreshold = 2f), initialVelocity = carried)
                    }
                    wave = null
                }
            }
        }
    }

    Box(
        Modifier.fillMaxSize().drawWithContent {
            contentLayer.record { this@drawWithContent.drawContent() }
            val w = wave
            if (w != null && Build.VERSION.SDK_INT >= 33) {
                effectLayer.record { drawLayer(contentLayer) }
                effectLayer.renderEffect = w.effect(radius.value)
                drawLayer(effectLayer)
            } else {
                drawLayer(contentLayer)
            }
        }
    ) {
        CompositionLocalProvider(LocalThemeSwitcher provides switcher) { content() }
    }
}

private class WaveState(snapshot: android.graphics.Bitmap, val center: Offset, val band: Float, val chroma: Float, val invert: Boolean, val size: Offset) {
    private val snapshotShader = BitmapShader(snapshot, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    private val shader: Any? = if (Build.VERSION.SDK_INT >= 33) RuntimeShader(WAVE_SHADER) else null

    @RequiresApi(33)
    fun effect(radius: Float): androidx.compose.ui.graphics.RenderEffect {
        val s = shader as RuntimeShader
        s.setInputShader("old", snapshotShader)
        s.setFloatUniform("center", center.x, center.y)
        s.setFloatUniform("radius", radius)
        s.setFloatUniform("band", band)
        s.setFloatUniform("chroma", chroma)
        s.setFloatUniform("invert", if (invert) 1f else 0f)
        s.setFloatUniform("size", size.x, size.y)
        return RenderEffect.createRuntimeShaderEffect(s, "content").asComposeRenderEffect()
    }
}

/** Round glass sun/moon button; flips the theme with the wave from its own centre. */
@Composable
fun ThemeButton(backdrop: Backdrop, size: Dp = 44.dp) {
    val switch = LocalThemeSwitcher.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    GlassCircle(backdrop, { switch(bounds.center, !Tg.dark) }, size, Modifier.menuAnchor { bounds = it }) {
        // The icon spins half a turn and the sun/moon swap pops in with a spring.
        val spin by animateFloatAsState(if (Tg.dark) 180f else 0f, spring(0.6f, 180f), label = "spin")
        AnimatedContent(
            Tg.dark,
            Modifier.graphicsLayer { rotationZ = spin },
            transitionSpec = { (scaleIn(spring(0.55f, 300f)) + fadeIn()) togetherWith (scaleOut() + fadeOut()) },
            label = "sunMoon",
        ) { dark ->
            Icon(if (dark) Icons.Rounded.DarkMode else Icons.Rounded.LightMode, Tg.Text, 22.dp)
        }
    }
}
