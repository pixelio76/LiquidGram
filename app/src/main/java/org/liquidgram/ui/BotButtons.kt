package org.liquidgram.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.glass.render.drawBackdrop
import io.github.neoruaa.glasmio.glass.render.effects.blur
import io.github.neoruaa.glasmio.glass.render.effects.lens
import io.github.neoruaa.glasmio.glass.render.effects.vibrancy
import com.kyant.shapes.RoundedRectangle
import org.liquidgram.data.BotButton

/** Bot keyboard (inline under a message, or the reply keyboard under the input): rows of glass keys. */
@Composable
fun BotKeyboard(rows: List<List<BotButton>>, backdrop: Backdrop, modifier: Modifier = Modifier, onPress: (BotButton) -> Unit) =
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { b -> BotKey(b, backdrop, Modifier.weight(1f)) { onPress(b) } }
            }
        }
    }

/** Bot API button colours; plain buttons are clear glass. */
private fun styleColor(style: String): Color? = when (style) {
    "danger" -> Color(0xFFE5484D)
    "success" -> Color(0xFF30B158)
    "primary" -> Tg.Accent
    else -> null
}

@Composable
private fun BotKey(b: BotButton, backdrop: Backdrop, modifier: Modifier, onClick: () -> Unit) {
    val color = styleColor(b.style)
    GlassBox(
        backdrop, modifier.heightIn(min = 44.dp), RoundedRectangle(16.dp),
        tint = color, prominent = color != null, onClick = onClick,
        contentAlignment = Alignment.Center,
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (b.iconPath != null) StickerView(b.iconPath, b.iconKind, "", 20.dp, Modifier.padding(end = 5.dp))
            T(b.text, 15.sp, if (color != null) Color.White else Tg.Text, FontWeight.Medium, maxLines = 2, align = TextAlign.Center)
        }
        // Link buttons get the small ↗ in the corner, as in Telegram.
        if (b.kind == "url") Icon(Icons.Rounded.NorthEast, if (color != null) Color.White else Tg.Secondary, 12.dp, Modifier.align(Alignment.TopEnd).padding(5.dp))
    }
}
