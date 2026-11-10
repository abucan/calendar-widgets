package app.calwidgets.core

import java.time.DayOfWeek
import java.time.YearMonth

/**
 * The weeks needed to show [month], each with seven days starting on
 * [firstDayOfWeek]. Days of the neighbouring months fill the first and last
 * week. Returns four to six weeks.
 */
fun monthGrid(month: YearMonth, firstDayOfWeek: DayOfWeek): List<List<DayCell>> {
    val first = month.atDay(1)
    val lead = (first.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    val weeks = (lead + month.lengthOfMonth() + 6) / 7
    val start = first.minusDays(lead.toLong())
    return List(weeks) { week ->
        List(7) { day ->
            val date = start.plusDays((week * 7 + day).toLong())
            DayCell(date, YearMonth.from(date) == month)
        }
    }
}

/** The seven weekdays in display order, starting on [firstDayOfWeek]. */
fun weekdayOrder(firstDayOfWeek: DayOfWeek): List<DayOfWeek> =
    List(7) { firstDayOfWeek.plus(it.toLong()) }
