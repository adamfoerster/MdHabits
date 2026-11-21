package com.adamfoerster.mdhabits.ui.screens.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adamfoerster.mdhabits.core.datetime.WeekCalculator
import com.adamfoerster.mdhabits.core.newId
import com.adamfoerster.mdhabits.domain.model.AnnualTheme
import com.adamfoerster.mdhabits.domain.model.HealthGoal
import com.adamfoerster.mdhabits.domain.model.Objective
import com.adamfoerster.mdhabits.domain.model.Penalty
import com.adamfoerster.mdhabits.domain.model.PersonalValue
import com.adamfoerster.mdhabits.domain.model.Recurrence
import com.adamfoerster.mdhabits.domain.model.ReminderTime
import kotlinx.datetime.LocalDate
import com.adamfoerster.mdhabits.domain.model.Reward
import com.adamfoerster.mdhabits.domain.model.Task
import com.adamfoerster.mdhabits.domain.model.stampHabitSince
import com.adamfoerster.mdhabits.domain.repository.PenaltyRepository
import com.adamfoerster.mdhabits.domain.repository.RewardRepository
import com.adamfoerster.mdhabits.domain.repository.TaskRepository
import com.adamfoerster.mdhabits.domain.repository.ThemeRepository
import com.adamfoerster.mdhabits.domain.repository.ValueRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek

/** Narrows the task list on the management screen to one frequency; [ALL] shows everything. */
enum class TaskFilter(private val recurrence: Recurrence?) {
    ALL(null),
    ADHOC(Recurrence.ADHOC),
    WEEKLY(Recurrence.WEEKLY),
    MONTHLY(Recurrence.MONTHLY),
    DAILY(Recurrence.DAILY),
    DAYS_OF_WEEK(Recurrence.DAYS_OF_WEEK),
    HABIT(Recurrence.HABIT),
    ;

    fun matches(task: Task) = recurrence == null || task.recurrence == recurrence
}

data class RegisterUiState(
    val tasks: List<Task> = emptyList(),
    val theme: AnnualTheme? = null,
    val values: List<PersonalValue> = emptyList(),
    val penalties: List<Penalty> = emptyList(),
    val rewards: List<Reward> = emptyList(),
) {
    val objectives: List<Objective> get() = theme?.objectives.orEmpty()

    fun linkedNames(task: Task): List<String> =
        task.linkedValueIds.mapNotNull { id -> values.find { it.id == id }?.name } +
            task.linkedObjectiveIds.mapNotNull { id -> objectives.find { it.id == id }?.title }

    /** Tasks whose title or linked value/objective names contain [query] (case- and accent-insensitive). */
    fun searchTasks(query: String): List<Task> =
        tasks.filter { matchesSearch(query, listOf(it.title) + linkedNames(it)) }

    /** How many listed tasks each [TaskFilter] chip would show among [matching]. */
    fun filterCounts(matching: List<Task>): Map<TaskFilter, Int> =
        TaskFilter.entries.associateWith { filter -> matching.count(filter::matches) }

    fun tasksLinkedToValue(id: String): Int = tasks.count { id in it.linkedValueIds }

    fun tasksLinkedToObjective(id: String): Int = tasks.count { id in it.linkedObjectiveIds }
}

/** True when [query] is blank or appears in any of [texts], ignoring case and accents. */
fun matchesSearch(query: String, texts: List<String>): Boolean {
    val needle = foldForSearch(query.trim())
    return needle.isEmpty() || texts.any { foldForSearch(it).contains(needle) }
}

private val accentFolds: Map<Char, Char> = buildMap {
    fun fold(target: Char, sources: String) = sources.forEach { put(it, target) }
    fold('a', "áàâãäå"); fold('e', "éèêë"); fold('i', "íìîï"); fold('o', "óòôõö")
    fold('u', "úùûü"); fold('c', "ç"); fold('n', "ñ"); fold('y', "ýÿ")
}

/** Lowercases and strips the Latin accents used in the app's three languages ("Diária" → "diaria"). */
fun foldForSearch(text: String): String =
    text.lowercase().map { accentFolds[it] ?: it }.joinToString("")

class RegisterViewModel(
    private val taskRepository: TaskRepository,
    private val themeRepository: ThemeRepository,
    private val valueRepository: ValueRepository,
    private val penaltyRepository: PenaltyRepository,
    private val rewardRepository: RewardRepository,
    private val weekCalculator: WeekCalculator,
) : ViewModel() {

    private val year = weekCalculator.today().year

    // A completed ad-hoc task is finished for good, so it leaves the management list.
    private val openTasks = combine(
        taskRepository.observeTasks(activeOnly = true),
        taskRepository.observeCompletedTaskIds(),
    ) { tasks, everCompleted ->
        tasks.filterNot { it.recurrence == Recurrence.ADHOC && it.id in everCompleted }
    }

    val state: StateFlow<RegisterUiState> = combine(
        openTasks,
        themeRepository.observeTheme(year),
        valueRepository.observeValues(),
        penaltyRepository.observePenalties(),
        rewardRepository.observeRewards(),
    ) { tasks, theme, values, penalties, rewards ->
        RegisterUiState(tasks, theme, values, penalties, rewards)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RegisterUiState())

    fun addValue(name: String) = viewModelScope.launch {
        valueRepository.upsert(PersonalValue(newId("v"), name))
    }

    /** Appends an objective to the current annual theme (no-op when no theme exists yet). */
    fun addObjective(title: String, points: Int) = viewModelScope.launch {
        val theme = themeRepository.getTheme(year) ?: return@launch
        themeRepository.upsertTheme(
            theme.copy(objectives = theme.objectives + Objective(newId("obj"), title, points)),
        )
    }

    fun addTask(
        title: String,
        points: Int,
        recurrence: Recurrence,
        daysOfWeek: List<DayOfWeek> = emptyList(),
        valueIds: List<String> = emptyList(),
        objectiveIds: List<String> = emptyList(),
        healthGoal: HealthGoal? = null,
        reminder: ReminderTime? = null,
        dueDate: LocalDate? = null,
    ) = viewModelScope.launch {
        taskRepository.upsert(
            Task(
                id = newId("t"),
                title = title,
                points = points,
                recurrence = recurrence,
                daysOfWeek = daysOfWeek,
                linkedValueIds = valueIds,
                linkedObjectiveIds = objectiveIds,
                healthGoal = healthGoal,
                reminder = reminder,
                dueDate = dueDate.takeIf { recurrence == Recurrence.ADHOC },
            ).stampHabitSince(weekCalculator.today()),
        )
    }

    fun addPenalty(name: String, cost: Int) = viewModelScope.launch {
        penaltyRepository.upsert(Penalty(newId("p"), name, cost))
    }

    fun addReward(name: String, cost: Int) = viewModelScope.launch {
        rewardRepository.upsert(Reward(newId("r"), name, cost))
    }

    // Edits update only the fields the form exposes; descriptions, active flags, and an
    // objective's achieved state are preserved from the stored entity.

    fun updateValue(id: String, name: String) = viewModelScope.launch {
        val existing = valueRepository.getValue(id) ?: return@launch
        valueRepository.upsert(existing.copy(name = name))
    }

    fun updateObjective(id: String, title: String, points: Int) = viewModelScope.launch {
        val theme = themeRepository.getTheme(year) ?: return@launch
        themeRepository.upsertTheme(
            theme.copy(
                objectives = theme.objectives.map {
                    if (it.id == id) it.copy(title = title, points = points) else it
                },
            ),
        )
    }

    fun updateTask(
        id: String,
        title: String,
        points: Int,
        recurrence: Recurrence,
        daysOfWeek: List<DayOfWeek> = emptyList(),
        valueIds: List<String> = emptyList(),
        objectiveIds: List<String> = emptyList(),
        healthGoal: HealthGoal? = null,
        reminder: ReminderTime? = null,
        dueDate: LocalDate? = null,
    ) = viewModelScope.launch {
        val existing = taskRepository.getTask(id) ?: return@launch
        taskRepository.upsert(
            existing.copy(
                title = title,
                points = points,
                recurrence = recurrence,
                daysOfWeek = daysOfWeek,
                linkedValueIds = valueIds,
                linkedObjectiveIds = objectiveIds,
                healthGoal = healthGoal,
                reminder = reminder,
                dueDate = dueDate.takeIf { recurrence == Recurrence.ADHOC },
                // A changed deadline is a new one: it can be charged again if missed.
                dueMissedOn = existing.dueMissedOn.takeIf { dueDate != null && it == dueDate },
            ).stampHabitSince(weekCalculator.today()),
        )
    }

    /** Removes the task itself; the points it already earned stay in the ledger. */
    fun deleteTask(id: String) = viewModelScope.launch {
        taskRepository.delete(id)
    }

    fun updatePenalty(id: String, name: String, cost: Int) = viewModelScope.launch {
        val existing = penaltyRepository.getPenalty(id) ?: return@launch
        penaltyRepository.upsert(existing.copy(name = name, pointCost = cost))
    }

    fun updateReward(id: String, name: String, cost: Int) = viewModelScope.launch {
        val existing = rewardRepository.getReward(id) ?: return@launch
        rewardRepository.upsert(existing.copy(name = name, pointCost = cost))
    }
}
