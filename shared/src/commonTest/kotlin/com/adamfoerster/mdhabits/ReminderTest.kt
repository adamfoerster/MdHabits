package com.adamfoerster.mdhabits

import com.adamfoerster.mdhabits.core.i18n.LocaleController
import com.adamfoerster.mdhabits.data.markdown.MarkdownCodecs
import com.adamfoerster.mdhabits.data.repo.InMemoryPenaltyRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryRewardRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryTaskRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryThemeRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryValueRepository
import com.adamfoerster.mdhabits.domain.model.Recurrence
import com.adamfoerster.mdhabits.domain.model.ReminderPlan
import com.adamfoerster.mdhabits.domain.model.ReminderTime
import com.adamfoerster.mdhabits.domain.model.Task
import com.adamfoerster.mdhabits.domain.model.nextReminderAt
import com.adamfoerster.mdhabits.domain.model.planReminders
import com.adamfoerster.mdhabits.domain.model.reminderDays
import com.adamfoerster.mdhabits.domain.repository.ReminderScheduler
import com.adamfoerster.mdhabits.domain.usecase.ReminderSyncer
import com.adamfoerster.mdhabits.ui.screens.register.RegisterViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Records every set of plans handed to it, standing in for AlarmManager / UNUserNotificationCenter. */
private class RecordingScheduler : ReminderScheduler {
    val scheduled = mutableListOf<List<ReminderPlan>>()
    override val isSupported = true
    override suspend fun requestPermission() = true
    override fun schedule(plans: List<ReminderPlan>) {
        scheduled += plans
    }

    val latest get() = scheduled.last()
}

class ReminderTest : MainDispatcherTest() {

    // 2026-07-02 is a Thursday.
    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime(2026, 7, day, hour, minute)

    private fun plan(
        time: ReminderTime = ReminderTime(8, 30),
        days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
        skipToday: Boolean = false,
    ) = ReminderPlan("t1", "Read", "Time", time, days, skipToday)

    @Test
    fun reminderTimeParsesAndFormats() {
        assertEquals(ReminderTime(8, 5), ReminderTime.parseOrNull("8:05"))
        assertEquals("08:05", ReminderTime(8, 5).toString())
        assertNull(ReminderTime.parseOrNull("24:00"))
        assertNull(ReminderTime.parseOrNull("12:60"))
        assertNull(ReminderTime.parseOrNull("soon"))
        assertFailsWith<IllegalArgumentException> { ReminderTime(25, 0) }
    }

    @Test
    fun reminderDaysFollowTheSchedule() {
        val time = ReminderTime(7, 0)
        assertEquals(7, Task("a", "A", 1, Recurrence.DAILY, reminder = time).reminderDays.size)
        assertEquals(7, Task("b", "B", 1, Recurrence.HABIT, reminder = time).reminderDays.size)
        assertEquals(
            setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
            Task("c", "C", 1, Recurrence.DAYS_OF_WEEK, listOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), reminder = time).reminderDays,
        )
        assertTrue(Task("d", "D", 1, Recurrence.WEEKLY, reminder = time).reminderDays.isEmpty())
        assertTrue(Task("e", "E", 1, Recurrence.ADHOC, reminder = time).reminderDays.isEmpty())
        assertTrue(Task("f", "F", 1, Recurrence.DAILY).reminderDays.isEmpty())
    }

    @Test
    fun planRemindersKeepsOnlyActiveTasksWithAReminder() {
        val time = ReminderTime(9, 0)
        val tasks = listOf(
            Task("on", "On", 1, Recurrence.DAILY, reminder = time),
            Task("none", "None", 1, Recurrence.DAILY),
            Task("off", "Off", 1, Recurrence.DAILY, reminder = time, active = false),
            Task("weekly", "Weekly", 1, Recurrence.WEEKLY, reminder = time),
            Task("done", "Done", 1, Recurrence.HABIT, reminder = time),
        )
        val plans = planReminders(tasks, completedTodayIds = setOf("done"), body = "Go")
        assertEquals(listOf("on", "done"), plans.map { it.taskId })
        assertEquals(listOf(false, true), plans.map { it.skipToday })
        assertEquals("Go", plans.first().body)
    }

    @Test
    fun nextReminderIsTodayWhenTheTimeIsStillAhead() {
        assertEquals(at(2, 8, 30), nextReminderAt(at(2, 7), plan()))
    }

    @Test
    fun nextReminderIsTomorrowOnceTheTimePassed() {
        assertEquals(at(3, 8, 30), nextReminderAt(at(2, 9), plan()))
        // The exact minute counts as passed: it must be strictly in the future.
        assertEquals(at(3, 8, 30), nextReminderAt(at(2, 8, 30), plan()))
    }

    @Test
    fun nextReminderSkipsTodayWhenAlreadyDone() {
        assertEquals(at(3, 8, 30), nextReminderAt(at(2, 7), plan(skipToday = true)))
    }

    @Test
    fun nextReminderLandsOnTheNextChosenWeekday() {
        // Thursday evening, reminder only on Mondays and Wednesdays: the next Monday is July 6th.
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)
        assertEquals(at(6, 8, 30), nextReminderAt(at(2, 20), plan(days = days)))
        // Same weekday, time passed: a week later.
        assertEquals(at(9, 8, 30), nextReminderAt(at(2, 9), plan(days = setOf(DayOfWeek.THURSDAY))))
        assertNull(nextReminderAt(at(2, 7), plan(days = emptySet())))
    }

    @Test
    fun reminderSurvivesTheMarkdownRoundTrip() {
        val task = Task("t1", "Read", 5, Recurrence.DAILY, reminder = ReminderTime(6, 45))
        val text = MarkdownCodecs.encodeTask(task)
        assertTrue("reminder: \"06:45\"" in text)
        assertEquals(task, MarkdownCodecs.decodeTask(text))
    }

    @Test
    fun taskWithoutReminderWritesNoField() {
        val text = MarkdownCodecs.encodeTask(Task("t1", "Read", 5))
        assertFalse("reminder" in text)
        assertNull(MarkdownCodecs.decodeTask(text)?.reminder)
    }

    @Test
    fun unreadableReminderIsDroppedNotFatal() {
        val text = MarkdownCodecs.encodeTask(Task("t1", "Read", 5, Recurrence.DAILY, reminder = ReminderTime(6, 45)))
            .replace("06:45", "whenever")
        val task = MarkdownCodecs.decodeTask(text)
        assertEquals("Read", task?.title)
        assertNull(task?.reminder)
    }

    @Test
    fun registerViewModelSavesAndClearsTheReminder() = runTest {
        val tasks = InMemoryTaskRepository()
        val vm = RegisterViewModel(
            tasks, InMemoryThemeRepository(), InMemoryValueRepository(), InMemoryPenaltyRepository(),
            InMemoryRewardRepository(), fixedWeekCalculator(),
        )
        vm.addTask("Stretch", 5, Recurrence.DAILY, reminder = ReminderTime(7, 15))
        val created = tasks.observeTasks(true).first().single()
        assertEquals(ReminderTime(7, 15), created.reminder)

        vm.updateTask(created.id, "Stretch", 5, Recurrence.DAILY, reminder = null)
        assertNull(tasks.getTask(created.id)?.reminder)
    }

    @Test
    fun syncerSchedulesRemindersAndDropsTodayOnceDone() = runTest {
        val tasks = InMemoryTaskRepository()
        tasks.upsert(Task("t1", "Read", 5, Recurrence.DAILY, reminder = ReminderTime(8, 0)))
        tasks.upsert(Task("t2", "Nap", 5, Recurrence.DAILY))
        val scheduler = RecordingScheduler()
        val calc = fixedWeekCalculator()
        val syncer = ReminderSyncer(tasks, scheduler, calc, LocaleController(FakeAppSettings()))

        backgroundScope.launch { syncer.run() }
        testScheduler.runCurrent()

        assertEquals(listOf("t1"), scheduler.latest.map { it.taskId })
        assertFalse(scheduler.latest.single().skipToday)

        tasks.setCompleted("t1", calc.weekId(), true, calc.today())
        testScheduler.runCurrent()

        assertTrue(scheduler.latest.single().skipToday)
    }

    @Test
    fun syncerSchedulesNothingForTasksThatLoseTheirReminder() = runTest {
        val tasks = InMemoryTaskRepository()
        tasks.upsert(Task("t1", "Read", 5, Recurrence.DAILY, reminder = ReminderTime(8, 0)))
        val scheduler = RecordingScheduler()
        val syncer = ReminderSyncer(tasks, scheduler, fixedWeekCalculator(), LocaleController(FakeAppSettings()))

        backgroundScope.launch { syncer.run() }
        testScheduler.runCurrent()
        tasks.upsert(Task("t1", "Read", 5, Recurrence.DAILY, reminder = null))
        testScheduler.runCurrent()

        assertTrue(scheduler.latest.isEmpty())
    }

    @Test
    fun weekdayArithmeticOfTheTestsHolds() {
        // The scheduling tests above assume 2026-07-02 is a Thursday.
        assertEquals(DayOfWeek.THURSDAY, LocalDate(2026, 7, 2).dayOfWeek)
    }
}
