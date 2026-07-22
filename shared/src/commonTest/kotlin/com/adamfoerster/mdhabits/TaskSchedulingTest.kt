package com.adamfoerster.mdhabits

import com.adamfoerster.mdhabits.data.markdown.MarkdownCodecs
import com.adamfoerster.mdhabits.data.repo.InMemoryPenaltyRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryPointsLedgerRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryTaskRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryThemeRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryValueRepository
import com.adamfoerster.mdhabits.domain.model.Recurrence
import com.adamfoerster.mdhabits.domain.model.Task
import com.adamfoerster.mdhabits.domain.usecase.ApplyPenaltyUseCase
import com.adamfoerster.mdhabits.domain.usecase.CompleteTaskUseCase
import com.adamfoerster.mdhabits.ui.screens.home.HomeViewModel
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TaskSchedulingCodecTest {

    @Test
    fun daysOfWeekTaskRoundTrip() {
        val task = Task(
            id = "t-1",
            title = "Academia",
            points = 8,
            recurrence = Recurrence.DAYS_OF_WEEK,
            daysOfWeek = listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
        )
        assertEquals(task, MarkdownCodecs.decodeTask(MarkdownCodecs.encodeTask(task)))
    }

    @Test
    fun dailyTaskRoundTrip() {
        val task = Task(id = "t-2", title = "Meditar", points = 5, recurrence = Recurrence.DAILY)
        assertEquals(task, MarkdownCodecs.decodeTask(MarkdownCodecs.encodeTask(task)))
    }

    @Test
    fun legacyOnceNotesDecodeAsAdhoc() {
        // Notes written before 0.5.0 stored the ad-hoc frequency as ONCE.
        val legacyNote = MarkdownCodecs
            .encodeTask(Task(id = "t-3", title = "Dentista", points = 3, recurrence = Recurrence.ADHOC))
            .replace("\"ADHOC\"", "\"ONCE\"")
        assertEquals(Recurrence.ADHOC, MarkdownCodecs.decodeTask(legacyNote)?.recurrence)
    }
}

/** The fixed test instant 2026-07-02 is a Thursday of ISO week 2026-W27. */
class HomeSectionsTest : MainDispatcherTest() {

    private val thursday = "2026-07-02T12:00:00Z"
    private val friday = "2026-07-03T12:00:00Z"

    private fun newViewModel(
        tasks: InMemoryTaskRepository,
        ledger: InMemoryPointsLedgerRepository = InMemoryPointsLedgerRepository(),
        iso: String = thursday,
    ) = HomeViewModel(
        taskRepository = tasks,
        themeRepository = InMemoryThemeRepository(),
        ledgerRepository = ledger,
        penaltyRepository = InMemoryPenaltyRepository(),
        valueRepository = InMemoryValueRepository(),
        completeTask = CompleteTaskUseCase(tasks, ledger, fixedClock(iso), TimeZone.UTC),
        applyPenalty = ApplyPenaltyUseCase(ledger, fixedClock(iso)),
        weekCalculator = fixedWeekCalculator(iso),
    )

    @Test
    fun tasksAreGroupedIntoTodayWeeklyAndAdhocSections() = runTest {
        val tasks = InMemoryTaskRepository()
        tasks.upsert(Task("daily", "Meditate", 5, Recurrence.DAILY))
        tasks.upsert(Task("thu", "Gym", 8, Recurrence.DAYS_OF_WEEK, daysOfWeek = listOf(DayOfWeek.THURSDAY)))
        tasks.upsert(Task("mon", "Swim", 8, Recurrence.DAYS_OF_WEEK, daysOfWeek = listOf(DayOfWeek.MONDAY)))
        tasks.upsert(Task("weekly", "Read", 5, Recurrence.WEEKLY))
        tasks.upsert(Task("adhoc", "Dentist", 3, Recurrence.ADHOC))

        val vm = newViewModel(tasks)
        keepHot(vm.state)

        val state = vm.state.value
        // Today: the daily task plus the days-of-week task scheduled for Thursday.
        assertEquals(listOf("Gym", "Meditate"), state.todayTasks.map { it.task.title })
        assertEquals(listOf("Read"), state.weeklyTasks.map { it.task.title })
        assertEquals(listOf("Dentist"), state.adhocTasks.map { it.task.title })
        // The Monday-only task is not due today, so it appears nowhere.
        assertFalse(state.allTasks.any { it.task.id == "mon" })
    }

    @Test
    fun dailyCompletionCountsOnlyForTheDayItWasMade() = runTest {
        val tasks = InMemoryTaskRepository()
        val ledger = InMemoryPointsLedgerRepository()
        val daily = Task("daily", "Meditate", 5, Recurrence.DAILY)
        val weekly = Task("weekly", "Read", 5, Recurrence.WEEKLY)
        tasks.upsert(daily)
        tasks.upsert(weekly)

        val thursdayVm = newViewModel(tasks, ledger, iso = thursday)
        keepHot(thursdayVm.state)
        thursdayVm.onToggleComplete(daily, completed = true)
        thursdayVm.onToggleComplete(weekly, completed = true)
        assertTrue(thursdayVm.state.value.todayTasks.first { it.task.id == "daily" }.completed)

        // The next day (same ISO week) the daily task is due again, but the weekly one stays done.
        val fridayVm = newViewModel(tasks, ledger, iso = friday)
        keepHot(fridayVm.state)
        assertFalse(fridayVm.state.value.todayTasks.first { it.task.id == "daily" }.completed)
        assertTrue(fridayVm.state.value.weeklyTasks.first { it.task.id == "weekly" }.completed)

        // Completing it again on Friday earns points for the new day.
        fridayVm.onToggleComplete(daily, completed = true)
        assertTrue(fridayVm.state.value.todayTasks.first { it.task.id == "daily" }.completed)
        assertEquals(15, ledger.currentBalance())
    }
}
