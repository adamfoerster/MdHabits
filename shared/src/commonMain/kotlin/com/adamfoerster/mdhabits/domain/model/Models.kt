package com.adamfoerster.mdhabits.domain.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/**
 * Domain models for MdHabits.
 *
 * All models are immutable data classes with plain [String] ids so the storage layer can map them
 * to Markdown notes (one note per entity) without a database. The current points balance is never
 * stored: it is always derived as the sum of [PointsEvent.delta] entries in the ledger, which keeps
 * the app resilient to manual edits made in Obsidian.
 */

/** How often a task is expected to be performed. */
enum class Recurrence {
    /** An unscheduled task, done whenever the chance appears. */
    ADHOC,

    /** A habit due every day. Completion counts per day, not per week. */
    DAILY,

    /** A weekly habit tracked per ISO week via [TaskInstance]. */
    WEEKLY,

    /** A habit due on the specific weekdays in [Task.daysOfWeek]. Completion counts per day. */
    DAYS_OF_WEEK,
}

/** The annual theme with exactly three [objectives] (the "exactly 3" rule is validated, not typed). */
data class AnnualTheme(
    val id: String,
    val year: Int,
    val name: String,
    val description: String = "",
    val objectives: List<Objective> = emptyList(),
)

/** A yearly objective that awards [points] when [achieved]. */
data class Objective(
    val id: String,
    val title: String,
    val points: Int,
    val achieved: Boolean = false,
    val achievedOn: LocalDate? = null,
)

/** A personal value such as "moderation" or "punctuality". Named [PersonalValue] to avoid `Value`. */
data class PersonalValue(
    val id: String,
    val name: String,
    val description: String = "",
)

/** A penalty that costs [pointCost] points (a positive number; applied to the ledger as negative). */
data class Penalty(
    val id: String,
    val name: String,
    val pointCost: Int,
    val description: String = "",
)

/** A task/habit that awards [points] on completion and may link to values and/or objectives. */
data class Task(
    val id: String,
    val title: String,
    val points: Int,
    val recurrence: Recurrence = Recurrence.WEEKLY,
    /** The weekdays the task is due on; only meaningful when [recurrence] is [Recurrence.DAYS_OF_WEEK]. */
    val daysOfWeek: List<DayOfWeek> = emptyList(),
    val linkedValueIds: List<String> = emptyList(),
    val linkedObjectiveIds: List<String> = emptyList(),
    val active: Boolean = true,
) {
    /** Whether the task belongs in a "today" list on the given weekday. */
    fun isDueOn(dayOfWeek: DayOfWeek): Boolean = when (recurrence) {
        Recurrence.DAILY -> true
        Recurrence.DAYS_OF_WEEK -> dayOfWeek in daysOfWeek
        Recurrence.WEEKLY, Recurrence.ADHOC -> false
    }
}

/** Per-week completion state for a [Task], kept separate from the definition so history is preserved. */
data class TaskInstance(
    val taskId: String,
    val weekId: String,
    val planned: Boolean = false,
    val completed: Boolean = false,
    val completedOn: LocalDate? = null,
)

/** A reward that costs [pointCost] points to redeem. */
data class Reward(
    val id: String,
    val name: String,
    val pointCost: Int,
    val description: String = "",
)

/** Where a [PointsEvent] originated. */
enum class PointsSource { TASK, OBJECTIVE, PENALTY, REWARD, ADJUSTMENT }

/** An append-only ledger entry. The balance is the running sum of every [delta]. */
data class PointsEvent(
    val id: String,
    val timestamp: Instant,
    val weekId: String,
    val source: PointsSource,
    val refId: String,
    val label: String,
    val delta: Int,
)

/** A weekly review. [submittedAt] is null while the review is still a draft. */
data class WeeklyReview(
    val weekId: String,
    val journal: String = "",
    /** ObjectiveId -> 1..5 sense of being close to (5) or far from (1) the objective. */
    val objectiveRatings: Map<String, Int> = emptyMap(),
    /** Tasks the user chose to focus on next week. */
    val plannedTaskIds: List<String> = emptyList(),
    val submittedAt: Instant? = null,
)

/** A read-model assembled from the ledger + instances for the weekly report screen. */
data class WeeklyReport(
    val weekId: String,
    val completedTasks: List<PointsEvent>,
    val achievedObjectives: List<PointsEvent>,
    val penalties: List<PointsEvent>,
    val redemptions: List<PointsEvent>,
    val earned: Int,
    val spent: Int,
    val net: Int,
    val endingBalance: Int,
)
