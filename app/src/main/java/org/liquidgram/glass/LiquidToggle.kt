package org.liquidgram.glass

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.ui.GlasmioSwitch

/** iOS switch: Glasmio's native control lens (github.com/NEORUAA/Glasmio, Apache-2.0). */
@Composable
fun LiquidToggle(
    selected: () -> Boolean,
    onSelect: (Boolean) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) = GlasmioSwitch(checked = selected(), onCheckedChange = onSelect, modifier = modifier, backdrop = backdrop)
