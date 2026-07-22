package com.adamfoerster.mdhabits.domain.usecase

import com.adamfoerster.mdhabits.core.newId
import com.adamfoerster.mdhabits.domain.model.Objective
import com.adamfoerster.mdhabits.domain.model.Penalty
import com.adamfoerster.mdhabits.domain.model.PointsEvent
import com.adamfoerster.mdhabits.domain.model.PointsSource
import com.adamfoerster.mdhabits.domain.model.Reward
import com.adamfoerster.mdhabits.domain.model.Task
import com.adamfoerster.mdhabits.domain.repository.PointsLedgerRepository
import com.adamfoerster.mdhabits.domain.repository.RewardRepository
import com.adamfoerster.mdhabits.domain.repository.TaskRepository
import com.adamfoerster.mdhabits.domain.repository.ThemeRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
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
