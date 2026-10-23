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

/** Taps a calendar day at [column] (Monday is 0) in week [row], from the layout in `CalendarMap`. */
fun ComposeContentTestRule.tapCalendarDay(column: Int, row: Int) {
    val calendar = onNode(hasContentDescription("Calendar of mg each day", substring = true))
    calendar.performScrollTo()
    calendar.performTouchInput {
        val gap = 4.dp.toPx()
        val columnWidth = (width - gap * 6) / 7
        val header = 16.dp.toPx() + 2.dp.toPx()
        click(Offset(column * (columnWidth + gap) + columnWidth / 2, header + row * 44.dp.toPx() + 22.dp.toPx()))
    }
}
