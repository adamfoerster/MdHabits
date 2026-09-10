package com.adamfoerster.mdhabits

import com.adamfoerster.mdhabits.core.datetime.WeekRange
import com.adamfoerster.mdhabits.data.repo.InMemoryPointsLedgerRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryTaskRepository
import com.adamfoerster.mdhabits.domain.model.Recurrence
import com.adamfoerster.mdhabits.domain.model.Task
import com.adamfoerster.mdhabits.domain.repository.MdPrayerRepository
import com.adamfoerster.mdhabits.domain.usecase.CompleteTaskUseCase
import com.adamfoerster.mdhabits.domain.usecase.SyncMdPrayerUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** An [MdPrayerRepository] fixed to always report [dates] as fully prayed, for any ref/range. */
private class FixedMdPrayerRepository(private val dates: Set<LocalDate>) : MdPrayerRepository {
    override suspend fun looksLikeMdPrayerVault(ref: String): Boolean = true
    override suspend fun completedDates(ref: String, range: WeekRange): Set<LocalDate> = dates
}

class SyncMdPrayerUseCaseTest {

    private val iso = "2026-09-02T10:00:00Z" // Wednesday of ISO week 2026-W36 (Mon 08-31..Sun 09-06).
    private val today = LocalDate(2026, 9, 2)

    private fun newSync(
        tasks: InMemoryTaskRepository,
        ledger: InMemoryPointsLedgerRepository,
        completedDates: Set<LocalDate>,
        enabled: Boolean = true,
        folderRef: String? = "prayer-folder",
        linkedTaskId: String? = "t1",
    ): Pair<SyncMdPrayerUseCase, FakeAppSettings> {
        val settings = FakeAppSettings().apply {
            mdPrayerEnabled = enabled
            mdPrayerFolderRef = folderRef
            mdPrayerLinkedTaskId = linkedTaskId
        }
        val sync = SyncMdPrayerUseCase(
            settings = settings,
            mdPrayer = FixedMdPrayerRepository(completedDates),
            tasks = tasks,
            completeTask = CompleteTaskUseCase(tasks, ledger, fixedClock(iso), TimeZone.UTC),
            weekCalculator = fixedWeekCalculator(iso),
        )
        return sync to settings
    }

    @Test
    fun disabledIntegrationDoesNothing() = runTest {
        val tasks = InMemoryTaskRepository()
        val ledger = InMemoryPointsLedgerRepository()
        tasks.upsert(Task("t1", "Oração matinal", points = 5, recurrence = Recurrence.DAILY))
        val (sync, _) = newSync(tasks, ledger, setOf(today), enabled = false)

        sync()

        assertEquals(0, ledger.currentBalance())
    }

    @Test
    fun dailyTaskIsMarkedDoneWhenMdPrayerShowsTodayComplete() = runTest {
        val tasks = InMemoryTaskRepository()
        val ledger = InMemoryPointsLedgerRepository()
        tasks.upsert(Task("t1", "Oração matinal", points = 5, recurrence = Recurrence.DAILY))
        val (sync, _) = newSync(tasks, ledger, completedDates = setOf(today))

        sync()

        val weekId = fixedWeekCalculator(iso).weekId()
        assertTrue(tasks.observeInstances(weekId).first().first { it.taskId == "t1" }.completed)
        assertEquals(5, ledger.currentBalance())
    }

    @Test
    fun callingSyncTwiceNeverDuplicatesTheLedgerEntry() = runTest {
        val tasks = InMemoryTaskRepository()
        val ledger = InMemoryPointsLedgerRepository()
        tasks.upsert(Task("t1", "Oração matinal", points = 5, recurrence = Recurrence.DAILY))
        val (sync, _) = newSync(tasks, ledger, completedDates = setOf(today))

        sync()
        sync()

        assertEquals(5, ledger.currentBalance())
    }

    @Test
    fun dailyTaskIsNotMarkedWhenTodayIsMissingFromMdPrayer() = runTest {
        val tasks = InMemoryTaskRepository()
        val ledger = InMemoryPointsLedgerRepository()
        tasks.upsert(Task("t1", "Oração matinal", points = 5, recurrence = Recurrence.DAILY))
        // mdPrayer only has yesterday's day complete, not today.
        val (sync, _) = newSync(tasks, ledger, completedDates = setOf(LocalDate(2026, 9, 1)))

        sync()

        assertEquals(0, ledger.currentBalance())
    }

    @Test
    fun weeklyTaskIsMarkedDoneWhenAnyDayThisWeekIsComplete() = runTest {
        val tasks = InMemoryTaskRepository()
        val ledger = InMemoryPointsLedgerRepository()
        tasks.upsert(Task("t1", "Oração da semana", points = 10, recurrence = Recurrence.WEEKLY))
        // Monday of the same ISO week, not today.
        val (sync, _) = newSync(tasks, ledger, completedDates = setOf(LocalDate(2026, 8, 31)))

        sync()

        val weekId = fixedWeekCalculator(iso).weekId()
        assertTrue(tasks.observeInstances(weekId).first().first { it.taskId == "t1" }.completed)
        assertEquals(10, ledger.currentBalance())
    }

    @Test
    fun manuallyUncheckingAfterSyncGetsMarkedDoneAgainOnTheNextSync() = runTest {
        val tasks = InMemoryTaskRepository()
        val ledger = InMemoryPointsLedgerRepository()
        tasks.upsert(Task("t1", "Oração matinal", points = 5, recurrence = Recurrence.DAILY))
        val (sync, _) = newSync(tasks, ledger, completedDates = setOf(today))
        sync()
        val weekId = fixedWeekCalculator(iso).weekId()
        tasks.setCompleted("t1", weekId, completed = false, on = today)

        sync()

        assertTrue(tasks.observeInstances(weekId).first().first { it.taskId == "t1" }.completed)
        // Marked done twice: the sync only ever adds, documenting the accepted "mark-only" trade-off.
        assertEquals(10, ledger.currentBalance())
    }

    @Test
    fun inactiveTaskIsIgnored() = runTest {
        val tasks = InMemoryTaskRepository()
        val ledger = InMemoryPointsLedgerRepository()
        tasks.upsert(Task("t1", "Oração matinal", points = 5, recurrence = Recurrence.DAILY, active = false))
        val (sync, _) = newSync(tasks, ledger, completedDates = setOf(today))

        sync()

        assertEquals(0, ledger.currentBalance())
    }

    @Test
    fun noLinkedTaskIsIgnored() = runTest {
        val tasks = InMemoryTaskRepository()
        val ledger = InMemoryPointsLedgerRepository()
        tasks.upsert(Task("t1", "Oração matinal", points = 5, recurrence = Recurrence.DAILY))
        val (sync, _) = newSync(tasks, ledger, completedDates = setOf(today), linkedTaskId = null)

        sync()

        assertEquals(0, ledger.currentBalance())
    }
}
