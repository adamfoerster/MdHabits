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

    /**
     * A habit due every day whose points work the other way around: a day that ends without it
     * being completed *costs* [Task.points] instead of earning them. The charge is applied by
     * [com.adamfoerster.mdhabits.domain.usecase.PenalizeMissedHabitsUseCase].
     */
    HABIT,
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
    /**
     * The day this task became a [Recurrence.HABIT]. Missed days are never charged before it, so
     * adding a habit (or turning a task into one) can't bill the weeks the sweep looks back over.
     * Stamped by [stampHabitSince]; null for every task that isn't a habit.
     */
    val habitSince: LocalDate? = null,
) {
    /** Whether the task belongs in a "today" list on the given weekday. */
    fun isDueOn(dayOfWeek: DayOfWeek): Boolean = when (recurrence) {
        Recurrence.DAILY, Recurrence.HABIT -> true
        Recurrence.DAYS_OF_WEEK -> dayOfWeek in daysOfWeek
        Recurrence.WEEKLY, Recurrence.ADHOC -> false
    }

    /**
     * Whether completion counts per day rather than once per ISO week: these tasks are due again
     * every scheduled day, so a completion only holds for the day it was made.
     */
    val isPerDay: Boolean
        get() = recurrence == Recurrence.DAILY || recurrence == Recurrence.DAYS_OF_WEEK ||
            recurrence == Recurrence.HABIT
}

/**
 * The task with its [Task.habitSince] stamp in sync with its recurrence: one that just became a
 * habit starts counting from [today], and one that stopped being a habit drops the stamp. Every
 * write of a task from the UI goes through this, so a habit always knows its first chargeable day.
 */
fun Task.stampHabitSince(today: LocalDate): Task = when {
    recurrence != Recurrence.HABIT -> if (habitSince == null) this else copy(habitSince = null)
    habitSince == null -> copy(habitSince = today)
    else -> this
}

/** Per-week completion state for a [Task], kept separate from the definition so history is preserved. */
data class TaskInstance(
    val taskId: String,
    val weekId: String,
    val planned: Boolean = false,
    val completed: Boolean = false,
    val completedOn: LocalDate? = null,
    /**
     * Every day of the week the task was completed on. A [Task.isPerDay] task is completed once
     * per day, so the single [completedOn] stamp can't say which days of the week were done — and
     * habits are charged exactly for the days that are missing here.
     */
    val completedDates: List<LocalDate> = emptyList(),
)

/**
 * The instance after (un)completing its task on [on], keeping the per-day [TaskInstance.completedDates]
 * history intact: un-completing drops only that day, never the rest of the week.
 */
fun TaskInstance.completing(completed: Boolean, on: LocalDate): TaskInstance = copy(
    completed = completed,
    completedOn = if (completed) on else null,
    completedDates = if (completed) (completedDates + on).distinct().sorted() else completedDates - on,
)

/** A reward that costs [pointCost] points to redeem. */
data class Reward(
    val id: String,
    val name: String,
    val pointCost: Int,
    val description: String = "",
)

/** Where a [PointsEvent] originated. [HABIT_MISS] is a day a [Recurrence.HABIT] task went undone. */
enum class PointsSource { TASK, OBJECTIVE, PENALTY, REWARD, ADJUSTMENT, HABIT_MISS }

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
    /** Everything the week deducted as a slip: applied penalties and [PointsSource.HABIT_MISS] days. */
    val penalties: List<PointsEvent>,
    val redemptions: List<PointsEvent>,
    val earned: Int,
    val spent: Int,
    val net: Int,
    val endingBalance: Int,
)
