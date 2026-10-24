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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.thelasthit.nrt.ui.theme.LastHit
import app.thelasthit.nrt.ui.theme.LastHitColors

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

/**
 * The scrim behind a sheet, as the mockups have it: the scrim colour at 56%, so the page falls
 * back behind the sheet in dark mode too.
 */
val sheetScrim: Color
    @Composable get() = LastHit.colors.scrim.copy(alpha = 0.56f)

/** The radius of a sheet's top corners, as Material draws them from the theme's extra large shape. */
private val SheetCorner = 28.dp

/**
 * In dark mode, a hairline along a sheet's top edge and down its sides, so it stands clear of the
 * dimmed page. It goes on what the sheet holds, which moves as the sheet slides and is dragged, as
 * [AppSheet] has it. On the sheet's own modifier it would stay put at the top of the screen.
 */
fun Modifier.sheetEdge(colors: LastHitColors): Modifier = if (!colors.isDark) {
    this
} else {
    drawWithCache {
        val stroke = 1.dp.toPx()
        val inset = stroke / 2
        val radius = SheetCorner.toPx()
        val edge = Path().apply {
            moveTo(inset, size.height)
            lineTo(inset, radius)
            arcTo(Rect(inset, inset, radius * 2 - inset, radius * 2 - inset), 180f, 90f, forceMoveTo = false)
            lineTo(size.width - radius, inset)
            arcTo(Rect(size.width - radius * 2 + inset, inset, size.width - inset, radius * 2 - inset), 270f, 90f, forceMoveTo = false)
            lineTo(size.width - inset, size.height)
        }
        onDrawWithContent {
            drawContent()
            drawPath(edge, colors.line, style = Stroke(stroke))
        }
    }
}
