package org.liquidgram.glass

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.ui.GlasmioSlider

/** iOS slider: Glasmio's native control lens (github.com/NEORUAA/Glasmio, Apache-2.0). */
@Composable
fun LiquidSlider(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    @Suppress("UNUSED_PARAMETER") visibilityThreshold: Float,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) = GlasmioSlider(value = value(), onValueChange = onValueChange, modifier = modifier, valueRange = valueRange, backdrop = backdrop)
