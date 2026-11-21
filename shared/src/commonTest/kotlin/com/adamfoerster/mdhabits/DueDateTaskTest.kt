package com.adamfoerster.mdhabits

import com.adamfoerster.mdhabits.data.markdown.MarkdownCodecs
import com.adamfoerster.mdhabits.data.markdown.WeekNote
import com.adamfoerster.mdhabits.data.repo.InMemoryPointsLedgerRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryTaskRepository
import com.adamfoerster.mdhabits.domain.model.PointsEvent
import com.adamfoerster.mdhabits.domain.model.PointsSource
import com.adamfoerster.mdhabits.domain.model.Recurrence
import com.adamfoerster.mdhabits.domain.model.Task
import com.adamfoerster.mdhabits.domain.usecase.PenalizeMissedHabitsUseCase
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The fixed test instant 2026-07-02 is the Thursday of ISO week 2026-W27. */
private const val NOW = "2026-07-02T12:00:00Z"
private val TODAY = LocalDate(2026, 7, 2)
private val YESTERDAY = LocalDate(2026, 7, 1)

private fun dated(due: LocalDate?, doneOn: LocalDate? = null, missed: LocalDate? = null) =
    Task("d1", "Pagar IPTU", 8, Recurrence.ADHOC, dueDate = due, doneOn = doneOn, dueMissedOn = missed)

class DueDateTaskTest {

    private val tasks = InMemoryTaskRepository()
    private val ledger = InMemoryPointsLedgerRepository()
    private val sweep = PenalizeMissedHabitsUseCase(tasks, ledger, fixedWeekCalculator(NOW), fixedClock(NOW))

    @Test
    fun aPassedDeadlineWithoutTheTaskDoneCostsItsPoints() = runTest {
        tasks.upsert(dated(YESTERDAY))

        sweep()

        assertEquals(-8, ledger.currentBalance())
        val event = ledger.eventsForWeek("2026-W27").single()
        assertEquals(PointsSource.DEADLINE_MISS, event.source)
        assertEquals("d1@2026-07-01", event.refId)
        assertEquals(YESTERDAY, tasks.getTask("d1")?.dueMissedOn)
    }

    @Test
    fun theDeadlineIsChargedOnlyOnce() = runTest {
        tasks.upsert(dated(YESTERDAY))

        sweep()
        sweep()

        assertEquals(-8, ledger.currentBalance())
    }

    @Test
    fun theDeadlineDayItselfIsNotOverYet() = runTest {
        tasks.upsert(dated(TODAY))

        sweep()

        assertEquals(0, ledger.currentBalance())
    }

    @Test
    fun aTaskDoneOnTimeIsNeverCharged() = runTest {
        tasks.upsert(dated(YESTERDAY, doneOn = YESTERDAY))

        sweep()

        assertEquals(0, ledger.currentBalance())
    }

    @Test
    fun aTaskDoneAfterTheDeadlineIsStillCharged() = runTest {
        tasks.upsert(dated(YESTERDAY, doneOn = TODAY))

        sweep()

        assertEquals(-8, ledger.currentBalance())
    }

    @Test
    fun aTaskWithoutDeadlineOrNotAdHocIsIgnored() = runTest {
        tasks.upsert(dated(null))
        tasks.upsert(dated(YESTERDAY).copy(id = "d2", recurrence = Recurrence.WEEKLY))
        tasks.upsert(dated(YESTERDAY).copy(id = "d3", active = false))

        sweep()

        assertEquals(0, ledger.currentBalance())
    }

    @Test
    fun aNewDeadlineCanBeChargedAgain() = runTest {
        tasks.upsert(dated(YESTERDAY, missed = YESTERDAY).copy(dueDate = LocalDate(2026, 6, 30)))

        sweep()

        assertEquals(-8, ledger.currentBalance())
    }
}

class DueDateCodecTest {

    @Test
    fun theDeadlineAndItsChargeRoundTrip() {
        val task = dated(YESTERDAY, missed = YESTERDAY)
        assertEquals(task, MarkdownCodecs.decodeTask(MarkdownCodecs.encodeTask(task)))
        assertNull(MarkdownCodecs.decodeTask(MarkdownCodecs.encodeTask(dated(null)))?.dueDate)
    }

    @Test
    fun aMissedDeadlineEntryRoundTripsThroughTheWeekNote() {
        val event = PointsEvent(
            "L-1", Instant.parse(NOW), "2026-W27", PointsSource.DEADLINE_MISS, "d1@2026-07-01", "Pagar IPTU", -8,
        )
        val decoded = MarkdownCodecs.decodeWeekNote(
            MarkdownCodecs.encodeWeekNote(WeekNote(weekId = "2026-W27", events = listOf(event))),
        )
        assertEquals(event, decoded?.events?.single())
    }
}
