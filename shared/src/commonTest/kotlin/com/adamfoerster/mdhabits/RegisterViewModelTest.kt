package com.adamfoerster.mdhabits

import com.adamfoerster.mdhabits.data.repo.InMemoryPenaltyRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryRewardRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryTaskRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryThemeRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryValueRepository
import com.adamfoerster.mdhabits.domain.model.AnnualTheme
import com.adamfoerster.mdhabits.domain.model.Objective
import com.adamfoerster.mdhabits.domain.model.Penalty
import com.adamfoerster.mdhabits.domain.model.PersonalValue
import com.adamfoerster.mdhabits.domain.model.Recurrence
import com.adamfoerster.mdhabits.domain.model.Reward
import com.adamfoerster.mdhabits.domain.model.Task
import com.adamfoerster.mdhabits.ui.screens.register.RegisterViewModel
import com.adamfoerster.mdhabits.ui.screens.register.TaskFilter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class RegisterViewModelTest : MainDispatcherTest() {

    private fun newViewModel(
        tasks: InMemoryTaskRepository = InMemoryTaskRepository(),
        theme: InMemoryThemeRepository = InMemoryThemeRepository(),
        values: InMemoryValueRepository = InMemoryValueRepository(),
        penalties: InMemoryPenaltyRepository = InMemoryPenaltyRepository(),
        rewards: InMemoryRewardRepository = InMemoryRewardRepository(),
    ) = RegisterViewModel(tasks, theme, values, penalties, rewards, fixedWeekCalculator())

    @Test
    fun addTaskPersistsPointsRecurrenceAndLinks() = runTest {
        val tasks = InMemoryTaskRepository()
        val theme = InMemoryThemeRepository()
        val values = InMemoryValueRepository()
        theme.upsertTheme(AnnualTheme("th", 2026, "Year", objectives = listOf(Objective("o1", "Read", 80))))
        values.upsert(PersonalValue("v1", "Moderation"))

        val vm = newViewModel(tasks = tasks, theme = theme, values = values)
        keepHot(vm.state)

        vm.addTask(
            title = "Walk 30 min",
            points = 10,
            recurrence = Recurrence.WEEKLY,
            valueIds = listOf("v1"),
            objectiveIds = listOf("o1"),
        )

        val task = tasks.observeTasks(true).first().first { it.title == "Walk 30 min" }
        assertEquals(10, task.points)
        assertEquals(Recurrence.WEEKLY, task.recurrence)
        assertEquals(listOf("v1"), task.linkedValueIds)
        assertEquals(listOf("o1"), task.linkedObjectiveIds)
    }

    @Test
    fun completedAdhocTasksAreHiddenFromTheList() = runTest {
        val tasks = InMemoryTaskRepository()
        tasks.upsert(Task("done", "Call bank", 5, Recurrence.ADHOC))
        tasks.upsert(Task("open", "Book trip", 5, Recurrence.ADHOC))
        tasks.upsert(Task("weekly", "Walk", 10, Recurrence.WEEKLY))
        tasks.upsert(Task("weeklyDone", "Read", 10, Recurrence.WEEKLY))

        val vm = newViewModel(tasks = tasks)
        keepHot(vm.state)
        assertEquals(setOf("done", "open", "weekly", "weeklyDone"), vm.state.value.tasks.map { it.id }.toSet())

        tasks.setCompleted("done", "2026-W10", true, LocalDate(2026, 3, 4))
        tasks.setCompleted("weeklyDone", "2026-W10", true, LocalDate(2026, 3, 4))

        // Only the finished ad-hoc one leaves; completed recurring tasks stay manageable.
        assertEquals(setOf("open", "weekly", "weeklyDone"), vm.state.value.tasks.map { it.id }.toSet())
    }

    @Test
    fun deleteTaskRemovesItFromTheListAndStorage() = runTest {
        val tasks = InMemoryTaskRepository()
        tasks.upsert(Task("t1", "Walk", 10, Recurrence.WEEKLY))
        tasks.upsert(Task("t2", "Read", 10, Recurrence.DAILY))

        val vm = newViewModel(tasks = tasks)
        keepHot(vm.state)

        vm.deleteTask("t1")

        assertEquals(listOf("t2"), vm.state.value.tasks.map { it.id })
        assertEquals(null, tasks.getTask("t1"))
    }

    @Test
    fun taskFilterKeepsOnlyTheChosenFrequency() {
        val tasks = Recurrence.entries.map { Task(it.name, it.name, 5, it) }

        assertEquals(tasks, tasks.filter(TaskFilter.ALL::matches))
        assertEquals(listOf("ADHOC"), tasks.filter(TaskFilter.ADHOC::matches).map { it.id })
        assertEquals(listOf("WEEKLY"), tasks.filter(TaskFilter.WEEKLY::matches).map { it.id })
        assertEquals(listOf("DAILY"), tasks.filter(TaskFilter.DAILY::matches).map { it.id })
        assertEquals(listOf("DAYS_OF_WEEK"), tasks.filter(TaskFilter.DAYS_OF_WEEK::matches).map { it.id })
        assertEquals(listOf("HABIT"), tasks.filter(TaskFilter.HABIT::matches).map { it.id })
    }

    @Test
    fun addObjectiveAppendsToCurrentTheme() = runTest {
        val theme = InMemoryThemeRepository()
        theme.upsertTheme(AnnualTheme("th", 2026, "Year", objectives = listOf(Objective("o1", "Read", 80))))

        val vm = newViewModel(theme = theme)
        keepHot(vm.state)

        vm.addObjective("Save 10k", 120)

        val objectives = theme.getTheme(2026)!!.objectives
        assertEquals(2, objectives.size)
        assertEquals("Save 10k", objectives.last().title)
        assertEquals(120, objectives.last().points)
    }

    @Test
    fun addValueAndRewardAppearInState() = runTest {
        val vm = newViewModel()
        keepHot(vm.state)

        vm.addValue("Punctuality")
        vm.addReward("Dinner out", 120)

        assertEquals("Punctuality", vm.state.value.values.single().name)
        assertEquals(120, vm.state.value.rewards.single().pointCost)
    }

    @Test
    fun updateTaskEditsFieldsAndKeepsIdAndActiveFlag() = runTest {
        val tasks = InMemoryTaskRepository()
        tasks.upsert(Task("t1", "Walk", 10, Recurrence.WEEKLY, linkedValueIds = listOf("v1"), active = false))

        val vm = newViewModel(tasks = tasks)
        keepHot(vm.state)

        vm.updateTask(
            id = "t1",
            title = "Walk 30 min",
            points = 15,
            recurrence = Recurrence.DAYS_OF_WEEK,
            daysOfWeek = listOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
        )

        val task = tasks.getTask("t1")!!
        assertEquals("Walk 30 min", task.title)
        assertEquals(15, task.points)
        assertEquals(Recurrence.DAYS_OF_WEEK, task.recurrence)
        assertEquals(listOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), task.daysOfWeek)
        assertEquals(emptyList(), task.linkedValueIds)
        assertEquals(false, task.active)
    }

    @Test
    fun updateObjectiveKeepsAchievedStateAndSiblings() = runTest {
        val theme = InMemoryThemeRepository()
        theme.upsertTheme(
            AnnualTheme(
                "th", 2026, "Year",
                objectives = listOf(
                    Objective("o1", "Read", 80, achieved = true, achievedOn = LocalDate(2026, 6, 1)),
                    Objective("o2", "Save", 120),
                ),
            ),
        )

        val vm = newViewModel(theme = theme)
        keepHot(vm.state)

        vm.updateObjective("o1", "Read 24 books", 100)

        val objectives = theme.getTheme(2026)!!.objectives
        assertEquals("Read 24 books", objectives[0].title)
        assertEquals(100, objectives[0].points)
        assertEquals(true, objectives[0].achieved)
        assertEquals(LocalDate(2026, 6, 1), objectives[0].achievedOn)
        assertEquals("Save", objectives[1].title)
    }

    @Test
    fun updateValuePenaltyAndRewardKeepDescriptions() = runTest {
        val values = InMemoryValueRepository()
        val penalties = InMemoryPenaltyRepository()
        val rewards = InMemoryRewardRepository()
        values.upsert(PersonalValue("v1", "Moderation", "Do less, better."))
        penalties.upsert(Penalty("p1", "Junk food", 10, "Fast food counts."))
        rewards.upsert(Reward("r1", "Dinner", 50, "Somewhere nice."))

        val vm = newViewModel(values = values, penalties = penalties, rewards = rewards)
        keepHot(vm.state)

        vm.updateValue("v1", "Temperance")
        vm.updatePenalty("p1", "Junk food day", 15)
        vm.updateReward("r1", "Dinner out", 60)

        assertEquals(PersonalValue("v1", "Temperance", "Do less, better."), values.getValue("v1"))
        assertEquals(Penalty("p1", "Junk food day", 15, "Fast food counts."), penalties.getPenalty("p1"))
        assertEquals(Reward("r1", "Dinner out", 60, "Somewhere nice."), rewards.getReward("r1"))
    }
}
