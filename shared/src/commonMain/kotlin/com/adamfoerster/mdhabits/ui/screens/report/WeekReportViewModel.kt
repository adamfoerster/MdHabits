package com.adamfoerster.mdhabits.ui.screens.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adamfoerster.mdhabits.core.datetime.WeekCalculator
import com.adamfoerster.mdhabits.core.datetime.WeekRange
import com.adamfoerster.mdhabits.domain.model.PointsEvent
import com.adamfoerster.mdhabits.domain.model.WeeklyReport
import com.adamfoerster.mdhabits.domain.repository.PointsLedgerRepository
import com.adamfoerster.mdhabits.domain.repository.WeeklyReviewRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WeekReportUiState(
    val weekId: String = "",
    val weekNumber: Int = 0,
    val range: WeekRange? = null,
    val report: WeeklyReport? = null,
    /** The journal of that week's review; empty when the week was never reviewed. */
    val journal: String = "",
    val loading: Boolean = true,
) {
    /** Tasks checked off during the week, newest last, as the ledger recorded them. */
    val completed: List<PointsEvent> get() = report?.completedTasks.orEmpty()

    /** What the week cost: applied penalties and missed habit days. */
    val slips: List<PointsEvent> get() = report?.penalties.orEmpty()

    val redemptions: List<PointsEvent> get() = report?.redemptions.orEmpty()

    val isEmpty: Boolean get() = !loading && completed.isEmpty() && slips.isEmpty() &&
        redemptions.isEmpty() && report?.achievedObjectives.orEmpty().isEmpty() && journal.isBlank()
}

/**
 * A closed week, read only. The screen hands it the week from the route via [show]; everything it
 * shows is derived from the ledger and the week's review, and it writes nothing back — a past week
 * is history, so the journal, the checkboxes and the points of those weeks are not editable.
 */
class WeekReportViewModel(
    private val ledgerRepository: PointsLedgerRepository,
    private val reviewRepository: WeeklyReviewRepository,
    private val weekCalculator: WeekCalculator,
) : ViewModel() {

    private val _state = MutableStateFlow(WeekReportUiState())
    val state: StateFlow<WeekReportUiState> = _state.asStateFlow()

    /** Loads [weekId]; re-loading the week already shown is a no-op. */
    fun show(weekId: String) = viewModelScope.launch {
        if (_state.value.weekId == weekId && !_state.value.loading) return@launch
        _state.value = WeekReportUiState(weekId = weekId, loading = true)
        val report = ledgerRepository.weeklyReport(weekId)
        val review = reviewRepository.getReview(weekId)
        _state.value = WeekReportUiState(
            weekId = weekId,
            weekNumber = weekCalculator.weekNumberOf(weekId) ?: 0,
            range = weekCalculator.rangeOfWeekId(weekId),
            report = report,
            journal = review?.journal.orEmpty(),
            loading = false,
        )
    }
}
