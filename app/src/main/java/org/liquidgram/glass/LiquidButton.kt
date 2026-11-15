package org.liquidgram.glass

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.glass.render.Backdrop

/**
 * Glass capsule button: Glasmio's iOS button material and press motion (github.com/NEORUAA/Glasmio, Apache-2.0).
 * A [tint] makes it a prominent (filled) button in that colour.
 */
@Composable
fun LiquidButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") isInteractive: Boolean = true,
    tint: Color = Color.Unspecified,
    @Suppress("UNUSED_PARAMETER") surfaceColor: Color = Color.Unspecified,
    height: Dp = 48f.dp,
    horizontalPadding: Dp = 16f.dp,
    content: @Composable RowScope.() -> Unit,
) = org.liquidgram.ui.GlassBox(
    backdrop, modifier.height(height), androidx.compose.foundation.shape.CircleShape,
    tint = tint.takeIf { it.isSpecified }, prominent = tint.isSpecified, onClick = onClick, material = null,
    contentAlignment = Alignment.Center,
) {
    Row(Modifier.padding(horizontal = horizontalPadding), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically, content = content)
}
