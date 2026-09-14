package com.adamfoerster.mdhabits

import com.adamfoerster.mdhabits.core.newId
import com.adamfoerster.mdhabits.data.markdown.MarkdownPointsLedgerRepository
import com.adamfoerster.mdhabits.data.markdown.MarkdownTaskRepository
import com.adamfoerster.mdhabits.data.markdown.MarkdownWeekStore
import com.adamfoerster.mdhabits.data.markdown.MarkdownWeeklyReviewRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryPenaltyRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryPointsLedgerRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryTaskRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryThemeRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryValueRepository
import com.adamfoerster.mdhabits.domain.model.PointsEvent
import com.adamfoerster.mdhabits.domain.model.PointsSource
import com.adamfoerster.mdhabits.domain.model.Task
import com.adamfoerster.mdhabits.domain.model.WeeklyReview
import com.adamfoerster.mdhabits.domain.repository.PointsLedgerRepository
import com.adamfoerster.mdhabits.domain.usecase.ApplyPenaltyUseCase
import com.adamfoerster.mdhabits.domain.usecase.CompleteTaskUseCase
import com.adamfoerster.mdhabits.ui.screens.home.HomeViewModel
import com.adamfoerster.mdhabits.ui.screens.report.WeekReportViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The fixed test instant 2026-07-02 is the Thursday of ISO week 2026-W27. */
private const val THURSDAY = "2026-07-02T12:00:00Z"

private fun event(weekId: String, delta: Int, label: String, source: PointsSource = PointsSource.TASK) =
    PointsEvent(newId("L"), Instant.parse(THURSDAY), weekId, source, "ref", label, delta)

class MonthGridTest {

    private val weekCalculator = fixedWeekCalculator(THURSDAY)

    @Test
    fun theCurrentMonthIsLaidOutAsItsWeekRows() {
        val grid = weekCalculator.monthGrid(monthsAgo = 0)

        assertEquals(2026 to 7, grid.year to grid.month)
        // July 2026 starts on a Wednesday, so the first row is the week of Monday June 29th.
        assertEquals(LocalDate(2026, 6, 29), grid.weeks.first().days.first())
        assertEquals("2026-W27", grid.weeks.first().weekId)
        assertEquals(27, grid.weeks.first().weekNumber)
        // Every row is a full Monday..Sunday week, and the last one still touches July.
        assertTrue(grid.weeks.all { it.days.size == 7 })
        assertTrue(grid.weeks.last().days.any { it.monthNumber == 7 })
        assertEquals(LocalDate(2026, 7, 27), grid.weeks.last().days.first())
    }

    @Test
    fun monthsAgoWalksBackThroughTheCalendar() {
        val june = weekCalculator.monthGrid(monthsAgo = 1)
        val lastDecember = weekCalculator.monthGrid(monthsAgo = 7)

        assertEquals(2026 to 6, june.year to june.month)
        assertEquals(2025 to 12, lastDecember.year to lastDecember.month)
    }

    @Test
    fun onlyWeeksWithRecordsAreMarked() {
        val grid = weekCalculator.monthGrid(monthsAgo = 0, recordedWeekIds = setOf("2026-W27", "2026-W29"))

        assertEquals(
            listOf("2026-W27", "2026-W29"),
            grid.weeks.filter { it.hasRecords }.map { it.weekId },
        )
        assertTrue(grid.weeks.any { !it.hasRecords })
    }

    @Test
    fun weekNumbersCanBeReadBackFromAWeekId() {
        assertEquals(27, weekCalculator.weekNumberOf("2026-W27"))
        assertEquals(null, weekCalculator.weekNumberOf("not-a-week"))
    }
}

class RecordedWeeksTest {

    @Test
    fun theLedgerListsTheWeeksItHasRecordsFor() = runTest {
        val ledger = InMemoryPointsLedgerRepository()
        ledger.append(event("2026-W26", 5, "Concluída: Ler"))
        ledger.append(event("2026-W24", -3, "Penalidade: Doce", PointsSource.PENALTY))
        ledger.append(event("2026-W26", 5, "Concluída: Correr"))

        // Oldest first, one entry per week.
        assertEquals(listOf("2026-W24", "2026-W26"), ledger.observeRecordedWeekIds().first())
    }

    @Test
    fun everyWeekNoteInTheVaultCountsAsARecord() = runTest {
        val vault = FakeVaultFileSystem()
        val store = MarkdownWeekStore(vault)
        val tasks = MarkdownTaskRepository(vault, store)
        val ledger = MarkdownPointsLedgerRepository(store)
        tasks.upsert(Task("t-1", "Ler", 5))
        // A week that was only planned still has a note, and so does one that only holds points.
        tasks.setPlanned("2026-W26", listOf("t-1"))
        ledger.append(event("2026-W24", -3, "Hábito não cumprido: Ler", PointsSource.HABIT_MISS))

        assertEquals(listOf("2026-W24", "2026-W26"), ledger.observeRecordedWeekIds().first())
    }

    @Test
    fun theReportsBalanceIsTheOneTheWeekClosedWith() = runTest {
        val ledger = InMemoryPointsLedgerRepository()
        ledger.append(event("2026-W25", 10, "Concluída: Ler"))
        ledger.append(event("2026-W26", 5, "Concluída: Correr"))
        ledger.append(event("2026-W27", 100, "Concluída: Maratona"))

        // W26 closed at 15 points; the 100 earned later belongs to a week that hadn't happened yet.
        assertEquals(15, ledger.weeklyReport("2026-W26").endingBalance)
        assertEquals(115, ledger.weeklyReport("2026-W27").endingBalance)
    }
}

class WeekReportViewModelTest : MainDispatcherTest() {

    private fun viewModel(ledger: PointsLedgerRepository, store: MarkdownWeekStore) = WeekReportViewModel(
        ledgerRepository = ledger,
        reviewRepository = MarkdownWeeklyReviewRepository(store),
        weekCalculator = fixedWeekCalculator(THURSDAY),
    )

    @Test
    fun itShowsWhatThePickedWeekRecorded() = runTest {
        val vault = FakeVaultFileSystem()
        val store = MarkdownWeekStore(vault)
        val ledger = MarkdownPointsLedgerRepository(store)
        val reviews = MarkdownWeeklyReviewRepository(store)
        ledger.append(event("2026-W25", 8, "Concluída: Ler"))
        ledger.append(event("2026-W25", -4, "Hábito não cumprido: Correr", PointsSource.HABIT_MISS))
        ledger.append(event("2026-W25", -2, "Resgate: Cinema", PointsSource.REWARD))
        ledger.append(event("2026-W26", 50, "Concluída: Semana seguinte"))
        reviews.upsert(WeeklyReview("2026-W25", journal = "Semana difícil."))

        val vm = viewModel(ledger, store)
        vm.show("2026-W25")

        val state = vm.state.value
        assertFalse(state.loading)
        assertEquals(25, state.weekNumber)
        assertEquals(LocalDate(2026, 6, 15), state.range?.start)
        assertEquals(listOf("Concluída: Ler"), state.completed.map { it.label })
        assertEquals(listOf("Hábito não cumprido: Correr"), state.slips.map { it.label })
        assertEquals(listOf("Resgate: Cinema"), state.redemptions.map { it.label })
        assertEquals("Semana difícil.", state.journal)
        // The balance is the week's own, untouched by what the following weeks earned.
        assertEquals(2, state.report?.endingBalance)
        assertFalse(state.isEmpty)
    }

    @Test
    fun aWeekWithoutRecordsReportsItselfAsEmpty() = runTest {
        val vault = FakeVaultFileSystem()
        val store = MarkdownWeekStore(vault)
        val vm = viewModel(MarkdownPointsLedgerRepository(store), store)

        vm.show("2026-W20")

        assertTrue(vm.state.value.isEmpty)
        assertEquals("", vm.state.value.journal)
    }
}

class WeekPickerOnHomeTest : MainDispatcherTest() {

    @Test
    fun thePickerMarksOnlyRecordedWeeksAndStopsAtTheCalendarsEdges() = runTest {
        val tasks = InMemoryTaskRepository()
        val ledger = InMemoryPointsLedgerRepository()
        ledger.append(event("2026-W27", 5, "Concluída: Ler"))
        ledger.append(event("2026-W22", 5, "Concluída: Ler"))
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
        keepHot(vm.recordedWeekIds)

        // July: only the current week has records, and there is no month after it to walk into.
        val july = vm.weekPicker(monthsAgo = 0)
        assertEquals(2026 to 7, july.grid.year to july.grid.month)
        assertEquals(listOf("2026-W27"), july.grid.weeks.filter { it.hasRecords }.map { it.weekId })
        assertFalse(july.canGoForward)
        assertTrue(july.canGoBack)

        // May holds the oldest record, so the back arrow stops there.
        val may = vm.weekPicker(monthsAgo = 2)
        assertEquals(listOf("2026-W22"), may.grid.weeks.filter { it.hasRecords }.map { it.weekId })
        assertTrue(may.canGoForward)
        assertFalse(may.canGoBack)
    }
}
