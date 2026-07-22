package com.adamfoerster.mdhabits

import com.adamfoerster.mdhabits.core.datetime.WeekCalculator
import com.adamfoerster.mdhabits.core.platform.AppInfo
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
