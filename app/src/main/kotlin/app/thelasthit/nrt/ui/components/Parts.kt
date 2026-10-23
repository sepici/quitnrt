package app.thelasthit.nrt.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.thelasthit.nrt.ui.theme.LastHit

/**
 * A round tile with an icon, leading list rows. A [dose] gets the neutral stone tile rather than
 * the mint one, since nicotine used is never shown in green.
 */
@Composable
fun IconTile(@DrawableRes icon: Int, modifier: Modifier = Modifier, size: Dp = 44.dp, dose: Boolean = false) {
    val colors = LastHit.colors
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (dose) colors.sunk else colors.iconTile),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = if (dose) colors.text2 else colors.onIconTile)
    }
}

/**
 * A filled shape with a darker band along its bottom, so pills and buttons look pressable, as in
 * the mockups. [edge] is drawn over [colour] inside the shape.
 */
fun Modifier.pressable(colour: Color, shape: Shape, edge: Color = Color.Black.copy(alpha = 0.16f), depth: Dp = 3.dp): Modifier =
    this
        .clip(shape)
        .background(colour)
        .drawBehind {
            val h = depth.toPx()
            drawRect(edge, topLeft = Offset(0f, size.height - h), size = Size(size.width, h))
        }

/**
 * Runs the content [by] past both sides, out of its parent's padding, so a row that scrolls
 * sideways reaches the screen's edges. Give the row the same padding inside its scroll.
 */
fun Modifier.bleed(by: Dp): Modifier = layout { measurable, constraints ->
    val side = by.roundToPx()
    val wide = if (constraints.hasBoundedWidth) constraints.maxWidth + side * 2 else constraints.maxWidth
    val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = wide))
    val width = (placeable.width - side * 2).coerceIn(constraints.minWidth, constraints.maxWidth)
    layout(width, placeable.height) { placeable.place(-side, 0) }
}
