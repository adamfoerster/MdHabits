package com.adamfoerster.mdhabits

import com.adamfoerster.mdhabits.core.i18n.Lang
import com.adamfoerster.mdhabits.core.i18n.LocaleController
import com.adamfoerster.mdhabits.core.i18n.stringsFor
import com.adamfoerster.mdhabits.data.markdown.MarkdownCodecs
import com.adamfoerster.mdhabits.data.markdown.MarkdownHealthLogRepository
import com.adamfoerster.mdhabits.data.markdown.MarkdownTaskRepository
import com.adamfoerster.mdhabits.data.markdown.MarkdownWeekStore
import com.adamfoerster.mdhabits.data.markdown.WeekNote
import com.adamfoerster.mdhabits.data.repo.InMemoryHealthLogRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryPointsLedgerRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryTaskRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryThemeRepository
import com.adamfoerster.mdhabits.domain.model.DailyHealth
import com.adamfoerster.mdhabits.domain.model.HealthComparison
import com.adamfoerster.mdhabits.domain.model.HealthGoal
import com.adamfoerster.mdhabits.domain.model.HealthMetric
import com.adamfoerster.mdhabits.domain.model.PointsEvent
import com.adamfoerster.mdhabits.domain.model.PointsSource
import com.adamfoerster.mdhabits.domain.model.Recurrence
import com.adamfoerster.mdhabits.domain.model.Task
import com.adamfoerster.mdhabits.domain.repository.HealthAvailability
import com.adamfoerster.mdhabits.domain.usecase.CompleteTaskUseCase
import com.adamfoerster.mdhabits.domain.usecase.PenalizeMissedHabitsUseCase
import com.adamfoerster.mdhabits.domain.usecase.SyncHealthUseCase
import com.adamfoerster.mdhabits.storage.VaultMigrator
import com.adamfoerster.mdhabits.ui.components.formTarget
import com.adamfoerster.mdhabits.ui.components.formatHealthValue
import com.adamfoerster.mdhabits.ui.components.healthProgressLabel
import com.adamfoerster.mdhabits.ui.components.parseHealthGoal
import com.adamfoerster.mdhabits.ui.screens.settings.HealthEnableResult
import com.adamfoerster.mdhabits.ui.screens.settings.SettingsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val THURSDAY = "2026-07-02T12:00:00Z" // ISO week 2026-W27: Mon 06-29 .. Sun 07-05.
private val MON = LocalDate(2026, 6, 29)
private val TUE = LocalDate(2026, 6, 30)
private val WED = LocalDate(2026, 7, 1)
private val THU = LocalDate(2026, 7, 2)

private val STEPS_8000 = HealthGoal(HealthMetric.STEPS, HealthComparison.AT_LEAST, 8000.0)

private fun steps(date: LocalDate, count: Long) = DailyHealth(date, steps = count)

/** Everything a [SyncHealthUseCase] test needs, wired to the same fixed instant. */
private class SyncFixture(
    days: List<DailyHealth> = emptyList(),
    val iso: String = THURSDAY,
    enabled: Boolean = true,
    granted: Boolean = true,
) {
    val tasks = InMemoryTaskRepository()
    val ledger = InMemoryPointsLedgerRepository()
    val healthLog = InMemoryHealthLogRepository()
    val source = FakeHealthDataSource(days, granted = granted)
    val settings = FakeAppSettings().apply { healthConnectEnabled = enabled }
    val weekCalculator = fixedWeekCalculator(iso)
    val sync = SyncHealthUseCase(
        settings = settings,
        source = source,
        healthLog = healthLog,
        tasks = tasks,
        ledger = ledger,
        completeTask = CompleteTaskUseCase(tasks, ledger, fixedClock(iso), TimeZone.UTC),
        weekCalculator = weekCalculator,
    )

    suspend fun completedDates(taskId: String, weekId: String = weekCalculator.weekId()): List<LocalDate> =
        tasks.observeInstances(weekId).first().find { it.taskId == taskId }?.completedDates.orEmpty()

    suspend fun completed(taskId: String, weekId: String = weekCalculator.weekId()): Boolean =
        tasks.observeInstances(weekId).first().find { it.taskId == taskId }?.completed == true
}

class SyncHealthUseCaseTest {

    @Test
    fun disabledIntegrationReadsAndCompletesNothing() = runTest {
        val f = SyncFixture(listOf(steps(THU, 9000)), enabled = false)
        f.tasks.upsert(Task("t1", "Walk", 5, Recurrence.DAILY, healthGoal = STEPS_8000))

        f.sync()

        assertEquals(emptyList(), f.completedDates("t1"))
        assertEquals(emptyList(), f.healthLog.observeWeek(f.weekCalculator.weekId()).first())
    }

    @Test
    fun withoutPermissionNothingHappens() = runTest {
        val f = SyncFixture(listOf(steps(THU, 9000)), granted = false)
        f.tasks.upsert(Task("t1", "Walk", 5, Recurrence.DAILY, healthGoal = STEPS_8000))

        f.sync()

        assertEquals(emptyList(), f.completedDates("t1"))
        assertEquals(0, f.ledger.currentBalance())
    }

    @Test
    fun aFailingHealthStoreNeverPropagates() = runTest {
        val f = SyncFixture(listOf(steps(THU, 9000)))
        f.source.failing = true
        f.tasks.upsert(Task("t1", "Walk", 5, Recurrence.DAILY, healthGoal = STEPS_8000))

        f.sync() // must not throw

        assertEquals(emptyList(), f.completedDates("t1"))
    }

    @Test
    fun theWeekSoFarIsRecordedIntoTheLog() = runTest {
        val f = SyncFixture(
            listOf(
                steps(MON, 4000),
                DailyHealth(WED, sleepMinutes = 430, weightKg = 79.4),
                steps(THU, 1200),
            ),
        )

        f.sync()

        // Tuesday had nothing recorded, so it is left out.
        assertEquals(
            listOf(steps(MON, 4000), DailyHealth(WED, sleepMinutes = 430, weightKg = 79.4), steps(THU, 1200)),
            f.healthLog.observeWeek("2026-W27").first(),
        )
    }

    @Test
    fun unchangedDataIsNotRewritten() = runTest {
        val f = SyncFixture(listOf(steps(THU, 1200)))

        f.sync()
        f.sync()

        assertEquals(1, f.healthLog.writes)
    }

    @Test
    fun aDailyGoalCompletesTodayAndYesterdayButNotEarlierDays() = runTest {
        val f = SyncFixture(listOf(steps(MON, 9000), steps(TUE, 3000), steps(WED, 8000), steps(THU, 12000)))
        f.tasks.upsert(Task("t1", "Walk", 5, Recurrence.DAILY, healthGoal = STEPS_8000))

        f.sync()

        assertEquals(listOf(WED, THU), f.completedDates("t1"))
        assertEquals(10, f.ledger.currentBalance())
    }

    @Test
    fun runningTwiceNeverCreditsTwice() = runTest {
        val f = SyncFixture(listOf(steps(WED, 9000), steps(THU, 9000)))
        f.tasks.upsert(Task("t1", "Walk", 5, Recurrence.DAILY, healthGoal = STEPS_8000))

        f.sync()
        f.sync()

        assertEquals(10, f.ledger.currentBalance())
    }

    @Test
    fun aDayBelowTheGoalOrWithoutDataIsNotCompleted() = runTest {
        val f = SyncFixture(listOf(steps(THU, 7999)))
        f.tasks.upsert(Task("t1", "Walk", 5, Recurrence.DAILY, healthGoal = STEPS_8000))
        val sleep = HealthGoal(HealthMetric.SLEEP, HealthComparison.AT_LEAST, 420.0)
        f.tasks.upsert(Task("t2", "Sleep well", 5, Recurrence.DAILY, healthGoal = sleep))

        f.sync()

        assertEquals(emptyList(), f.completedDates("t1"))
        assertEquals(emptyList(), f.completedDates("t2"))
    }

    @Test
    fun anAtMostWeightGoalCompletesAtOrBelowTheTarget() = runTest {
        val f = SyncFixture(listOf(DailyHealth(WED, weightKg = 80.4), DailyHealth(THU, weightKg = 80.0)))
        val weight = HealthGoal(HealthMetric.WEIGHT, HealthComparison.AT_MOST, 80.0)
        f.tasks.upsert(Task("t1", "Weigh in", 3, Recurrence.DAILY, healthGoal = weight))

        f.sync()

        assertEquals(listOf(THU), f.completedDates("t1"))
    }

    @Test
    fun aDaysOfWeekTaskIsOnlyCompletedOnItsDays() = runTest {
        val f = SyncFixture(listOf(steps(WED, 9000), steps(THU, 9000)))
        f.tasks.upsert(
            Task(
                "t1", "Long walk", 5, Recurrence.DAYS_OF_WEEK,
                daysOfWeek = listOf(DayOfWeek.THURSDAY), healthGoal = STEPS_8000,
            ),
        )

        f.sync()

        assertEquals(listOf(THU), f.completedDates("t1"))
    }

    @Test
    fun aHabitIsNeverCompletedBeforeItsStartDay() = runTest {
        val f = SyncFixture(listOf(steps(WED, 9000), steps(THU, 9000)))
        f.tasks.upsert(Task("h1", "Walk", 5, Recurrence.HABIT, habitSince = THU, healthGoal = STEPS_8000))

        f.sync()

        assertEquals(listOf(THU), f.completedDates("h1"))
    }

    @Test
    fun aHabitDayAlreadyChargedAsMissedIsLeftAlone() = runTest {
        val f = SyncFixture(listOf(steps(WED, 9000)))
        f.tasks.upsert(Task("h1", "Walk", 5, Recurrence.HABIT, habitSince = MON, healthGoal = STEPS_8000))
        f.ledger.append(
            PointsEvent(
                "L1", Instant.parse(THURSDAY), "2026-W27", PointsSource.HABIT_MISS,
                PenalizeMissedHabitsUseCase.missRefId("h1", WED), "miss", -5,
            ),
        )

        f.sync()

        assertEquals(emptyList(), f.completedDates("h1"))
        assertEquals(-5, f.ledger.currentBalance())
    }

    @Test
    fun syncingBeforeTheSweepKeepsAMetHabitDayFromBeingCharged() = runTest {
        val f = SyncFixture(listOf(steps(WED, 9000)))
        f.tasks.upsert(Task("h1", "Walk", 5, Recurrence.HABIT, habitSince = WED, healthGoal = STEPS_8000))

        f.sync()
        PenalizeMissedHabitsUseCase(f.tasks, f.ledger, f.weekCalculator, fixedClock(THURSDAY))()

        val misses = f.ledger.eventsForWeek("2026-W27").filter { it.source == PointsSource.HABIT_MISS }
        assertEquals(emptyList(), misses)
        assertEquals(5, f.ledger.currentBalance())
    }

    @Test
    fun aWeeklyTaskCompletesOnceWhenAnyDayOfTheWeekMetTheGoal() = runTest {
        val f = SyncFixture(listOf(steps(MON, 15000)))
        f.tasks.upsert(Task("w1", "Big walk", 10, Recurrence.WEEKLY, healthGoal = STEPS_8000))

        f.sync()
        f.sync()

        assertTrue(f.completed("w1"))
        assertEquals(10, f.ledger.currentBalance())
    }

    @Test
    fun anAdhocTaskCompletesOnceForGood() = runTest {
        val f = SyncFixture(listOf(DailyHealth(THU, weightKg = 79.0)))
        val weight = HealthGoal(HealthMetric.WEIGHT, HealthComparison.AT_MOST, 80.0)
        f.tasks.upsert(Task("a1", "Reach 80 kg", 50, Recurrence.ADHOC, healthGoal = weight))

        f.sync()
        f.sync()

        assertTrue(f.completed("a1"))
        assertEquals(50, f.ledger.currentBalance())
    }

    @Test
    fun onMondayYesterdayIsReadAndCompletedInLastWeek() = runTest {
        val sunday = LocalDate(2026, 7, 5)
        val monday = LocalDate(2026, 7, 6)
        val f = SyncFixture(listOf(steps(sunday, 9000), steps(monday, 100)), iso = "2026-07-06T09:00:00Z")
        f.tasks.upsert(Task("t1", "Walk", 5, Recurrence.DAILY, healthGoal = STEPS_8000))

        f.sync()

        assertEquals(listOf(sunday), f.completedDates("t1", "2026-W27"))
        assertEquals(listOf(steps(sunday, 9000)), f.healthLog.observeWeek("2026-W27").first())
        assertEquals(listOf(steps(monday, 100)), f.healthLog.observeWeek("2026-W28").first())
    }

    @Test
    fun tasksWithoutAGoalAreUntouched() = runTest {
        val f = SyncFixture(listOf(steps(THU, 20000)))
        f.tasks.upsert(Task("t1", "Read", 5, Recurrence.DAILY))

        f.sync()

        assertEquals(emptyList(), f.completedDates("t1"))
    }
}

class HealthGoalTest {

    @Test
    fun goalComparesTheMetricValue() {
        assertTrue(STEPS_8000.isMetBy(steps(THU, 8000)))
        assertFalse(STEPS_8000.isMetBy(steps(THU, 7999)))
        assertFalse(STEPS_8000.isMetBy(DailyHealth(THU)))
        val ceiling = HealthGoal(HealthMetric.WEIGHT, HealthComparison.AT_MOST, 80.0)
        assertTrue(ceiling.isMetBy(DailyHealth(THU, weightKg = 79.9)))
        assertFalse(ceiling.isMetBy(DailyHealth(THU, weightKg = 80.1)))
    }
}

class HealthCodecTest {

    @Test
    fun aTaskHealthGoalRoundTrips() {
        val task = Task("t1", "Walk", 5, Recurrence.DAILY, healthGoal = STEPS_8000)

        val text = MarkdownCodecs.encodeTask(task)

        assertTrue("health_goal: {\"metric\":\"STEPS\",\"comparison\":\"AT_LEAST\",\"target\":8000}" in text, text)
        assertEquals(task, MarkdownCodecs.decodeTask(text))
    }

    @Test
    fun aTaskWithoutAGoalWritesNoField() {
        val text = MarkdownCodecs.encodeTask(Task("t1", "Read", 5))

        assertFalse("health_goal" in text)
        assertNull(MarkdownCodecs.decodeTask(text)?.healthGoal)
    }

    @Test
    fun aMalformedGoalIsDroppedNotFatal() {
        val text = """
            ---
            id: "t1"
            title: "Walk"
            points: 5
            health_goal: {"metric":"FLOORS","comparison":"AT_LEAST","target":3}
            ---
        """.trimIndent()

        val task = MarkdownCodecs.decodeTask(text)

        assertEquals("Walk", task?.title)
        assertNull(task?.healthGoal)
    }

    @Test
    fun weekNoteHealthRoundTripsAndLeavesMissingValuesOut() {
        val note = WeekNote(
            weekId = "2026-W27",
            health = listOf(steps(MON, 8123), DailyHealth(TUE, sleepMinutes = 432, weightKg = 79.4)),
        )

        val text = MarkdownCodecs.encodeWeekNote(note)

        assertTrue(
            "## Health\n\n- 2026-06-29 | steps: 8123\n- 2026-06-30 | sleep_min: 432 | weight_kg: 79.4" in text,
            text,
        )
        assertEquals(note, MarkdownCodecs.decodeWeekNote(text))
    }

    @Test
    fun aWeekNoteWithoutHealthDecodesAnEmptyList() {
        val text = MarkdownCodecs.encodeWeekNote(WeekNote("2026-W27"))

        assertFalse("health" in text)
        assertEquals(emptyList(), MarkdownCodecs.decodeWeekNote(text)?.health)
    }
}

class MarkdownHealthLogTest {

    @Test
    fun recordingMergesDaysAndSkipsUnchangedWrites() = runTest {
        val vault = FakeVaultFileSystem()
        val log = MarkdownHealthLogRepository(MarkdownWeekStore(vault, fixedWeekCalculator()))

        log.record("2026-W27", listOf(steps(MON, 1000), steps(TUE, 2000)))
        log.record("2026-W27", listOf(steps(TUE, 2500), steps(WED, 3000)))
        assertEquals(
            listOf(steps(MON, 1000), steps(TUE, 2500), steps(WED, 3000)),
            log.observeWeek("2026-W27").first(),
        )

        // An identical re-read must not touch the note again.
        vault.files["weeks/2026-W27.md"] = "untouched"
        log.record("2026-W27", listOf(steps(WED, 3000)))
        assertEquals("untouched", vault.files["weeks/2026-W27.md"])
    }

    @Test
    fun healthDataAloneDoesNotStartTheWeek() = runTest {
        val vault = FakeVaultFileSystem()
        val store = MarkdownWeekStore(vault, fixedWeekCalculator())
        val tasks = MarkdownTaskRepository(vault, store)

        MarkdownHealthLogRepository(store).record("2026-W27", listOf(steps(THU, 4000)))

        assertFalse(tasks.observeWeekStarted("2026-W27").first())
    }
}

class HealthLabelsTest {

    @Test
    fun sleepIsTypedInHoursAndStoredInMinutes() {
        val goal = parseHealthGoal(HealthMetric.SLEEP, HealthComparison.AT_LEAST, "7,5")

        assertEquals(HealthGoal(HealthMetric.SLEEP, HealthComparison.AT_LEAST, 450.0), goal)
        assertEquals("7.5", formTarget(goal!!))
    }

    @Test
    fun aMissingOrZeroTargetIsNoGoal() {
        assertNull(parseHealthGoal(HealthMetric.STEPS, HealthComparison.AT_LEAST, ""))
        assertNull(parseHealthGoal(HealthMetric.STEPS, HealthComparison.AT_LEAST, "0"))
        assertEquals("8000", formTarget(STEPS_8000))
    }

    @Test
    fun valuesAreFormattedInTheirUnit() {
        assertEquals("8000", formatHealthValue(HealthMetric.STEPS, 8000.0))
        assertEquals("7h12", formatHealthValue(HealthMetric.SLEEP, 432.0))
        assertEquals("7h", formatHealthValue(HealthMetric.SLEEP, 420.0))
        assertEquals("7h05", formatHealthValue(HealthMetric.SLEEP, 425.0))
        assertEquals("79.4 kg", formatHealthValue(HealthMetric.WEIGHT, 79.44))
        assertEquals("80 kg", formatHealthValue(HealthMetric.WEIGHT, 80.0))
    }

    @Test
    fun progressShowsTodayAgainstTheGoal() {
        val pt = stringsFor(Lang.PT)
        assertEquals("Passos: 6240 · ≥ 8000", pt.healthProgressLabel(STEPS_8000, 6240.0))
        assertEquals("Passos: — · ≥ 8000", pt.healthProgressLabel(STEPS_8000, null))
    }
}

class SettingsHealthTest : MainDispatcherTest() {

    private fun newViewModel(
        settings: FakeAppSettings,
        source: FakeHealthDataSource,
        tasks: InMemoryTaskRepository = InMemoryTaskRepository(),
        ledger: InMemoryPointsLedgerRepository = InMemoryPointsLedgerRepository(),
    ) = SettingsViewModel(
        settings, FakeVaultPicker(), VaultMigrator(FakeVaultFileSystem(), settings),
        InMemoryThemeRepository(), fixedWeekCalculator(), LocaleController(settings), FakeAppInfo(),
        NoopMdPrayerRepository(), tasks, disabledMdPrayerSync(),
        source,
        SyncHealthUseCase(
            settings, source, InMemoryHealthLogRepository(), tasks, ledger,
            CompleteTaskUseCase(tasks, ledger, fixedClock(THURSDAY), TimeZone.UTC), fixedWeekCalculator(),
        ),
    )

    @Test
    fun enablingAsksForAccessThenSyncsRightAway() = runTest {
        val settings = FakeAppSettings()
        val source = FakeHealthDataSource(listOf(steps(THU, 9000)), granted = false)
        val tasks = InMemoryTaskRepository()
        tasks.upsert(Task("t1", "Walk", 5, Recurrence.DAILY, healthGoal = STEPS_8000))
        val vm = newViewModel(settings, source, tasks)
        var result: HealthEnableResult? = null

        vm.setHealthEnabled(true) { result = it }

        assertEquals(HealthEnableResult.Enabled, result)
        assertEquals(1, source.permissionRequests)
        assertTrue(settings.healthConnectEnabled)
        assertTrue(vm.healthEnabled.value)
        assertEquals(listOf(THU), tasks.observeInstances("2026-W27").first().single().completedDates)
    }

    @Test
    fun deniedAccessKeepsTheIntegrationOff() = runTest {
        val settings = FakeAppSettings()
        val source = FakeHealthDataSource(granted = false, grantOnRequest = false)
        val vm = newViewModel(settings, source)
        var result: HealthEnableResult? = null

        vm.setHealthEnabled(true) { result = it }

        assertEquals(HealthEnableResult.Denied, result)
        assertFalse(settings.healthConnectEnabled)
        assertFalse(vm.healthEnabled.value)
    }

    @Test
    fun withoutHealthConnectInstalledNoAccessIsRequested() = runTest {
        val settings = FakeAppSettings()
        val source = FakeHealthDataSource(granted = false, availability = HealthAvailability.NOT_INSTALLED)
        val vm = newViewModel(settings, source)
        var result: HealthEnableResult? = null

        vm.setHealthEnabled(true) { result = it }

        assertEquals(HealthEnableResult.NotInstalled, result)
        assertEquals(0, source.permissionRequests)
        assertFalse(settings.healthConnectEnabled)
    }

    @Test
    fun disablingTurnsItOff() = runTest {
        val settings = FakeAppSettings().apply { healthConnectEnabled = true }
        val vm = newViewModel(settings, FakeHealthDataSource())

        vm.setHealthEnabled(false)

        assertFalse(settings.healthConnectEnabled)
        assertFalse(vm.healthEnabled.value)
    }
}
