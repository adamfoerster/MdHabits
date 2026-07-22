package com.adamfoerster.mdhabits

import com.adamfoerster.mdhabits.core.datetime.WeekCalculator
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class WeekCalculatorTest {

    private fun calcAt(iso: String) = WeekCalculator(
        clock = object : Clock {
            override fun now(): Instant = Instant.parse(iso)
        },
        timeZone = TimeZone.UTC,
    )

    @Test
    fun isoWeekAndIdForKnownThursday() {
        val wc = calcAt("2026-07-02T12:00:00Z") // Thursday
        assertEquals(2026 to 27, wc.isoWeek(LocalDate(2026, 7, 2)))
        assertEquals("2026-W27", wc.weekId())
    }

    @Test
    fun rangeIsMondayToSunday() {
        val wc = calcAt("2026-07-02T12:00:00Z")
        val range = wc.rangeOf()
        assertEquals(LocalDate(2026, 6, 29), range.start)
        assertEquals(LocalDate(2026, 7, 5), range.endInclusive)
    }

    @Test
    fun weekIdRoundTripsThroughRange() {
        val wc = calcAt("2026-07-02T12:00:00Z")
        val range = wc.rangeOfWeekId("2026-W27")
        assertEquals(LocalDate(2026, 6, 29), range?.start)
        assertEquals(LocalDate(2026, 7, 5), range?.endInclusive)
    }

    @Test
    fun previousWeekId() {
        val wc = calcAt("2026-07-02T12:00:00Z")
        assertEquals("2026-W26", wc.previousWeekId())
    }

    @Test
    fun handlesIsoYearBoundary() {
        // 2027-01-01 is a Friday, whose ISO week belongs to 2026 (week 53).
        val wc = calcAt("2027-01-01T12:00:00Z")
        assertEquals(2026 to 53, wc.isoWeek(LocalDate(2027, 1, 1)))
    }
}
