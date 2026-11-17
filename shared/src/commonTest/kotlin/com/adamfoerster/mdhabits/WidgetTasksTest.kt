package com.adamfoerster.mdhabits

import com.adamfoerster.mdhabits.data.repo.InMemoryPointsLedgerRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryTaskRepository
import com.adamfoerster.mdhabits.domain.model.Recurrence
import com.adamfoerster.mdhabits.domain.model.Task
import com.adamfoerster.mdhabits.domain.usecase.CompleteTaskUseCase
import com.adamfoerster.mdhabits.domain.usecase.WidgetTasksUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The fixed test instant 2026-07-02 is a Thursday of ISO week 2026-W27. */
class WidgetTasksTest {

    private val weekCalculator = fixedWeekCalculator()
    private val weekId = weekCalculator.weekId()

    private suspend fun setUp(vararg tasks: Task): Triple<WidgetTasksUseCase, InMemoryTaskRepository, InMemoryPointsLedgerRepository> {
        val repo = InMemoryTaskRepository()
        val ledger = InMemoryPointsLedgerRepository()
        tasks.forEach { repo.upsert(it) }
        val useCase = WidgetTasksUseCase(repo, CompleteTaskUseCase(repo, ledger, clock = fixedClock()), weekCalculator)
        return Triple(useCase, repo, ledger)
    }

    @Test
    fun listsTasksDueTodayAndWeeklyTasksButNoAdhocOnes() = runTest {
        val (useCase, _, _) = setUp(
            Task("d", "Meditar", 5, Recurrence.DAILY),
            Task("h", "Sem açúcar", 4, Recurrence.HABIT),
            Task("thu", "Academia", 8, Recurrence.DAYS_OF_WEEK, daysOfWeek = listOf(DayOfWeek.THURSDAY)),
            Task("mon", "Natação", 8, Recurrence.DAYS_OF_WEEK, daysOfWeek = listOf(DayOfWeek.MONDAY)),
            Task("w", "Faxina", 10, Recurrence.WEEKLY),
            Task("a", "Dentista", 3, Recurrence.ADHOC),
            Task("off", "Inativa", 3, Recurrence.DAILY, active = false),
        )
        val snapshot = useCase.snapshot()
        assertEquals(listOf("thu", "d", "h"), snapshot.today.map { it.taskId })
        assertEquals(listOf("w"), snapshot.week.map { it.taskId })
    }

    @Test
    fun togglingChecksOffTodayAndCreditsTheLedger() = runTest {
        val (useCase, repo, ledger) = setUp(Task("d", "Meditar", 5, Recurrence.DAILY), Task("w", "Faxina", 10, Recurrence.WEEKLY))

        assertTrue(useCase.toggle("d"))
        assertTrue(useCase.toggle("w"))

        val snapshot = useCase.snapshot()
        assertTrue(snapshot.today.single().completed)
        assertTrue(snapshot.week.single().completed)
        assertEquals(15, ledger.currentBalance())
        val instance = repo.observeInstances(weekId).first().single { it.taskId == "d" }
        assertEquals(listOf(weekCalculator.today()), instance.completedDates)
    }

    @Test
    fun togglingAgainUndoesIt() = runTest {
        val (useCase, _, ledger) = setUp(Task("d", "Meditar", 5, Recurrence.DAILY))
        useCase.toggle("d")
        useCase.toggle("d")
        assertFalse(useCase.snapshot().today.single().completed)
        assertEquals(0, ledger.currentBalance())
    }

    @Test
    fun completedRowsSortAfterPendingOnes() = runTest {
        val (useCase, _, _) = setUp(Task("a", "A", 1, Recurrence.DAILY), Task("b", "B", 1, Recurrence.DAILY))
        useCase.toggle("a")
        assertEquals(listOf("b", "a"), useCase.snapshot().today.map { it.taskId })
    }

    @Test
    fun ignoresTasksTheWidgetDoesNotList() = runTest {
        val (useCase, _, ledger) = setUp(
            Task("a", "Dentista", 3, Recurrence.ADHOC),
            Task("mon", "Natação", 8, Recurrence.DAYS_OF_WEEK, daysOfWeek = listOf(DayOfWeek.MONDAY)),
        )
        assertFalse(useCase.toggle("a"))
        assertFalse(useCase.toggle("mon"))
        assertFalse(useCase.toggle("missing"))
        assertEquals(0, ledger.currentBalance())
    }
}
