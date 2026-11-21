package com.adamfoerster.mdhabits

import com.adamfoerster.mdhabits.data.markdown.MarkdownCodecs
import com.adamfoerster.mdhabits.domain.model.Recurrence
import com.adamfoerster.mdhabits.domain.model.Task
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MonthlyTaskTest {

    private fun monthly(doneOn: LocalDate? = null) = Task("m1", "Revisar contas", 10, Recurrence.MONTHLY, doneOn = doneOn)

    @Test
    fun aMonthlyTaskIsDoneOnlyForTheMonthItWasCheckedOffIn() {
        val task = monthly(doneOn = LocalDate(2026, 7, 3))
        assertTrue(task.isDoneInMonthOf(LocalDate(2026, 7, 31)))
        // Due again on the first of the next month, and a same-month day of another year doesn't count.
        assertFalse(task.isDoneInMonthOf(LocalDate(2026, 8, 1)))
        assertFalse(task.isDoneInMonthOf(LocalDate(2025, 7, 3)))
        assertFalse(monthly().isDoneInMonthOf(LocalDate(2026, 7, 3)))
    }

    @Test
    fun aMonthlyTaskHasNoParticularDueDay() {
        assertFalse(monthly().isPerDay)
        kotlinx.datetime.DayOfWeek.entries.forEach { assertFalse(monthly().isDueOn(it)) }
    }

    @Test
    fun aMonthlyTaskRoundTripsThroughItsNote() {
        val task = monthly(doneOn = LocalDate(2026, 7, 3))
        assertEquals(task, MarkdownCodecs.decodeTask(MarkdownCodecs.encodeTask(task)))
    }
}
