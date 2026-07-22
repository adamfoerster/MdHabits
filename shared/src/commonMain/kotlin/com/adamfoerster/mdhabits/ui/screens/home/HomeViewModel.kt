package com.adamfoerster.mdhabits.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adamfoerster.mdhabits.core.datetime.WeekCalculator
import com.adamfoerster.mdhabits.core.datetime.WeekRange
import com.adamfoerster.mdhabits.domain.model.Penalty
import com.adamfoerster.mdhabits.domain.model.Recurrence
import com.adamfoerster.mdhabits.domain.model.Task
import com.adamfoerster.mdhabits.domain.repository.PenaltyRepository
import com.adamfoerster.mdhabits.domain.repository.PointsLedgerRepository
import com.adamfoerster.mdhabits.domain.repository.TaskRepository
import com.adamfoerster.mdhabits.domain.repository.ThemeRepository
import com.adamfoerster.mdhabits.domain.repository.ValueRepository
import com.adamfoerster.mdhabits.domain.usecase.ApplyPenaltyUseCase
import com.adamfoerster.mdhabits.domain.usecase.CompleteTaskUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeTaskRow(
    val task: Task,
    val completed: Boolean,
    val planned: Boolean,
    /** Names of the values/objectives this task is linked to, for the row subtitle. */
    val linkedNames: List<String> = emptyList(),
)

data class HomeUiState(
    val weekNumber: Int = 0,
    val range: WeekRange? = null,
    val themeName: String = "",
    val hasTheme: Boolean = false,
    /** Tasks due today: daily tasks plus days-of-week tasks whose days include today. */
    val todayTasks: List<HomeTaskRow> = emptyList(),
    /** Weekly tasks, completed once per ISO week. */
    val weeklyTasks: List<HomeTaskRow> = emptyList(),
    /** Unscheduled tasks, done whenever. */
    val adhocTasks: List<HomeTaskRow> = emptyList(),
    val balance: Int = 0,
    /** True while the current week's note doesn't exist yet — the review is what creates it. */
    val showReviewCta: Boolean = false,
    val loading: Boolean = true,
) {
    val allTasks: List<HomeTaskRow> get() = todayTasks + weeklyTasks + adhocTasks
}

class HomeViewModel(
    private val taskRepository: TaskRepository,
    private val themeRepository: ThemeRepository,
    private val ledgerRepository: PointsLedgerRepository,
    private val penaltyRepository: PenaltyRepository,
    private val valueRepository: ValueRepository,
    private val completeTask: CompleteTaskUseCase,
    private val applyPenalty: ApplyPenaltyUseCase,
    private val weekCalculator: WeekCalculator,
) : ViewModel() {

    val weekId: String = weekCalculator.weekId()
    private val year = weekCalculator.today().year

    val state: StateFlow<HomeUiState> = combine(
        combine(
            taskRepository.observeTasks(activeOnly = true),
            taskRepository.observeInstances(weekId),
            taskRepository.observeWeekStarted(weekId),
        ) { tasks, instances, weekStarted -> Triple(tasks, instances, weekStarted) },
        themeRepository.observeTheme(year),
        ledgerRepository.observeBalance(),
        valueRepository.observeValues(),
    ) { (tasks, instances, weekStarted), theme, balance, values ->
        val today = weekCalculator.today()
        val (_, weekNumber) = weekCalculator.isoWeek(today)
        val instanceByTask = instances.associateBy { it.taskId }
        val valueNames = values.associate { it.id to it.name }
        val objectiveNames = theme?.objectives?.associate { it.id to it.title }.orEmpty()
        val rows = tasks.map { task ->
            val instance = instanceByTask[task.id]
            // Daily and days-of-week tasks are due again every scheduled day, so a completion
            // only counts on the day it was made; weekly/ad-hoc completions hold for the week.
            val perDay = task.recurrence == Recurrence.DAILY || task.recurrence == Recurrence.DAYS_OF_WEEK
            val completed = instance?.completed == true && (!perDay || instance.completedOn == today)
            HomeTaskRow(
                task = task,
                completed = completed,
                planned = instance?.planned == true,
                linkedNames = task.linkedValueIds.mapNotNull(valueNames::get) +
                    task.linkedObjectiveIds.mapNotNull(objectiveNames::get),
            )
        }.sortedWith(compareBy({ it.completed }, { it.task.title }))
        HomeUiState(
            weekNumber = weekNumber,
            range = weekCalculator.rangeOf(),
            themeName = theme?.name.orEmpty(),
            hasTheme = theme != null,
            todayTasks = rows.filter { it.task.isDueOn(today.dayOfWeek) },
            weeklyTasks = rows.filter { it.task.recurrence == Recurrence.WEEKLY },
            adhocTasks = rows.filter { it.task.recurrence == Recurrence.ADHOC },
            balance = balance,
            showReviewCta = !weekStarted,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    val penalties: StateFlow<List<Penalty>> = penaltyRepository.observePenalties()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onToggleComplete(task: Task, completed: Boolean) = viewModelScope.launch {
        completeTask(task, weekId, completed)
    }

    fun onApplyPenalty(penalty: Penalty) = viewModelScope.launch {
        applyPenalty(weekId, penalty)
    }
}
