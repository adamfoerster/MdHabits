package com.adamfoerster.mdhabits.ui.screens.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adamfoerster.mdhabits.core.datetime.WeekCalculator
import com.adamfoerster.mdhabits.core.datetime.WeekRange
import com.adamfoerster.mdhabits.domain.model.Task
import com.adamfoerster.mdhabits.domain.model.WeeklyReport
import com.adamfoerster.mdhabits.domain.model.WeeklyReview
import com.adamfoerster.mdhabits.domain.repository.PointsLedgerRepository
import com.adamfoerster.mdhabits.domain.repository.TaskRepository
import com.adamfoerster.mdhabits.domain.repository.ThemeRepository
import com.adamfoerster.mdhabits.domain.repository.WeeklyReviewRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus

data class ReviewUiState(
    val step: Int = 1,
    val tasks: List<Task> = emptyList(),
    val completedCount: Int = 0,
    val report: WeeklyReport? = null,
    val commitIds: Set<String> = emptySet(),
)

class ReviewViewModel(
    private val taskRepository: TaskRepository,
    private val ledgerRepository: PointsLedgerRepository,
    private val reviewRepository: WeeklyReviewRepository,
    private val themeRepository: ThemeRepository,
    private val weekCalculator: WeekCalculator,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    // The review looks back at the week that just ended and plans the week that just started:
    // the journal/report target the previous week's note, the commitments create the new one.
    private val previousMonday = weekCalculator.rangeOf().start.minus(7, DateTimeUnit.DAY)
    val reviewWeekId: String = weekCalculator.weekId(previousMonday)
    val reviewWeekNumber: Int = weekCalculator.isoWeek(previousMonday).second

    val planWeekId: String = weekCalculator.weekId()
    val planWeekNumber: Int = weekCalculator.isoWeek(weekCalculator.today()).second
    val planWeekRange: WeekRange = weekCalculator.rangeOf()

    private val step = MutableStateFlow(1)
    private val commitIds = MutableStateFlow<Set<String>>(emptySet())
    private val report = MutableStateFlow<WeeklyReport?>(null)

    private val _journal = MutableStateFlow("")
    val journal: StateFlow<String> = _journal.asStateFlow()

    private val _proximity = MutableStateFlow(3)
    val proximity: StateFlow<Int> = _proximity.asStateFlow()

    val state: StateFlow<ReviewUiState> = combine(
        taskRepository.observeTasks(activeOnly = true),
        taskRepository.observeInstances(reviewWeekId),
        step,
        commitIds,
        report,
    ) { tasks, instances, currentStep, commits, currentReport ->
        ReviewUiState(
            step = currentStep,
            tasks = tasks,
            completedCount = instances.count { it.completed },
            report = currentReport,
            commitIds = commits,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReviewUiState())

    init {
        viewModelScope.launch { report.value = ledgerRepository.weeklyReport(reviewWeekId) }
        // Pre-select every task as a commitment for the new week; the user unchecks what's out.
        viewModelScope.launch {
            val tasks = taskRepository.observeTasks(activeOnly = true).first()
            commitIds.value = tasks.map { it.id }.toSet()
        }
        // Restore a draft review if one exists.
        viewModelScope.launch {
            reviewRepository.getReview(reviewWeekId)?.let { existing ->
                _journal.value = existing.journal
                existing.objectiveRatings.values.firstOrNull()?.let { _proximity.value = it }
            }
        }
    }

    fun setJournal(text: String) {
        _journal.value = text
    }

    fun setProximity(value: Int) {
        _proximity.value = value
    }

    fun next() {
        step.value = 2
    }

    fun backToStep1() {
        step.value = 1
    }

    fun toggleCommit(taskId: String) {
        commitIds.value = if (taskId in commitIds.value) commitIds.value - taskId else commitIds.value + taskId
    }

    /** Writes the review into the previous week's note and plans the new week, creating its note. */
    fun submit(onDone: () -> Unit) = viewModelScope.launch {
        val objectives = themeRepository.getTheme(weekCalculator.today().year)?.objectives.orEmpty()
        reviewRepository.upsert(
            WeeklyReview(
                weekId = reviewWeekId,
                journal = _journal.value,
                objectiveRatings = objectives.associate { it.id to _proximity.value },
                plannedTaskIds = commitIds.value.toList(),
                submittedAt = clock.now(),
            ),
        )
        taskRepository.setPlanned(planWeekId, commitIds.value.toList())
        onDone()
    }
}
