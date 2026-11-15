package io.github.neoruaa.glasmio.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    val initialValue: Float,
    val valueRange: ClosedRange<Float>,
    val visibilityThreshold: Float,
    val initialScale: Float,
    val pressedScale: Float,
    val onDragStarted: DampedDragAnimation.(position: Offset) -> Unit,
    val onDragStopped: DampedDragAnimation.() -> Unit,
    val onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit,
    consumeGestures: Boolean = false,
    val onDragCancelled: DampedDragAnimation.() -> Unit = onDragStopped,
    private val dragCoordinateSpace: (() -> LayoutCoordinates?)? = null,
    sharedValueAnimation: Animatable<Float, AnimationVector1D>? = null,
    horizontalDragOnly: Boolean = false,
    private val lensStyle: NativeControlLensStyle? = null,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {

    private val valueAnimationSpec =
        spring(1f, 1000f, visibilityThreshold)
    private val velocityAnimationSpec =
        spring(0.5f, 300f, visibilityThreshold * 10f)
    // UIKit's lens spec supplies distinct lift/unlift springs for its small variant.
    private val nativeLiftSpec = lensStyle?.let {
        when (it) {
            NativeControlLensStyle.SwitchThumb, NativeControlLensStyle.SliderThumb ->
                spring(.625f, 541.54208f, .001f)
            NativeControlLensStyle.SegmentedIndicator, NativeControlLensStyle.TabBarIndicator ->
                spring(1f, 631.65468f, .001f)
        }
    }
    private val nativeUnliftSpec = lensStyle?.let {
        when (it) {
            NativeControlLensStyle.SwitchThumb, NativeControlLensStyle.SliderThumb ->
                spring(.7f, 157.91367f, .001f)
            NativeControlLensStyle.SegmentedIndicator, NativeControlLensStyle.TabBarIndicator ->
                spring(1f, 631.65468f, .001f)
        }
    }
    private val pressProgressAnimationSpec = nativeLiftSpec ?: spring(1f, 1000f, 0.001f)
    private val releaseProgressAnimationSpec = nativeUnliftSpec ?: pressProgressAnimationSpec
    private val scaleXAnimationSpec =
        nativeLiftSpec ?: spring(0.6f, 250f, 0.001f)
    private val scaleYAnimationSpec =
        nativeLiftSpec ?: spring(0.7f, 250f, 0.001f)
    private val releaseScaleXAnimationSpec = nativeUnliftSpec ?: scaleXAnimationSpec
    private val releaseScaleYAnimationSpec = nativeUnliftSpec ?: scaleYAnimationSpec

    private val valueAnimation =
        sharedValueAnimation ?: Animatable(initialValue, visibilityThreshold)
    // Input must accumulate before the animation coroutine gets a chance to run.
    private var requestedTarget by mutableFloatStateOf(valueAnimation.targetValue)
    private var targetRequest = 0L
    private val velocityAnimation =
        Animatable(0f, 5f)
    private val pressProgressAnimation =
        Animatable(0f, 0.001f)
    private val scaleXAnimation =
        Animatable(initialScale, 0.001f)
    private val scaleYAnimation =
        Animatable(initialScale, 0.001f)

    private val mutatorMutex = MutatorMutex()
    private var releaseJob: Job? = null
    private var releaseGeneration = 0L
    private var nativeLiftStarted: TimeMark? = null
    private var nativeWantsLifted = false

    private val velocityTracker = VelocityTracker()

    val value: Float get() = valueAnimation.value
    val progress: Float get() = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)
    val targetValue: Float get() = requestedTarget
    val pressProgressState: State<Float> = pressProgressAnimation.asState()
    val pressProgress: Float get() = pressProgressState.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val velocity: Float get() = velocityAnimation.value

    private var dragCoordinates: LayoutCoordinates? = null

    /** LiquidGram: finger travel of the current gesture, so a tap can be told from a drag. */
    private var gestureTravel = 0f
    private var tapSlop = 0f
    /** True while the current gesture has not moved past the touch slop. */
    val gestureIsTap: Boolean get() = gestureTravel <= tapSlop
    /** Set by [playTap]: its own scripted release replaces the gesture's. */
    private var scriptedRelease = false

    val modifier: Modifier = Modifier
        .then(if (dragCoordinateSpace != null) {
            Modifier.onGloballyPositioned { dragCoordinates = it }
        } else Modifier)
        .pointerInput(this) {
            tapSlop = viewConfiguration.touchSlop
            inspectDragGestures(
                consumeGestures = consumeGestures,
                horizontalDragOnly = horizontalDragOnly,
                onDragStart = { down ->
                    gestureTravel = 0f
                    scriptedRelease = false
                    onDragStarted(down.position)
                    press()
                },
                onDragEnd = {
                    onDragStopped()
                    if (!scriptedRelease) release()
                },
                onDragCancel = {
                    onDragCancelled()
                    release()
                }
            ) { change, dragAmount ->
                gestureTravel += dragAmount.getDistance()
                val source = dragCoordinates
                val destination = dragCoordinateSpace?.invoke()
                val amount = if (source?.isAttached == true && destination?.isAttached == true) {
                    // Convert a vector at the current transform; subtract origins so the
                    // moving indicator itself cannot contribute extra finger travel.
                    destination.localPositionOf(source, dragAmount) -
                        destination.localPositionOf(source, Offset.Zero)
                } else dragAmount
                onDrag(size, amount)
            }
        }

    fun press() {
        releaseGeneration++
        releaseJob?.cancel()
        releaseJob = null
        if (lensStyle != null) {
            if (!nativeWantsLifted) nativeLiftStarted = timeSource.markNow()
            nativeWantsLifted = true
        }
        velocityTracker.resetTracking()
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(pressedScale, scaleYAnimationSpec) }
        }
    }

    fun release() {
        releaseGeneration++
        val generation = releaseGeneration
        releaseJob?.cancel()
        val liftStarted = nativeLiftStarted
        val operation = animationScope.launch {
            if (lensStyle == null) {
                withFrameNanos { }
                if (value != targetValue) {
                    val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
                    snapshotFlow { valueAnimation.value }
                        .filter { abs(it - targetValue) < threshold }
                        .first()
                }
            } else {
                // UIKit's hang time is a minimum lifted lifetime, not a post-up delay.
                while (liftStarted != null && liftStarted.elapsedNow() < 220.milliseconds) {
                    withFrameNanos { }
                }
                if (generation != releaseGeneration) return@launch
                nativeWantsLifted = false
            }
            coroutineScope {
                launch { pressProgressAnimation.animateTo(0f, releaseProgressAnimationSpec) }
                launch { scaleXAnimation.animateTo(initialScale, releaseScaleXAnimationSpec) }
                launch { scaleYAnimation.animateTo(initialScale, releaseScaleYAnimationSpec) }
            }
            if (generation == releaseGeneration) {
                velocityAnimation.animateTo(0f, velocityAnimationSpec)
                pressProgressAnimation.snapTo(0f)
                scaleXAnimation.snapTo(initialScale)
                scaleYAnimation.snapTo(initialScale)
                velocityAnimation.snapTo(0f)
            }
        }
        releaseJob = operation
    }

    fun updateValue(value: Float) {
        val targetValue = value.coerceIn(valueRange)
        requestedTarget = targetValue
        val request = ++targetRequest
        animationScope.launch {
            if (request != targetRequest) return@launch
            valueAnimation.animateTo(targetValue, valueAnimationSpec) { updateVelocity(request) }
        }
    }

    fun animateToValue(value: Float, animatePress: Boolean = true) {
        val targetValue = value.coerceIn(valueRange)
        requestedTarget = targetValue
        val request = ++targetRequest
        animationScope.launch {
            if (request != targetRequest) return@launch
            mutatorMutex.mutate {
                if (request != targetRequest) return@mutate
                if (animatePress) press()
                launch {
                    if (request == targetRequest) {
                        valueAnimation.animateTo(targetValue, valueAnimationSpec)
                        if (request == targetRequest) velocityAnimation.animateTo(0f, velocityAnimationSpec)
                    }
                }
                release()
            }
        }
    }

    /**
     * LiquidGram: the scripted tap animation (a drag keeps the live physics). The drop swells fully first, flows to
     * [value] on a soft spring while lifted, and only then settles: a quick tap no longer releases a half-swollen
     * drop that hasn't arrived yet.
     */
    fun playTap(value: Float) {
        val targetValue = value.coerceIn(valueRange)
        requestedTarget = targetValue
        val request = ++targetRequest
        scriptedRelease = true
        releaseGeneration++
        val generation = releaseGeneration
        releaseJob?.cancel()
        if (lensStyle != null) nativeWantsLifted = true
        releaseJob = animationScope.launch {
            // Very light physics on a tap: critically damped swell, a barely-bouncy travel and a fraction of the
            // usual speed stretch. Drags keep the full physics.
            launch { pressProgressAnimation.animateTo(1f, TapSwell) }
            launch { scaleXAnimation.animateTo(pressedScale, TapSwell) }
            launch { scaleYAnimation.animateTo(pressedScale, TapSwell) }
            snapshotFlow { pressProgressAnimation.value }.first { it >= 0.6f }
            launch { valueAnimation.animateTo(targetValue, TapMove) { updateVelocity(request, TapStretch) } }
            // Settles as soon as it has arrived, not after the spring's tail.
            snapshotFlow { abs(valueAnimation.value - targetValue) < .04f && pressProgressAnimation.value >= .97f }.first { it }
            if (generation != releaseGeneration || request != targetRequest) return@launch
            nativeWantsLifted = false
            coroutineScope {
                launch { velocityAnimation.animateTo(0f, TapShrink) }
                launch { pressProgressAnimation.animateTo(0f, TapShrink) }
                launch { scaleXAnimation.animateTo(initialScale, TapShrink) }
                launch { scaleYAnimation.animateTo(initialScale, TapShrink) }
            }
        }
    }

    private fun updateVelocity(request: Long, stretch: Float = 1f) {
        if (request != targetRequest) return
        velocityTracker.addPosition(
            Clock.System.now().toEpochMilliseconds(),
            Offset(value, 0f)
        )
        val targetVelocity = stretch * velocityTracker.calculateVelocity().x / (valueRange.endInclusive - valueRange.start)
        animationScope.launch { velocityAnimation.animateTo(targetVelocity, velocityAnimationSpec) }
    }
}

// LiquidGram tap animation, very light physics: no-overshoot swell and shrink, a travel spring damped to a ~1% bounce,
// and a quarter of the drag's speed stretch.
private val TapSwell = spring(1f, 631.65468f, .001f)
private val TapMove = spring(.9f, 360f, .001f)
private val TapShrink = spring(1f, 520f, .001f)
private const val TapStretch = .25f
