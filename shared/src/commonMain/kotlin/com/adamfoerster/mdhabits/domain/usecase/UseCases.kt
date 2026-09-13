package com.adamfoerster.mdhabits.domain.usecase

import com.adamfoerster.mdhabits.core.datetime.WeekCalculator
import com.adamfoerster.mdhabits.core.datetime.WeekRange
import com.adamfoerster.mdhabits.core.newId
import com.adamfoerster.mdhabits.core.settings.AppSettings
import com.adamfoerster.mdhabits.domain.model.Objective
import com.adamfoerster.mdhabits.domain.model.Penalty
import com.adamfoerster.mdhabits.domain.model.PointsEvent
import com.adamfoerster.mdhabits.domain.model.PointsSource
import com.adamfoerster.mdhabits.domain.model.Recurrence
import com.adamfoerster.mdhabits.domain.model.Reward
import com.adamfoerster.mdhabits.domain.model.Task
import com.adamfoerster.mdhabits.domain.model.TaskInstance
import com.adamfoerster.mdhabits.domain.repository.MdPrayerRepository
import com.adamfoerster.mdhabits.domain.repository.PointsLedgerRepository
import com.adamfoerster.mdhabits.domain.repository.RewardRepository
import com.adamfoerster.mdhabits.domain.repository.TaskRepository
import com.adamfoerster.mdhabits.domain.repository.ThemeRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Use cases coordinate multi-repository writes so a state change and its ledger entry happen
 * together (e.g. completing a task both flips the instance and credits the ledger).
 */

/** Toggles a task's completion for a week and records the matching (positive/negative) ledger entry. */
class CompleteTaskUseCase(
    private val tasks: TaskRepository,
    private val ledger: PointsLedgerRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    suspend operator fun invoke(task: Task, weekId: String, nowCompleted: Boolean) {
        val now = clock.now()
        val today = now.toLocalDateTime(timeZone).date
        tasks.setCompleted(task.id, weekId, nowCompleted, today)
        val delta = if (nowCompleted) task.points else -task.points
        val label = (if (nowCompleted) "Concluída: " else "Desfeita: ") + task.title
        ledger.append(
            PointsEvent(newId("L"), now, weekId, PointsSource.TASK, task.id, label, delta),
        )
    }
}

/** Marks an objective achieved (or not) and records the points award/reversal. */
class AchieveObjectiveUseCase(
    private val theme: ThemeRepository,
    private val ledger: PointsLedgerRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    suspend operator fun invoke(weekId: String, objective: Objective, nowAchieved: Boolean) {
        val now = clock.now()
        val today = now.toLocalDateTime(timeZone).date
        theme.setObjectiveAchieved(objective.id, nowAchieved, today)
        val delta = if (nowAchieved) objective.points else -objective.points
        val label = (if (nowAchieved) "Objetivo alcançado: " else "Objetivo revertido: ") + objective.title
        ledger.append(
            PointsEvent(newId("L"), now, weekId, PointsSource.OBJECTIVE, objective.id, label, delta),
        )
    }
}

/** Applies a penalty, deducting its cost from the ledger. */
class ApplyPenaltyUseCase(
    private val ledger: PointsLedgerRepository,
    private val clock: Clock = Clock.System,
) {
    suspend operator fun invoke(weekId: String, penalty: Penalty) {
        ledger.append(
            PointsEvent(
                newId("L"), clock.now(), weekId, PointsSource.PENALTY, penalty.id,
                "Penalidade: ${penalty.name}", -penalty.pointCost,
            ),
        )
    }
}

/**
 * Applies the optional mdPrayer integration: marks the linked task done for the days mdPrayer
 * recorded as fully prayed within the current week. Only ever marks completion, never reverses it
 * (a manual undo in MdHabits, or mdPrayer no longer showing a day as prayed, isn't propagated) and
 * is idempotent — it never re-marks a day already reflected in [TaskRepository], so repeated calls
 * (every app open, or the manual "sync now") never double-credit the points ledger.
 *
 * Only the current week is checked, and for a per-day task only *today*: earlier days are already
 * closed (their weekly report is written, and a habit's missed days have been charged by
 * [PenalizeMissedHabitsUseCase]), so marking them now would credit points after the fact.
 */
class SyncMdPrayerUseCase(
    private val settings: AppSettings,
    private val mdPrayer: MdPrayerRepository,
    private val tasks: TaskRepository,
    private val completeTask: CompleteTaskUseCase,
    private val weekCalculator: WeekCalculator,
) {
    suspend operator fun invoke() {
        if (!settings.mdPrayerEnabled) return
        val ref = settings.mdPrayerFolderRef ?: return
        val taskId = settings.mdPrayerLinkedTaskId ?: return
        val task = tasks.getTask(taskId)?.takeIf { it.active } ?: return

        val weekId = weekCalculator.weekId()
        val range = weekCalculator.rangeOf()
        val today = weekCalculator.today()
        val completedDates = mdPrayer.completedDates(ref, range)
        val instance = tasks.observeInstances(weekId).first().find { it.taskId == taskId }

        val shouldComplete = if (task.isPerDay) {
            today in completedDates && today !in instance?.completedDates.orEmpty()
        } else {
            instance?.completed != true && daysOf(range).any { it in completedDates }
        }
        if (shouldComplete) completeTask(task, weekId, true)
    }

    private fun daysOf(range: WeekRange) =
        generateSequence(range.start) { it.plus(1, DateTimeUnit.DAY) }.takeWhile { it <= range.endInclusive }
}

/**
 * Charges every day a [Recurrence.HABIT] task went uncompleted, looking back [weeksBack] ISO weeks
 * (the current one included). A habit is the one task type whose points work backwards: the day
 * ending without it done *costs* [Task.points] instead of earning them.
 *
 * The app has no background scheduler, so the sweep runs on app open (and, like the balance itself,
 * it only ever derives from the ledger). It is idempotent — a (habit, day) miss is charged at most
 * once, recognized by the [PointsSource.HABIT_MISS] entry's [missRefId] — and it never charges:
 *
 * - today, which isn't over yet (the user still has until the end of the day);
 * - days before the habit's [Task.habitSince], so adding a habit can't bill the weeks before it;
 * - days the habit was completed on, read from [TaskInstance.completedDates].
 *
 * Inactive habits are skipped: pausing a habit stops the charges from the day it is switched off.
 */
class PenalizeMissedHabitsUseCase(
    private val tasks: TaskRepository,
    private val ledger: PointsLedgerRepository,
    private val weekCalculator: WeekCalculator,
    private val clock: Clock = Clock.System,
    /** How many ISO weeks back the sweep looks, counting the current one. */
    private val weeksBack: Int = 4,
) {
    suspend operator fun invoke() {
        val habits = tasks.observeTasks(activeOnly = true).first()
            .filter { it.recurrence == Recurrence.HABIT }
        if (habits.isEmpty()) return

        val today = weekCalculator.today()
        val thisMonday = weekCalculator.rangeOf(today).start
        // Oldest week first, so the ledger keeps its chronological order.
        for (weeksAgo in (weeksBack - 1) downTo 0) {
            val monday = thisMonday.minus(weeksAgo * 7, DateTimeUnit.DAY)
            val weekId = weekCalculator.weekId(monday)
            val instances = tasks.observeInstances(weekId).first().associateBy { it.taskId }
            val alreadyCharged = ledger.eventsForWeek(weekId)
                .filter { it.source == PointsSource.HABIT_MISS }
                .mapTo(mutableSetOf()) { it.refId }

            for (habit in habits) {
                val since = habit.habitSince
                val completed = instances[habit.id]?.completedDates.orEmpty().toSet()
                val missedDays = (0..6).map { monday.plus(it, DateTimeUnit.DAY) }
                    // Today isn't over yet, and a habit only counts from the day it became one.
                    .filter { it < today && (since == null || it >= since) && it !in completed }
                for (day in missedDays) {
                    val refId = missRefId(habit.id, day)
                    if (refId in alreadyCharged) continue
                    ledger.append(
                        PointsEvent(
                            id = newId("L"),
                            timestamp = clock.now(),
                            weekId = weekId,
                            source = PointsSource.HABIT_MISS,
                            refId = refId,
                            label = "Hábito não cumprido: ${habit.title} ($day)",
                            delta = -habit.points,
                        ),
                    )
                }
            }
        }
    }

    companion object {
        /** `<taskId>@<date>`: the miss is per day, so the day has to be part of what identifies it. */
        fun missRefId(taskId: String, date: LocalDate): String = "$taskId@$date"
    }
}

/** Result of attempting to redeem a reward. */
sealed interface RedeemResult {
    data object Success : RedeemResult
    data class InsufficientBalance(val balance: Int, val cost: Int) : RedeemResult
}

/** Redeems a reward if the balance covers its cost, deducting the cost from the ledger. */
class RedeemRewardUseCase(
    private val rewards: RewardRepository,
    private val ledger: PointsLedgerRepository,
    private val clock: Clock = Clock.System,
) {
    suspend operator fun invoke(weekId: String, reward: Reward): RedeemResult {
        val balance = ledger.currentBalance()
        if (balance < reward.pointCost) {
            return RedeemResult.InsufficientBalance(balance, reward.pointCost)
        }
        ledger.append(
            PointsEvent(
                newId("L"), clock.now(), weekId, PointsSource.REWARD, reward.id,
                "Resgate: ${reward.name}", -reward.pointCost,
            ),
        )
        // Touch the repository so a Markdown-backed impl can stamp redeemed_on later.
        rewards.getReward(reward.id)?.let { rewards.upsert(it) }
        return RedeemResult.Success
    }
}
