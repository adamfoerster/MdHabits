package com.adamfoerster.mdhabits

import com.adamfoerster.mdhabits.core.datetime.WeekCalculator
import com.adamfoerster.mdhabits.core.datetime.WeekRange
import com.adamfoerster.mdhabits.core.platform.AppInfo
import com.adamfoerster.mdhabits.data.repo.InMemoryHealthLogRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryPointsLedgerRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryTaskRepository
import com.adamfoerster.mdhabits.domain.model.DailyHealth
import com.adamfoerster.mdhabits.domain.repository.HealthAvailability
import com.adamfoerster.mdhabits.domain.repository.HealthDataSource
import com.adamfoerster.mdhabits.domain.repository.MdPrayerRepository
import com.adamfoerster.mdhabits.domain.repository.PointsLedgerRepository
import com.adamfoerster.mdhabits.domain.repository.TaskRepository
import com.adamfoerster.mdhabits.domain.repository.UnsupportedHealthDataSource
import com.adamfoerster.mdhabits.domain.usecase.CompleteTaskUseCase
import com.adamfoerster.mdhabits.domain.usecase.PenalizeMissedHabitsUseCase
import com.adamfoerster.mdhabits.domain.usecase.SyncHealthUseCase
import com.adamfoerster.mdhabits.domain.usecase.SyncMdPrayerUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

class FakeAppInfo(override val version: String = "1.2.3") : AppInfo

/** A [Clock] pinned to a fixed instant, for deterministic dates in tests. */
fun fixedClock(iso: String = "2026-07-02T12:00:00Z"): Clock = object : Clock {
    override fun now(): Instant = Instant.parse(iso)
}

/** A [WeekCalculator] pinned to a fixed instant so week ids are deterministic in tests. */
fun fixedWeekCalculator(iso: String = "2026-07-02T12:00:00Z") = WeekCalculator(
    clock = fixedClock(iso),
    timeZone = TimeZone.UTC,
)

/** An [MdPrayerRepository] that never finds a vault or completed days — used where a test just
 *  needs a harmless stand-in, e.g. a disabled [SyncMdPrayerUseCase]. */
class NoopMdPrayerRepository : MdPrayerRepository {
    override suspend fun looksLikeMdPrayerVault(ref: String): Boolean = false
    override suspend fun completedDates(ref: String, range: WeekRange): Set<kotlinx.datetime.LocalDate> = emptySet()
}

/** A [SyncMdPrayerUseCase] that always no-ops (the integration is off in [FakeAppSettings] by
 *  default) — the harmless default for tests that don't exercise the mdPrayer integration. */
fun disabledMdPrayerSync(weekCalculator: WeekCalculator = fixedWeekCalculator()) = SyncMdPrayerUseCase(
    settings = FakeAppSettings(),
    mdPrayer = NoopMdPrayerRepository(),
    tasks = InMemoryTaskRepository(),
    completeTask = CompleteTaskUseCase(InMemoryTaskRepository(), InMemoryPointsLedgerRepository()),
    weekCalculator = weekCalculator,
)

/**
 * A scripted health store: [days] are what Health Connect "recorded", [granted] whether the user
 * gave access (a [requestPermissions] call grants it when [grantOnRequest]). [failing] makes every
 * read throw, like a revoked permission or a missing provider would.
 */
class FakeHealthDataSource(
    days: List<DailyHealth> = emptyList(),
    var granted: Boolean = true,
    var availability: HealthAvailability = HealthAvailability.AVAILABLE,
    private val grantOnRequest: Boolean = true,
    var failing: Boolean = false,
) : HealthDataSource {
    val days = days.associateBy { it.date }.toMutableMap()
    var permissionRequests = 0
        private set

    override val isSupported = true
    override suspend fun availability() = availability
    override suspend fun hasPermissions() = granted
    override suspend fun requestPermissions(): Boolean {
        permissionRequests++
        if (grantOnRequest) granted = true
        return granted
    }
    override suspend fun readDay(date: LocalDate): DailyHealth {
        if (failing) error("Health Connect unavailable")
        return days[date] ?: DailyHealth(date)
    }
}

/** A [SyncHealthUseCase] that always no-ops (the integration is off in [FakeAppSettings] by
 *  default) — the harmless default for tests that don't exercise the health integration. */
fun disabledHealthSync(weekCalculator: WeekCalculator = fixedWeekCalculator()) = SyncHealthUseCase(
    settings = FakeAppSettings(),
    source = UnsupportedHealthDataSource,
    healthLog = InMemoryHealthLogRepository(),
    tasks = InMemoryTaskRepository(),
    ledger = InMemoryPointsLedgerRepository(),
    completeTask = CompleteTaskUseCase(InMemoryTaskRepository(), InMemoryPointsLedgerRepository()),
    weekCalculator = weekCalculator,
)

/** A [PenalizeMissedHabitsUseCase] pinned to the same fixed instant as [fixedWeekCalculator]; a
 *  no-op for tests whose tasks hold no habit. */
fun habitSweep(
    tasks: TaskRepository,
    ledger: PointsLedgerRepository,
    iso: String = "2026-07-02T12:00:00Z",
) = PenalizeMissedHabitsUseCase(tasks, ledger, fixedWeekCalculator(iso), fixedClock(iso))

/**
 * Base class for ViewModel tests: installs an [UnconfinedTestDispatcher] as `Dispatchers.Main`
 * so `viewModelScope` runs eagerly and its `StateFlow`s emit synchronously.
 */
/**
 * Subscribes to [flow] on an eager [UnconfinedTestDispatcher] via [TestScope.backgroundScope], so a
 * `stateIn(WhileSubscribed)` flow becomes hot and its `value` reflects updates. The subscription is
 * cancelled automatically when the test ends.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.keepHot(flow: Flow<*>) {
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect {} }
}

@OptIn(ExperimentalCoroutinesApi::class)
abstract class MainDispatcherTest {
    @BeforeTest
    fun installMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun removeMain() {
        Dispatchers.resetMain()
    }
}
