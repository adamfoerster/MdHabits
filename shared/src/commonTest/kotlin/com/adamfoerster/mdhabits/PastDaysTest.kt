package com.adamfoerster.mdhabits

import com.adamfoerster.mdhabits.data.repo.InMemoryHealthLogRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryPenaltyRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryPointsLedgerRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryTaskRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryThemeRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryValueRepository
import com.adamfoerster.mdhabits.domain.model.PointsSource
import com.adamfoerster.mdhabits.domain.model.Recurrence
import com.adamfoerster.mdhabits.domain.model.Task
import com.adamfoerster.mdhabits.domain.model.TaskInstance
import com.adamfoerster.mdhabits.domain.model.completing
import com.adamfoerster.mdhabits.domain.repository.PointsLedgerRepository
import com.adamfoerster.mdhabits.domain.usecase.ApplyPenaltyUseCase
import com.adamfoerster.mdhabits.domain.usecase.CompleteTaskUseCase
import com.adamfoerster.mdhabits.ui.screens.home.HomeViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Home's "previous days": the ended days of the weeks still open, which can be checked off late. */
class PastDaysTest : MainDispatcherTest() {

    // The fixed clock is Thursday 2026-07-02 (W27); the previous week, W26, runs Jun 22–28.
    private val wc = fixedWeekCalculator()
    private val today = LocalDate(2026, 7, 2)
    private val yesterday = LocalDate(2026, 7, 1)

    private fun newViewModel(
        tasks: InMemoryTaskRepository,
        ledger: PointsLedgerRepository = InMemoryPointsLedgerRepository(),
    ) = HomeViewModel(
        taskRepository = tasks,
        themeRepository = InMemoryThemeRepository(),
        ledgerRepository = ledger,
        penaltyRepository = InMemoryPenaltyRepository(),
        valueRepository = InMemoryValueRepository(),
        completeTask = CompleteTaskUseCase(tasks, ledger, fixedClock(), TimeZone.UTC),
        applyPenalty = ApplyPenaltyUseCase(ledger),
        weekCalculator = wc,
        syncMdPrayer = disabledMdPrayerSync(wc),
        penalizeMissedHabits = habitSweep(tasks, ledger),
        syncHealth = disabledHealthSync(wc),
        healthLogRepository = InMemoryHealthLogRepository(),
    )

    @Test
    fun pastDaysRunFromYesterdayBackToThePreviousMonday() = runTest {
        val tasks = InMemoryTaskRepository()
        tasks.upsert(Task("d1", "Read", points = 2, recurrence = Recurrence.DAILY))
        tasks.upsert(Task("w1", "Clean", points = 5, recurrence = Recurrence.WEEKLY))
        tasks.upsert(Task("a1", "Taxes", points = 8, recurrence = Recurrence.ADHOC))
        tasks.upsert(
            Task("m1", "Gym", points = 3, recurrence = Recurrence.DAYS_OF_WEEK, daysOfWeek = listOf(DayOfWeek.MONDAY)),
        )

        val vm = newViewModel(tasks)
        keepHot(vm.state)

        val days = vm.state.value.pastDays
        assertEquals((1..10).map { today.minus(it, DateTimeUnit.DAY) }, days.map { it.date })
        assertEquals(LocalDate(2026, 6, 22), days.last().date)
        // Only per-day tasks, and only on the days they're due.
        assertEquals(listOf("m1", "d1"), days.single { it.date == LocalDate(2026, 6, 29) }.tasks.map { it.task.id })
        assertEquals(listOf("d1"), days.first().tasks.map { it.task.id })
    }

    @Test
    fun aClosedWeekIsNeverListed() = runTest {
        val tasks = InMemoryTaskRepository()
        tasks.upsert(Task("d1", "Read", points = 2, recurrence = Recurrence.DAILY))
        val inMemory = InMemoryPointsLedgerRepository()
        // The previous week's note already holds its final balance.
        val ledger = object : PointsLedgerRepository by inMemory {
            override suspend fun isWeekClosed(weekId: String) = weekId == wc.previousWeekId()
        }

        val vm = newViewModel(tasks, ledger)
        keepHot(vm.state)

        assertEquals(
            listOf(yesterday, LocalDate(2026, 6, 30), LocalDate(2026, 6, 29)),
            vm.state.value.pastDays.map { it.date },
        )
    }

    @Test
    fun habitDaysBeforeTheHabitStartedAreNotListed() = runTest {
        val tasks = InMemoryTaskRepository()
        tasks.upsert(
            Task("h1", "Floss", points = 4, recurrence = Recurrence.HABIT, habitSince = LocalDate(2026, 6, 30)),
        )

        val vm = newViewModel(tasks)
        keepHot(vm.state)

        assertEquals(listOf(yesterday, LocalDate(2026, 6, 30)), vm.state.value.pastDays.map { it.date })
    }

    @Test
    fun checkingOffAPastDayCountsForThatDayInItsWeek() = runTest {
        val tasks = InMemoryTaskRepository()
        val ledger = InMemoryPointsLedgerRepository()
        val read = Task("d1", "Read", points = 2, recurrence = Recurrence.DAILY)
        tasks.upsert(read)
        val vm = newViewModel(tasks, ledger)
        keepHot(vm.state)
        val lastSunday = LocalDate(2026, 6, 28)

        vm.onTogglePastDay(read, lastSunday, completed = true)

        assertEquals(listOf(lastSunday), tasks.observeInstances(wc.previousWeekId()).first().single().completedDates)
        assertEquals(2, ledger.currentBalance())
        assertTrue(vm.state.value.pastDays.single { it.date == lastSunday }.tasks.single().completed)
        // Today is still to do.
        assertFalse(vm.state.value.todayTasks.single().completed)
    }

    @Test
    fun undoingAPastDayKeepsTodaysCompletion() = runTest {
        val tasks = InMemoryTaskRepository()
        val read = Task("d1", "Read", points = 2, recurrence = Recurrence.DAILY)
        tasks.upsert(read)
        val vm = newViewModel(tasks)
        keepHot(vm.state)

        vm.onToggleComplete(read, completed = true)
        vm.onTogglePastDay(read, yesterday, completed = true)
        vm.onTogglePastDay(read, yesterday, completed = false)

        val instance = tasks.observeInstances(wc.weekId()).first().single()
        assertEquals(listOf(today), instance.completedDates)
        assertTrue(instance.completed)
        assertEquals(today, instance.completedOn)
        assertTrue(vm.state.value.todayTasks.single().completed)
    }

    @Test
    fun checkingOffAChargedHabitDayLateTakesTheChargeBack() = runTest {
        val tasks = InMemoryTaskRepository()
        val ledger = InMemoryPointsLedgerRepository()
        val floss = Task("h1", "Floss", points = 4, recurrence = Recurrence.HABIT, habitSince = yesterday)
        tasks.upsert(floss)
        // Opening Home charges yesterday, which ended undone.
        val vm = newViewModel(tasks, ledger)
        keepHot(vm.state)
        assertEquals(-4, ledger.currentBalance())

        vm.onTogglePastDay(floss, yesterday, completed = true)
        // The charge is gone and the day earns its points like any completed day.
        assertEquals(4, ledger.currentBalance())

        vm.onTogglePastDay(floss, yesterday, completed = false)
        assertEquals(-4, ledger.currentBalance())

        // The sweep sees the day as charged already and leaves it alone.
        habitSweep(tasks, ledger)()
        assertEquals(-4, ledger.currentBalance())
        assertEquals(3, ledger.eventsForWeek(wc.weekId()).count { it.source == PointsSource.HABIT_MISS })
    }

    @Test
    fun completingAPerDayInstanceTracksEveryDay() {
        val start = TaskInstance("d1", "2026-W27")
        val both = start.completing(true, today, perDay = true).completing(true, yesterday, perDay = true)
        assertEquals(listOf(yesterday, today), both.completedDates)
        assertEquals(today, both.completedOn)

        val undone = both.completing(false, today, perDay = true)
        assertTrue(undone.completed)
        assertEquals(yesterday, undone.completedOn)
        assertFalse(undone.completing(false, yesterday, perDay = true).completed)
    }

    @Test
    fun weeksCloseOnlyAWeekAfterTheirSunday() {
        // W26 ended Sunday Jun 28: still open through Jul 5, closed from Jul 6.
        assertFalse(wc.isPastClosing("2026-W26", LocalDate(2026, 7, 5)))
        assertTrue(wc.isPastClosing("2026-W26", LocalDate(2026, 7, 6)))
        assertFalse(wc.isPastClosing(wc.weekId(), today))
    }
}
