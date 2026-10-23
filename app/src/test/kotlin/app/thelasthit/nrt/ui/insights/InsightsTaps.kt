package app.thelasthit.nrt.ui.insights

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp

/**
 * Taps the hour map's cell at [column] (Monday is 0) and [row] (0 starts at the day start), from
 * the layout in `HourMap` at the normal font size: a 42 dp label column, 2 dp gaps, 14 dp rows,
 * and day names 16 sp tall over a 6 dp gap.
 */
fun ComposeContentTestRule.tapHour(column: Int, row: Int) {
    val map = onNode(hasContentDescription("by hour and day", substring = true))
    map.performScrollTo()
    map.performTouchInput {
        val gap = 2.dp.toPx()
        val label = 42.dp.toPx()
        val cell = (width - label - gap * 7) / 7
        val header = 16.dp.toPx() + 6.dp.toPx()
        val x = label + gap + column * (cell + gap) + cell / 2
        val y = header + gap + row * (14.dp.toPx() + gap) + 7.dp.toPx()
        click(Offset(x, y))
    }
}

/**
 * Long-presses the hour map's cell at [column] and [fromRow], then slides down to [toRow], from the
 * same layout as [tapHour].
 */
fun ComposeContentTestRule.pressAndSlideHour(column: Int, fromRow: Int, toRow: Int) {
    val map = onNode(hasContentDescription("by hour and day", substring = true))
    map.performScrollTo()
    map.performTouchInput {
        val gap = 2.dp.toPx()
        val label = 42.dp.toPx()
        val cell = (width - label - gap * 7) / 7
        val header = 16.dp.toPx() + 6.dp.toPx()
        val x = label + gap + column * (cell + gap) + cell / 2
        fun y(row: Int) = header + gap + row * (14.dp.toPx() + gap) + 7.dp.toPx()
        down(Offset(x, y(fromRow)))
        // Held still past the long press, in small steps so the clock moves with the events.
        repeat(((viewConfiguration.longPressTimeoutMillis + 100) / 50).toInt()) { move(delayMillis = 50) }
        (1..5).forEach { step -> moveTo(Offset(x, y(fromRow) + (y(toRow) - y(fromRow)) * step / 5f)) }
        up()
    }
}

/**
 * Taps a calendar day at [column] (Monday is 0) in week [row], from the layout in `CalendarMap`:
 * 40 dp squares spread edge to edge, under day names 16 sp tall and a 2 dp gap, in 48 dp rows.
 */
fun ComposeContentTestRule.tapCalendarDay(column: Int, row: Int) {
    val calendar = onNode(hasContentDescription("Calendar of mg each day", substring = true))
    calendar.performScrollTo()
    calendar.performTouchInput {
        val side = minOf(40.dp.toPx(), (width - 4.dp.toPx() * 6) / 7)
        val gap = (width - side * 7) / 6
        val header = 16.dp.toPx() + 2.dp.toPx()
        click(Offset(column * (side + gap) + side / 2, header + row * 48.dp.toPx() + 24.dp.toPx()))
    }
}
