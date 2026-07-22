package com.adamfoerster.mdhabits.core.datetime

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/** A Monday..Sunday date range for a single ISO week. */
data class WeekRange(val start: LocalDate, val endInclusive: LocalDate)

/**
 * ISO-8601 week calculations. A week belongs to the year of its Thursday, and week 1 is the week
 * containing the first Thursday of that year. [clock] is injectable so week logic is unit-testable.
 */
class WeekCalculator(
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    fun today(): LocalDate = clock.now().toLocalDateTime(timeZone).date

    /** ISO (year, week) for [date]. */
    fun isoWeek(date: LocalDate): Pair<Int, Int> {
        val dow = date.dayOfWeek.isoDayNumber // Mon=1..Sun=7
        val thursday = date.plus(4 - dow, DateTimeUnit.DAY)
        val week = (thursday.dayOfYear - 1) / 7 + 1
        return thursday.year to week
    }

    /** Stable week identifier, e.g. "2026-W27". */
    fun weekId(date: LocalDate = today()): String {
        val (year, week) = isoWeek(date)
        return "$year-W${pad2(week)}"
    }

    /** The Monday..Sunday range of the week containing [date]. */
    fun rangeOf(date: LocalDate = today()): WeekRange {
        val dow = date.dayOfWeek.isoDayNumber
        val monday = date.minus(dow - 1, DateTimeUnit.DAY)
        return WeekRange(monday, monday.plus(6, DateTimeUnit.DAY))
    }

    /** Range of the week identified by [weekId] (e.g. "2026-W27"). */
    fun rangeOfWeekId(weekId: String): WeekRange? {
        val date = mondayOfWeekId(weekId) ?: return null
        return WeekRange(date, date.plus(6, DateTimeUnit.DAY))
    }

    /** The weekId of the week immediately before the one containing [date]. */
    fun previousWeekId(date: LocalDate = today()): String =
        weekId(rangeOf(date).start.minus(1, DateTimeUnit.DAY))

    private fun mondayOfWeekId(weekId: String): LocalDate? {
        val match = WEEK_ID_REGEX.matchEntire(weekId) ?: return null
        val year = match.groupValues[1].toInt()
        val week = match.groupValues[2].toInt()
        // Monday of ISO week 1 = the Monday on/before Jan 4th; add (week-1) weeks.
        val jan4 = LocalDate(year, 1, 4)
        val week1Monday = jan4.minus(jan4.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
        return week1Monday.plus((week - 1) * 7, DateTimeUnit.DAY)
    }

    private fun pad2(value: Int): String = if (value < 10) "0$value" else value.toString()

    private companion object {
        val WEEK_ID_REGEX = Regex("""(\d{4})-W(\d{2})""")
    }
}
