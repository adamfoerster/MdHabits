package com.adamfoerster.mdhabits

import com.adamfoerster.mdhabits.data.repo.InMemoryHealthLogRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryPenaltyRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryPointsLedgerRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryTaskRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryThemeRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryValueRepository
import com.adamfoerster.mdhabits.domain.model.DailyHealth
import com.adamfoerster.mdhabits.domain.model.HealthComparison
import com.adamfoerster.mdhabits.domain.model.HealthGoal
import com.adamfoerster.mdhabits.domain.model.HealthMetric
import com.adamfoerster.mdhabits.domain.model.Penalty
import com.adamfoerster.mdhabits.domain.model.PointsSource
import com.adamfoerster.mdhabits.domain.model.PersonalValue
import com.adamfoerster.mdhabits.domain.model.Recurrence
import com.adamfoerster.mdhabits.domain.model.Task
import com.adamfoerster.mdhabits.domain.usecase.ApplyPenaltyUseCase
import com.adamfoerster.mdhabits.domain.usecase.CompleteTaskUseCase
import com.adamfoerster.mdhabits.domain.usecase.SyncHealthUseCase
import com.adamfoerster.mdhabits.ui.screens.home.HomeViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeViewModelTest : MainDispatcherTest() {

    private fun newViewModel(
        tasks: InMemoryTaskRepository = InMemoryTaskRepository(),
        ledger: InMemoryPointsLedgerRepository = InMemoryPointsLedgerRepository(),
        values: InMemoryValueRepository = InMemoryValueRepository(),
        penalties: InMemoryPenaltyRepository = InMemoryPenaltyRepository(),
        healthLog: InMemoryHealthLogRepository = InMemoryHealthLogRepository(),
        syncHealth: SyncHealthUseCase? = null,
    ): HomeViewModel {
        val theme = InMemoryThemeRepository()
        val wc = fixedWeekCalculator()
        return HomeViewModel(
            taskRepository = tasks,
            themeRepository = theme,
            ledgerRepository = ledger,
            penaltyRepository = penalties,
            valueRepository = values,
            completeTask = CompleteTaskUseCase(tasks, ledger),
            applyPenalty = ApplyPenaltyUseCase(ledger),
            weekCalculator = wc,
            syncMdPrayer = disabledMdPrayerSync(wc),
            penalizeMissedHabits = habitSweep(tasks, ledger),
            syncHealth = syncHealth ?: disabledHealthSync(wc),
            healthLogRepository = healthLog,
        )
    }

    @Test
    fun rowResolvesLinkedValueNames() = runTest {
        val tasks = InMemoryTaskRepository()
        val values = InMemoryValueRepository()
        values.upsert(PersonalValue("v1", "Moderation"))
        tasks.upsert(Task("t1", "Meditate", points = 5, linkedValueIds = listOf("v1")))

        val vm = newViewModel(tasks = tasks, values = values)
        keepHot(vm.state)

        val row = vm.state.value.allTasks.first { it.task.id == "t1" }
        assertEquals(listOf("Moderation"), row.linkedNames)
        assertFalse(row.completed)
    }

    @Test
    fun completingTaskCreditsBalanceAndMarksRowDone() = runTest {
        val tasks = InMemoryTaskRepository()
        val ledger = InMemoryPointsLedgerRepository()
        tasks.upsert(Task("t1", "Meditate", points = 5))

        val vm = newViewModel(tasks = tasks, ledger = ledger)
        keepHot(vm.state)

        vm.onToggleComplete(Task("t1", "Meditate", points = 5), completed = true)

        assertEquals(5, ledger.currentBalance())
        assertEquals(5, vm.state.value.balance)
        assertTrue(vm.state.value.allTasks.first { it.task.id == "t1" }.completed)
    }

    @Test
    fun applyingPenaltyDeductsBalance() = runTest {
        val tasks = InMemoryTaskRepository()
        val ledger = InMemoryPointsLedgerRepository()
        tasks.upsert(Task("t1", "Meditate", points = 20))

        val vm = newViewModel(tasks = tasks, ledger = ledger)
        keepHot(vm.state)

        vm.onToggleComplete(Task("t1", "Meditate", points = 20), completed = true)
        vm.onApplyPenalty(Penalty("p1", "Skipped", pointCost = 8))

        assertEquals(12, vm.state.value.balance)
    }

    @Test
    fun completedAdhocTaskDropsOffVisibleListButStaysCounted() = runTest {
        val tasks = InMemoryTaskRepository()
        val ledger = InMemoryPointsLedgerRepository()
        val adhoc = Task("t1", "Fix bike", points = 5, recurrence = Recurrence.ADHOC)
        tasks.upsert(adhoc)

        val vm = newViewModel(tasks = tasks, ledger = ledger)
        keepHot(vm.state)

        assertTrue(vm.state.value.visibleAdhocTasks.any { it.task.id == "t1" })

        vm.onToggleComplete(adhoc, completed = true)

        assertFalse(vm.state.value.visibleAdhocTasks.any { it.task.id == "t1" })
        assertTrue(vm.state.value.adhocTasks.first { it.task.id == "t1" }.completed)
        assertTrue(vm.state.value.allTasks.first { it.task.id == "t1" }.completed)
    }

    @Test
    fun adhocTaskCompletedInAnEarlierWeekNeverComesBack() = runTest {
        val tasks = InMemoryTaskRepository()
        val ledger = InMemoryPointsLedgerRepository()
        val wc = fixedWeekCalculator()
        val adhoc = Task("t1", "Fix bike", points = 5, recurrence = Recurrence.ADHOC)
        tasks.upsert(adhoc)
        CompleteTaskUseCase(tasks, ledger)(adhoc, wc.previousWeekId(), nowCompleted = true)

        val vm = newViewModel(tasks = tasks, ledger = ledger)
        keepHot(vm.state)

        assertFalse(vm.state.value.visibleAdhocTasks.any { it.task.id == "t1" })
        assertFalse(vm.state.value.adhocTasks.any { it.task.id == "t1" })
        assertFalse(vm.state.value.allTasks.any { it.task.id == "t1" })
    }

    @Test
    fun completingAnAdhocTaskTwiceCreditsItsPointsOnce() = runTest {
        val tasks = InMemoryTaskRepository()
        val ledger = InMemoryPointsLedgerRepository()
        val wc = fixedWeekCalculator()
        val adhoc = Task("t1", "Fix bike", points = 5, recurrence = Recurrence.ADHOC)
        tasks.upsert(adhoc)
        val complete = CompleteTaskUseCase(tasks, ledger)

        complete(adhoc, wc.previousWeekId(), nowCompleted = true)
        complete(adhoc, wc.weekId(), nowCompleted = true)

        assertEquals(5, ledger.currentBalance())
        assertTrue(tasks.observeInstances(wc.weekId()).first().isEmpty())
    }

    @Test
    fun aHealthGoalRowCarriesTodaysValue() = runTest {
        val tasks = InMemoryTaskRepository()
        val healthLog = InMemoryHealthLogRepository()
        val wc = fixedWeekCalculator()
        val goal = HealthGoal(HealthMetric.STEPS, HealthComparison.AT_LEAST, 8000.0)
        tasks.upsert(Task("t1", "Walk", points = 5, recurrence = Recurrence.DAILY, healthGoal = goal))
        tasks.upsert(Task("t2", "Read", points = 5, recurrence = Recurrence.DAILY))
        healthLog.record(wc.weekId(), listOf(DailyHealth(wc.today(), steps = 6240)))

        val vm = newViewModel(tasks = tasks, healthLog = healthLog)
        keepHot(vm.state)

        val rows = vm.state.value.todayTasks.associateBy { it.task.id }
        assertEquals(6240.0, rows.getValue("t1").healthToday)
        assertEquals(null, rows.getValue("t2").healthToday)
        // Health values alone don't start the week: the review call to action stays.
        assertTrue(vm.state.value.showReviewCta)
    }

    @Test
    fun openingHomeSyncsHealthBeforeChargingMissedHabits() = runTest {
        val tasks = InMemoryTaskRepository()
        val ledger = InMemoryPointsLedgerRepository()
        val healthLog = InMemoryHealthLogRepository()
        val wc = fixedWeekCalculator()
        val wednesday = LocalDate(2026, 7, 1)
        val goal = HealthGoal(HealthMetric.STEPS, HealthComparison.AT_LEAST, 8000.0)
        tasks.upsert(
            Task("h1", "Walk", points = 5, recurrence = Recurrence.HABIT, habitSince = wednesday, healthGoal = goal),
        )
        val settings = FakeAppSettings().apply { healthConnectEnabled = true }
        val sync = SyncHealthUseCase(
            settings, FakeHealthDataSource(listOf(DailyHealth(wednesday, steps = 9000))), healthLog, tasks, ledger,
            CompleteTaskUseCase(tasks, ledger, fixedClock(), TimeZone.UTC), wc,
        )

        newViewModel(tasks = tasks, ledger = ledger, healthLog = healthLog, syncHealth = sync)

        assertEquals(listOf(wednesday), tasks.observeInstances(wc.weekId()).first().single().completedDates)
        assertTrue(ledger.eventsForWeek(wc.weekId()).none { it.source == PointsSource.HABIT_MISS })
    }
}
