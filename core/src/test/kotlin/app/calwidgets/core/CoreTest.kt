package app.calwidgets.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

class CoreTest {
    private val zone = ZoneId.of("Europe/Zagreb")

    private fun at(date: String, time: String = "00:00"): Long =
        LocalDateTime.parse("${date}T$time").atZone(zone).toInstant().toEpochMilli()

    private fun utcMidnight(date: String): Long =
        LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun timed(id: Long, date: String, from: String, to: String, endDate: String = date, color: Int = 1) =
        CalendarEvent(id, "e$id", at(date, from), at(endDate, to), allDay = false, color = color)

    private fun allDay(id: Long, from: String, toExclusive: String, color: Int = 2) =
        CalendarEvent(id, "a$id", utcMidnight(from), utcMidnight(toExclusive), allDay = true, color = color)

    // --- month grid ---

    @Test
    fun gridStartsOnChosenWeekday() {
        // 1 Nov 2026 is a Sunday.
        val monday = monthGrid(YearMonth.of(2026, 11), DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 10, 26), monday.first().first().date)
        assertEquals(6, monday.size)
        assertEquals(LocalDate.of(2026, 12, 6), monday.last().last().date)

        val sunday = monthGrid(YearMonth.of(2026, 11), DayOfWeek.SUNDAY)
        assertEquals(LocalDate.of(2026, 11, 1), sunday.first().first().date)
        assertEquals(5, sunday.size)
    }

    @Test
    fun gridMarksDaysOutsideTheMonth() {
        val grid = monthGrid(YearMonth.of(2026, 11), DayOfWeek.MONDAY)
        assertFalse(grid[0][0].inMonth)
        assertTrue(grid[0][6].inMonth)
        assertEquals(30, grid.flatten().count { it.inMonth })
        assertTrue(grid.all { it.size == 7 })
    }

    @Test
    fun februaryFittingExactlyFourWeeks() {
        // February 2027 starts on a Monday and has 28 days.
        val grid = monthGrid(YearMonth.of(2027, 2), DayOfWeek.MONDAY)
        assertEquals(4, grid.size)
        assertTrue(grid.flatten().all { it.inMonth })
    }

    @Test
    fun leapFebruary() {
        val grid = monthGrid(YearMonth.of(2028, 2), DayOfWeek.MONDAY)
        assertEquals(29, grid.flatten().count { it.inMonth })
    }

    @Test
    fun weekdayOrderWraps() {
        assertEquals(
            listOf(
                DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY,
            ),
            weekdayOrder(DayOfWeek.SUNDAY),
        )
    }

    // --- days an event covers ---

    @Test
    fun allDayEventUsesUtcDatesNotLocalOnes() {
        val event = allDay(1, "2026-11-10", "2026-11-11")
        assertEquals(LocalDate.of(2026, 11, 10), event.firstDay(zone))
        assertEquals(LocalDate.of(2026, 11, 10), event.lastDay(zone))
        // Also west of UTC, where the UTC midnight is still the previous local day.
        val la = ZoneId.of("America/Los_Angeles")
        assertEquals(LocalDate.of(2026, 11, 10), event.firstDay(la))
        assertEquals(LocalDate.of(2026, 11, 10), event.lastDay(la))
    }

    @Test
    fun multiDayAllDayEvent() {
        val event = allDay(1, "2026-11-10", "2026-11-13")
        assertTrue(event.occursOn(LocalDate.of(2026, 11, 12), zone))
        assertFalse(event.occursOn(LocalDate.of(2026, 11, 13), zone))
    }

    @Test
    fun eventEndingAtMidnightDoesNotTouchNextDay() {
        val event = timed(1, "2026-11-10", "22:00", "00:00", endDate = "2026-11-11")
        assertEquals(LocalDate.of(2026, 11, 10), event.lastDay(zone))
    }

    @Test
    fun eventCrossingMidnightShowsOnBothDays() {
        val event = timed(1, "2026-11-10", "23:00", "01:00", endDate = "2026-11-11")
        assertTrue(event.occursOn(LocalDate.of(2026, 11, 10), zone))
        assertTrue(event.occursOn(LocalDate.of(2026, 11, 11), zone))
    }

    @Test
    fun zeroLengthEvent() {
        val event = timed(1, "2026-11-10", "09:00", "09:00")
        assertTrue(event.occursOn(LocalDate.of(2026, 11, 10), zone))
    }

    // --- agenda ---

    @Test
    fun dayListPutsAllDayFirstThenByStart() {
        val events = listOf(
            timed(1, "2026-11-10", "14:00", "15:00"),
            timed(2, "2026-11-10", "09:00", "10:00"),
            allDay(3, "2026-11-10", "2026-11-11"),
            timed(4, "2026-11-11", "09:00", "10:00"),
        )
        assertEquals(listOf(3L, 2L, 1L), eventsOnDay(events, LocalDate.of(2026, 11, 10), zone).map { it.id })
    }

    @Test
    fun remainingDropsFinishedTimedEventsButKeepsAllDay() {
        val events = listOf(
            timed(1, "2026-11-10", "09:00", "10:00"),
            timed(2, "2026-11-10", "11:00", "12:00"),
            allDay(3, "2026-11-10", "2026-11-11"),
        )
        val now = at("2026-11-10", "11:30")
        assertEquals(listOf(3L, 2L), remainingOnDay(events, LocalDate.of(2026, 11, 10), zone, now).map { it.id })
    }

    @Test
    fun nextEventPrefersRunningEventAndSkipsAllDay() {
        val events = listOf(
            allDay(1, "2026-11-10", "2026-11-11"),
            timed(2, "2026-11-10", "09:00", "10:00"),
            timed(3, "2026-11-10", "11:00", "12:00"),
            timed(4, "2026-11-10", "13:00", "14:00"),
        )
        assertEquals(3L, nextTimedEvent(events, at("2026-11-10", "11:30"))?.id)
        assertEquals(3L, nextTimedEvent(events, at("2026-11-10", "10:00"))?.id)
        assertEquals(4L, nextTimedEvent(events, at("2026-11-10", "12:00"))?.id)
        assertNull(nextTimedEvent(events, at("2026-11-10", "14:00")))
    }

    @Test
    fun nextChangeIsNearestStartOrEnd() {
        val events = listOf(
            timed(1, "2026-11-10", "09:00", "10:00"),
            timed(2, "2026-11-10", "11:00", "12:00"),
        )
        assertEquals(at("2026-11-10", "10:00"), nextChangeMillis(events, at("2026-11-10", "09:30")))
        assertEquals(at("2026-11-10", "11:00"), nextChangeMillis(events, at("2026-11-10", "10:00")))
        assertNull(nextChangeMillis(events, at("2026-11-10", "12:00")))
    }

    @Test
    fun dotsAreDistinctCappedAndClippedToRange() {
        val events = listOf(
            allDay(1, "2026-10-30", "2026-11-03", color = 10),
            timed(2, "2026-11-02", "09:00", "10:00", color = 20),
            timed(3, "2026-11-02", "10:00", "11:00", color = 20),
            timed(4, "2026-11-02", "11:00", "12:00", color = 30),
            timed(5, "2026-11-02", "12:00", "13:00", color = 40),
        )
        val dots = colorsByDay(events, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), zone)
        assertEquals(setOf(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2)), dots.keys)
        assertEquals(listOf(10), dots[LocalDate.of(2026, 11, 1)])
        assertEquals(listOf(10, 20, 30), dots[LocalDate.of(2026, 11, 2)])
    }

    // --- formatting ---

    @Test
    fun countdown() {
        val event = timed(1, "2026-11-10", "12:00", "13:00")
        assertEquals("Now", formatCountdown(event, at("2026-11-10", "12:00"), zone))
        assertEquals("Now", formatCountdown(event, at("2026-11-10", "12:30"), zone))
        assertEquals("in 1 min", formatCountdown(event, at("2026-11-10", "12:00") - 20_000, zone))
        assertEquals("in 25 min", formatCountdown(event, at("2026-11-10", "11:35"), zone))
        assertEquals("in 1 h", formatCountdown(event, at("2026-11-10", "11:00"), zone))
        assertEquals("in 2 h 5 min", formatCountdown(event, at("2026-11-10", "09:55"), zone))
        assertEquals("Tomorrow", formatCountdown(event, at("2026-11-09", "09:00"), zone))
        assertEquals("in 3 days", formatCountdown(event, at("2026-11-07", "23:00"), zone))
    }

    @Test
    fun countdownJustAfterMidnightTomorrowUsesHours() {
        val event = timed(1, "2026-11-11", "01:00", "02:00")
        assertEquals("in 2 h", formatCountdown(event, at("2026-11-10", "23:00"), zone))
    }

    @Test
    fun timeRange() {
        val event = timed(1, "2026-11-10", "09:00", "17:30")
        assertEquals("09:00 – 17:30", formatTimeRange(event, zone, use24h = true, locale = Locale.UK))
        assertEquals("9:00 AM – 5:30 PM", formatTimeRange(event, zone, use24h = false, locale = Locale.US))
        assertEquals("All day", formatTimeRange(allDay(2, "2026-11-10", "2026-11-11"), zone, true))
    }

    @Test
    fun dayLabel() {
        val today = LocalDate.of(2026, 11, 10)
        assertEquals("Today", formatDayLabel(today, today))
        assertEquals("Tomorrow", formatDayLabel(today.plusDays(1), today))
        assertEquals("Fri 13 Nov", formatDayLabel(LocalDate.of(2026, 11, 13), today, Locale.UK))
    }
}
