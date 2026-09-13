package com.adamfoerster.mdhabits

import com.adamfoerster.mdhabits.data.markdown.MarkdownCodecs
import com.adamfoerster.mdhabits.data.markdown.MarkdownPenaltyRepository
import com.adamfoerster.mdhabits.data.markdown.MarkdownPointsLedgerRepository
import com.adamfoerster.mdhabits.data.markdown.MarkdownTaskRepository
import com.adamfoerster.mdhabits.data.markdown.MarkdownThemeRepository
import com.adamfoerster.mdhabits.data.markdown.MarkdownValueRepository
import com.adamfoerster.mdhabits.data.markdown.MarkdownWeekStore
import com.adamfoerster.mdhabits.data.markdown.WeekNote
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
import com.adamfoerster.mdhabits.domain.model.stampHabitSince
import com.adamfoerster.mdhabits.domain.repository.PointsLedgerRepository
import com.adamfoerster.mdhabits.domain.repository.TaskRepository
import com.adamfoerster.mdhabits.domain.usecase.ApplyPenaltyUseCase
import com.adamfoerster.mdhabits.domain.usecase.CompleteTaskUseCase
import com.adamfoerster.mdhabits.domain.usecase.PenalizeMissedHabitsUseCase
import com.adamfoerster.mdhabits.domain.usecase.PenalizeMissedHabitsUseCase.Companion.missRefId
import com.adamfoerster.mdhabits.ui.screens.home.HomeViewModel
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The fixed test instant 2026-07-02 is the Thursday of ISO week 2026-W27 (Monday 2026-06-29). */
private const val THURSDAY = "2026-07-02T12:00:00Z"
private val thisMonday = LocalDate(2026, 6, 29)
private val today = LocalDate(2026, 7, 2)

private fun habit(
    points: Int = 5,
    since: LocalDate? = thisMonday,
    active: Boolean = true,
) = Task("h1", "Ler 10 páginas", points, Recurrence.HABIT, habitSince = since, active = active)

class HabitCodecTest {

    @Test
    fun habitTaskRoundTripsWithItsStartDay() {
        val task = habit()
        assertEquals(task, MarkdownCodecs.decodeTask(MarkdownCodecs.encodeTask(task)))
    }

    @Test
    fun weekNoteKeepsEveryDayAHabitWasCompletedOn() {
        val days = listOf(LocalDate(2026, 6, 29), LocalDate(2026, 7, 1))
        val note = WeekNote(
            weekId = "2026-W27",
            instances = listOf(
                TaskInstance("h1", "2026-W27")
                    .completing(true, days.first())
                    .completing(true, days.last()),
            ),
        )
        val decoded = MarkdownCodecs.decodeWeekNote(MarkdownCodecs.encodeWeekNote(note))
        assertEquals(days, decoded?.instances?.single()?.completedDates)
    }

    @Test
    fun preHabitNotesFallBackToTheirSingleCompletionStamp() {
        // Notes written before 0.11.0 have no per-day list, only the one completedOn stamp.
        val current = MarkdownCodecs.encodeWeekNote(
            WeekNote(
                weekId = "2026-W27",
                instances = listOf(
                    TaskInstance("t1", "2026-W27", completed = true, completedOn = thisMonday),
                ),
            ),
        )
        val legacy = current.replace(Regex(""",\s*"dates":\s*\[[^]]*]"""), "")
        assertTrue(legacy != current, "the encoder stopped writing a per-day list")
        val decoded = MarkdownCodecs.decodeWeekNote(legacy)
        assertEquals(listOf(thisMonday), decoded?.instances?.single()?.completedDates)
    }

    @Test
    fun theStartDayIsStampedOnlyWhileTheTaskIsAHabit() {
        val stamped = habit(since = null).stampHabitSince(today)
        assertEquals(today, stamped.habitSince)
        // Re-saving keeps the original day; leaving the habit frequency drops the stamp.
        assertEquals(today, stamped.stampHabitSince(LocalDate(2026, 7, 9)).habitSince)
        assertNull(stamped.copy(recurrence = Recurrence.DAILY).stampHabitSince(today).habitSince)
    }
}

class MissedHabitSweepTest {

    private val weekCalculator = fixedWeekCalculator(THURSDAY)

    private fun sweep(
        tasks: TaskRepository,
        ledger: PointsLedgerRepository,
        weeksBack: Int = 4,
    ) = PenalizeMissedHabitsUseCase(tasks, ledger, weekCalculator, fixedClock(THURSDAY), weeksBack)

    @Test
    fun everyDayThatEndedWithoutTheHabitIsCharged() = runTest {
        val tasks = InMemoryTaskRepository()
        val ledger = InMemoryPointsLedgerRepository()
        tasks.upsert(habit())

        sweep(tasks, ledger)()

        // Monday, Tuesday and Wednesday ended undone; Thursday is still running.
        assertEquals(-15, ledger.currentBalance())
        val events = ledger.eventsForWeek("2026-W27")
        assertEquals(
            listOf(thisMonday, LocalDate(2026, 6, 30), LocalDate(2026, 7, 1)).map { missRefId("h1", it) },
            events.map { it.refId },
        )
        assertTrue(events.all { it.source == PointsSource.HABIT_MISS && it.delta == -5 })
    }

    @Test
    fun daysTheHabitWasCompletedOnAreNotCharged() = runTest {
        val tasks = InMemoryTaskRepository()
        val ledger = InMemoryPointsLedgerRepository()
        tasks.upsert(habit())
        tasks.setCompleted("h1", "2026-W27", completed = true, on = LocalDate(2026, 6, 30))

        sweep(tasks, ledger)()

        assertEquals(-10, ledger.currentBalance())
    }

    @Test
    fun aDayTakenBackIsChargedAgain() = runTest {
        val tasks = InMemoryTaskRepository()
        val ledger = InMemoryPointsLedgerRepository()
        tasks.upsert(habit())
        tasks.setCompleted("h1", "2026-W27", completed = true, on = thisMonday)
        tasks.setCompleted("h1", "2026-W27", completed = true, on = LocalDate(2026, 6, 30))
        // Un-checking Tuesday must not erase Monday's completion.
        tasks.setCompleted("h1", "2026-W27", completed = false, on = LocalDate(2026, 6, 30))

        sweep(tasks, ledger)()

        assertEquals(
            listOf(LocalDate(2026, 6, 30), LocalDate(2026, 7, 1)).map { missRefId("h1", it) },
            ledger.eventsForWeek("2026-W27").map { it.refId },
        )
    }

    @Test
    fun sweepingTwiceChargesEachDayOnce() = runTest {
        val tasks = InMemoryTaskRepository()
        val ledger = InMemoryPointsLedgerRepository()
        tasks.upsert(habit())

        sweep(tasks, ledger)()
        sweep(tasks, ledger)()

        assertEquals(-15, ledger.currentBalance())
    }

    @Test
    fun daysBeforeTheTaskBecameAHabitAreNotCharged() = runTest {
        val tasks = InMemoryTaskRepository()
        val ledger = InMemoryPointsLedgerRepository()
        // Added today: nothing behind it may be billed, and today isn't over yet.
        tasks.upsert(habit(since = today))

        sweep(tasks, ledger)()

        assertEquals(0, ledger.currentBalance())
    }

    @Test
    fun theSweepReachesFourWeeksBackAndNoFurther() = runTest {
        val tasks = InMemoryTaskRepository()
        val ledger = InMemoryPointsLedgerRepository()
        tasks.upsert(habit(since = LocalDate(2026, 1, 1)))

        sweep(tasks, ledger)()

        // Monday of the oldest swept week through yesterday: 24 days at 5 points.
        val oldestMonday = LocalDate(2026, 6, 8)
        assertEquals(-120, ledger.currentBalance())
        val charged = (0..3).flatMap { week ->
            ledger.eventsForWeek(weekCalculator.weekId(oldestMonday.plus(week * 7, DateTimeUnit.DAY)))
        }
        assertEquals(24, charged.size)
        assertEquals(missRefId("h1", oldestMonday), charged.first().refId)
        assertEquals(missRefId("h1", LocalDate(2026, 7, 1)), charged.last().refId)
        // The week before the window is left alone.
        assertTrue(ledger.eventsForWeek(weekCalculator.weekId(LocalDate(2026, 6, 1))).isEmpty())
    }

    @Test
    fun pausedHabitsAndOtherTaskTypesAreNeverCharged() = runTest {
        val tasks = InMemoryTaskRepository()
        val ledger = InMemoryPointsLedgerRepository()
        tasks.upsert(habit(active = false))
        tasks.upsert(Task("d1", "Meditar", 5, Recurrence.DAILY))
        tasks.upsert(Task("w1", "Ler o jornal", 5, Recurrence.WEEKLY))

        sweep(tasks, ledger)()

        assertEquals(0, ledger.currentBalance())
    }

    @Test
    fun chargedDaysCountAsTheWeeksSlips() = runTest {
        val tasks = InMemoryTaskRepository()
        val ledger = InMemoryPointsLedgerRepository()
        tasks.upsert(habit())

        sweep(tasks, ledger)()

        val report = ledger.weeklyReport("2026-W27")
        assertEquals(3, report.penalties.size)
        assertEquals(-15, report.net)
    }
}

class HabitOnHomeTest : MainDispatcherTest() {

    @Test
    fun openingHomeChargesTheMissedDaysAndListsTheHabitForToday() = runTest {
        val tasks = InMemoryTaskRepository()
        val ledger = InMemoryPointsLedgerRepository()
        val theHabit = habit(since = LocalDate(2026, 6, 30))
        tasks.upsert(theHabit)
        val weekCalculator = fixedWeekCalculator(THURSDAY)

        val vm = HomeViewModel(
            taskRepository = tasks,
            themeRepository = InMemoryThemeRepository(),
            ledgerRepository = ledger,
            penaltyRepository = InMemoryPenaltyRepository(),
            valueRepository = InMemoryValueRepository(),
            completeTask = CompleteTaskUseCase(tasks, ledger, fixedClock(THURSDAY), TimeZone.UTC),
            applyPenalty = ApplyPenaltyUseCase(ledger, fixedClock(THURSDAY)),
            weekCalculator = weekCalculator,
            syncMdPrayer = disabledMdPrayerSync(weekCalculator),
            penalizeMissedHabits = habitSweep(tasks, ledger, THURSDAY),
        )
        keepHot(vm.state)

        // Tuesday and Wednesday were missed; a habit is due every day, so it sits in "today".
        assertEquals(listOf("Ler 10 páginas"), vm.state.value.todayTasks.map { it.task.title })
        assertEquals(-10, ledger.currentBalance())
        assertEquals(-10, vm.state.value.balance)

        // Doing it today earns the points as any other task does.
        vm.onToggleComplete(theHabit, completed = true)
        assertEquals(-5, vm.state.value.balance)
        assertTrue(vm.state.value.todayTasks.single().completed)
    }

    @Test
    fun chargingMissedDaysDoesNotCountAsStartingTheWeek() = runTest {
        val vault = FakeVaultFileSystem()
        val store = MarkdownWeekStore(vault)
        val tasks = MarkdownTaskRepository(vault, store)
        val ledger = MarkdownPointsLedgerRepository(store)
        tasks.upsert(habit(since = LocalDate(2026, 6, 30)))
        val weekCalculator = fixedWeekCalculator(THURSDAY)

        val vm = HomeViewModel(
            taskRepository = tasks,
            themeRepository = MarkdownThemeRepository(vault),
            ledgerRepository = ledger,
            penaltyRepository = MarkdownPenaltyRepository(vault),
            valueRepository = MarkdownValueRepository(vault),
            completeTask = CompleteTaskUseCase(tasks, ledger, fixedClock(THURSDAY), TimeZone.UTC),
            applyPenalty = ApplyPenaltyUseCase(ledger, fixedClock(THURSDAY)),
            weekCalculator = weekCalculator,
            syncMdPrayer = disabledMdPrayerSync(weekCalculator),
            penalizeMissedHabits = habitSweep(tasks, ledger, THURSDAY),
        )
        keepHot(vm.state)

        // The charge lands in the week note, but the user hasn't planned or done anything in the
        // week yet, so the weekly-review call to action must stay.
        assertEquals(-10, vm.state.value.balance)
        assertTrue("weeks/2026-W27.md" in vault.files)
        assertTrue(vm.state.value.showReviewCta)
    }
}
